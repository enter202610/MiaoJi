package com.inkledger.app.data.ai

import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.CountDownEntity
import com.inkledger.app.data.db.GoalEntity
import com.inkledger.app.data.db.NoteEntity
import com.inkledger.app.util.formatMoney
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Insight(val emoji: String, val title: String, val body: String)

data class TxLite(val catId: Long, val minor: Long, val type: Int, val epochDay: Long)

/**
 * 本机智能洞察引擎（零联网）：分析应用内全部数据——
 * 支出/收入、预算、存钱目标、打卡习惯、倒数日、记事。
 * 每条洞察必须给出「具体数字 + 可执行建议」。
 */
object InsightEngine {

    fun generate(
        cur: List<TxLite>,
        prev: List<TxLite>,
        categoryMap: Map<Long, CategoryEntity>,
        budgetMinor: Long,
        currency: String,
        savings: List<Pair<GoalEntity, Long>>,
        habitStreaks: List<Pair<GoalEntity, Int>>,
        habitChecks: List<Pair<GoalEntity, Set<Long>>>,
        countdowns: List<CountDownEntity>,
        notes: List<NoteEntity>,
        today: LocalDate,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val curExp = cur.filter { it.type == 0 }
        val curInc = cur.filter { it.type == 1 }
        val curExpTotal = curExp.sumOf { it.minor }
        val curIncTotal = curInc.sumOf { it.minor }
        val daysTotal = ChronoUnit.DAYS.between(periodStart, periodEnd).toInt().coerceAtLeast(1)
        val daysElapsed = ChronoUnit.DAYS.between(periodStart, today).plus(1).coerceIn(1L, daysTotal.toLong()).toInt()
        val daysLeft = (daysTotal - daysElapsed).coerceAtLeast(0)
        fun name(id: Long) = categoryMap[id]?.name ?: "该分类"

        // ───── 记账洞察 ─────

        // 1. 环比异动 → 给出该分类本周期剩余额度
        val curByCat = curExp.groupBy { it.catId }.mapValues { e -> e.value.sumOf { it.minor } }
        val prevByCat = prev.filter { it.type == 0 }.groupBy { it.catId }.mapValues { e -> e.value.sumOf { it.minor } }
        val mom = curByCat.mapNotNull { (cat, amt) ->
            val before = prevByCat[cat] ?: 0L
            if (before >= 3000 && amt > before) {
                val pct = (amt - before) * 100 / before
                if (pct >= 20) Triple(cat, pct, amt) else null
            } else null
        }.maxByOrNull { it.third }
        mom?.let { (cat, pct, amt) ->
            val prevDaily = (prevByCat[cat] ?: 0L) / daysTotal
            val allowance = prevDaily * daysLeft
            out += Insight(
                "📈", "${name(cat)}环比 +$pct%",
                "本周期已花 ${formatMoney(amt, currency)}（上期同期约 ${formatMoney((prevByCat[cat] ?: 0L) * daysElapsed / daysTotal, currency)}）。行动：接下来 $daysLeft 天把该分类控制在 ${formatMoney(allowance, currency)} 内（日均 ${formatMoney(prevDaily, currency)}），就能拉回上期水平"
            )
        }

        // 2. 预算走势 → 精确到"每天最多花多少"
        if (budgetMinor > 0 && curExpTotal > 0) {
            val projected = curExpTotal / daysElapsed * daysTotal
            val over = projected > budgetMinor
            val dailyCap = if (daysLeft > 0) (budgetMinor - curExpTotal) / daysLeft else 0L
            out += Insight(
                if (over) "⚠️" else "🎯",
                if (over) "照此节奏将超预算 ${formatMoney(projected - budgetMinor, currency)}"
                else "预计本周期剩余 ${formatMoney(budgetMinor - projected, currency)} 预算",
                if (over && daysLeft > 0)
                    "已用 ${formatMoney(curExpTotal, currency)}/${formatMoney(budgetMinor, currency)}（${curExpTotal * 100 / budgetMinor}%）。行动：接下来 $daysLeft 天日均 ≤ ${formatMoney(dailyCap.coerceAtLeast(0), currency)} 就能回到预算内"
                else if (over)
                    "本周期只剩 $daysLeft 天，预算已用 ${curExpTotal * 100 / budgetMinor}%，下期建议预算设为 ${formatMoney(curExpTotal, currency)} 更现实"
                else
                    "节奏健康。行动：留 ${formatMoney((budgetMinor - projected) / 5, currency)} 作周末机动，其余可安心花"
            )
        }

        // 3. 高频小额：可选消费才建议削减；餐饮等必需类只给事实陈述
        val essentialCats = setOf("餐饮", "交通", "医疗", "居住", "日用", "通讯", "学习", "宠物", "运动", "买菜", "水电")
        curExp.groupBy { it.catId }.forEach { (cat, list) ->
            if (list.size >= 6) {
                val total = list.sumOf { it.minor }
                val avg = total / list.size
                if (avg in 1..5000 && total >= 10000) {
                    val n = name(cat)
                    if (essentialCats.any { n.contains(it) }) {
                        out += Insight(
                            "🍚", "$n：${list.size} 笔日常",
                            "合计 ${formatMoney(total, currency)} · 日均 ${formatMoney(total / daysElapsed, currency)}，属于日常必需开销，正常范围"
                        )
                    } else {
                        out += Insight(
                            "☕", "${n}小额 ${list.size} 笔",
                            "均价 ${formatMoney(avg, currency)}、合计 ${formatMoney(total, currency)}。行动：这类「无感小额」砍掉一半 ≈ 每期省 ${formatMoney(total / 2, currency)}、一年 ${formatMoney(total / 2 * 12, currency)}"
                        )
                    }
                }
            }
        }

        // 4. 订阅/固定支出检测（连续两个周期同金额同分类）
        curExp.groupBy { it.catId to it.minor }.keys.forEach { (cat, amt) ->
            if (amt >= 1000 && prev.any { it.type == 0 && it.catId == cat && it.minor == amt }) {
                out += Insight(
                    "🔁", "发现固定支出 ${formatMoney(amt, currency)}/期",
                    "${name(cat)}连续两期同金额，疑似订阅。行动：确认还在用吗？不用就退订——一年能省 ${formatMoney(amt * 12, currency)}"
                )
            }
        }

        // 5. 周末放大效应
        if (curExp.size >= 8) {
            val weekendDays = curExp.map { LocalDate.ofEpochDay(it.epochDay) }
                .filter { it.dayOfWeek.value >= 6 }.map { it.toEpochDay() }.toSet()
            if (weekendDays.isNotEmpty() && curExp.size > weekendDays.size) {
                val weekendTotal = curExp.filter { it.epochDay in weekendDays }.sumOf { it.minor }
                val weekdayTotal = curExpTotal - weekendTotal
                val wAvg = weekendTotal / weekendDays.size
                val dAvg = weekdayTotal / (curExp.size - weekendDays.size)
                if (dAvg > 0 && wAvg >= dAvg + dAvg / 2) {
                    val ratio = (wAvg * 10 / dAvg) / 10.0
                    val satSunLeft = ((daysLeft + 6) / 7).coerceAtLeast(1)
                    out += Insight(
                        "🏖", "周末日均是工作日的 $ratio 倍",
                        "周末 ${formatMoney(wAvg, currency)}/天 vs 工作日 ${formatMoney(dAvg, currency)}/天。行动：接下来约 $satSunLeft 个周末，出门前列张清单，每个周末控制在 ${formatMoney(dAvg, currency)} 能省 ${formatMoney((wAvg - dAvg) * satSunLeft, currency)}"
                    )
                }
            }
        }

        // 6. 预算用量紧急预警
        if (budgetMinor > 0 && curExpTotal * 100 >= budgetMinor * 85) {
            out += Insight(
                "🚨", "预算已用 ${curExpTotal * 100 / budgetMinor}%",
                "剩 ${formatMoney((budgetMinor - curExpTotal).coerceAtLeast(0), currency)} · $daysLeft 天。行动：大额消费推迟到下期，必要支出优先"
            )
        }

        // 7. 收入 vs 支出健康度（收不抵支 / 结余率）
        if (curIncTotal > 0 && curExpTotal > 0) {
            val balance = curIncTotal - curExpTotal
            val rate = (balance * 100 / curIncTotal).toInt()
            out += if (balance >= 0) Insight(
                "💰", "本期结余 ${formatMoney(balance, currency)}（结余率 $rate%）",
                "收入 ${formatMoney(curIncTotal, currency)} − 支出 ${formatMoney(curExpTotal, currency)}。行动：结余的 ${formatMoney(balance, currency)} 别躺在余额里——转进「${savings.firstOrNull()?.first?.name ?: "存钱目标"}」最实在"
            ) else Insight(
                "📉", "本期超支 ${formatMoney(-balance, currency)}",
                "收入 ${formatMoney(curIncTotal, currency)} < 支出 ${formatMoney(curExpTotal, currency)}，缺口 $rate%。行动：优先砍第 3 条里的无感小额，或把本月最大一笔支出 category（${name(curExp.maxByOrNull { it.minor }?.catId ?: 0L)}）下期设硬上限"
            )
        }

        // ───── 目标洞察 ─────

        // 8. 存钱目标：进度 + 每周存多少能达成
        savings.filter { (g, _) -> (g.targetAmountMinor ?: 0) > 0 }
            .maxByOrNull { (g, c) -> c * 1000 / (g.targetAmountMinor ?: 1) }?.let { (g, c) ->
                val target = g.targetAmountMinor ?: 0
                val pct = c * 100 / target
                val gap = (target - c).coerceAtLeast(0)
                val weeks = g.deadlineEpochDay?.let {
                    (ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(it)) / 7).toInt()
                }
                val advice = if (weeks != null && weeks > 0) "每周存 ${formatMoney(gap / weeks, currency)} 可如期达成" else "没有期限，建议每周存 ${formatMoney(gap / 10, currency)}（10 周计划）"
                out += Insight("🐷", "「${g.name}」$pct%（差 ${formatMoney(gap, currency)}）", "行动：$advice")
            }

        // 9. 打卡：连续 streak + 本期坚持率
        habitStreaks.filter { it.second >= 3 }.maxByOrNull { it.second }?.let { (g, streak) ->
            out += Insight("🔥", "「${g.name}」连续 $streak 天", "行动：坚持满 21 天（还差 ${21 - streak}）它就会变成本能")
        }
        habitChecks.forEach { (g, set) ->
            if (set.isEmpty()) return@forEach
            if (habitStreaks.any { it.first.id == g.id && it.second >= 3 }) return@forEach
            // 只统计目标创建之后的日子：今天建的目标不会把之前的日期算成"漏了"
            val createdDay = java.time.Instant.ofEpochMilli(g.createdAt)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val windowStart = maxOf(periodStart.toEpochDay(), createdDay)
            val windowDays = (today.toEpochDay() - windowStart + 1).coerceAtLeast(1)
            val checked = set.count { it >= windowStart }
            val missed = (windowDays - checked).coerceAtLeast(0)
            if (missed >= 3 && windowDays >= 4) out += Insight(
                "⏰", "「${g.name}」漏了 $missed 天",
                "建立以来 $windowDays 天里打卡 $checked 天。行动：今晚补一次，别让中断变成放弃——先连续 3 天找回节奏"
            )
        }

        // ───── 倒数日洞察 ─────

        // 10. 最近的倒数日提醒（含年度重复换算）
        countdowns.mapNotNull { c ->
            var t = LocalDate.ofEpochDay(c.targetEpochDay)
            if (c.repeatYearly) {
                var guard = 0
                while (t.isBefore(today) && guard < 200) { t = t.plusYears(1); guard++ }
            }
            val d = ChronoUnit.DAYS.between(today, t)
            if (d in 0..30) c to d.toInt() else null
        }.minByOrNull { it.second }?.let { (c, d) ->
            out += when {
                d == 0 -> Insight("🎉", "「${c.name}」就是今天！", "重要日子到了，拍张纪念卡留档吧")
                d <= 7 -> Insight("⏳", "「${c.name}」还有 $d 天", "行动：礼物/安排还没定的，本周内定下来，别拖到最后一两天临时抱佛脚")
                else -> Insight("📅", "「${c.name}」还有 $d 天", "行动：按 $d 天倒推，每周做一点点准备，到时候从容不迫")
            }
        }
        // 已过期未标记年度重复的倒数日
        countdowns.mapNotNull { c ->
            val d = ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(c.targetEpochDay))
            if (d < 0 && !c.repeatYearly) c to -d.toInt() else null
        }.maxByOrNull { it.second }?.let { (c, passed) ->
            if (passed <= 60) out += Insight(
                "🌙", "「${c.name}」已过 $passed 天",
                "这个日子已经过去了。行动：如果每年都想纪念它，编辑里打开「每年重复」，它会自动滚动到下一年"
            )
        }

        // ───── 记事洞察 ─────

        // 11. 记事本使用情况：最近记录时间 + 待整理提醒
        val latestNote = notes.maxByOrNull { it.updatedAt }
        if (notes.isNotEmpty() && latestNote != null) {
            val daysSince = ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(latestNote.updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                today
            )
            if (daysSince >= 14) out += Insight(
                "📝", "记事本 ${daysSince} 天没动了",
                "共 ${notes.size} 篇记事，最后更新于 ${daysSince} 天前。行动：翻翻看有没有过期的心愿单/清单，顺手清掉两篇，大脑清空才装得下新东西"
            )
        }
        // 清单式记事（含 - / 1. 前缀的）提醒
        val checklistNotes = notes.count { Regex("""(?m)^\s*(-|\d+\.)\s""").containsMatchIn(it.body) }
        if (checklistNotes >= 2) {
            out += Insight(
                "✅", "有 $checklistNotes 篇清单式记事",
                "清单里的承诺完成了吗？行动：挑一篇今天就能划掉一项的，成就感会滚雪球"
            )
        }

        // ───── 记账习惯本身 ─────

        // 12. 记账中断提醒（比漏记可怕的是停记）
        val recordDays = cur.map { it.epochDay }.toSet()
        if (recordDays.isNotEmpty()) {
            val lastDay = recordDays.max()
            val silent = today.toEpochDay() - lastDay
            if (silent >= 3 && daysElapsed >= 3) out += Insight(
                "🖊", "已经 $silent 天没记账了",
                "本周期只记了 ${recordDays.size} 天。行动：漏记不可怕，停记才可怕——现在补一句「昨天午饭三十」就行，AI 帮你归位"
            )
        }

        return out.take(6)
    }
}
