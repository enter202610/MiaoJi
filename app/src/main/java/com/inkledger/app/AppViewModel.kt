package com.inkledger.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkledger.app.data.ai.LlmClient
import com.inkledger.app.data.auto.AutoRecordService
import com.inkledger.app.data.backup.BackupManager
import com.inkledger.app.data.db.AppDatabase
import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.CountDownEntity
import com.inkledger.app.data.db.GoalDepositEntity
import com.inkledger.app.data.db.GoalEntity
import com.inkledger.app.data.db.HabitCheckEntity
import com.inkledger.app.data.db.NoteEntity
import com.inkledger.app.data.db.SeedData
import com.inkledger.app.data.db.TemplateEntity
import com.inkledger.app.data.db.TransactionEntity
import com.inkledger.app.data.prefs.AppSettings
import com.inkledger.app.data.prefs.SettingsRepository
import com.inkledger.app.data.prefs.ThemePreset
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.nowMillis
import com.inkledger.app.util.toLocalDate
import com.inkledger.app.util.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 全局状态容器：Room + DataStore 的唯一入口 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val prefs = SettingsRepository(app)

    val settings: StateFlow<AppSettings> =
        prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val categories: StateFlow<List<CategoryEntity>> =
        db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryMap: StateFlow<Map<Long, CategoryEntity>> =
        categories.map { list -> list.associateBy { it.id } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val txs: StateFlow<List<TransactionEntity>> =
        db.txDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val templates: StateFlow<List<TemplateEntity>> =
        db.templateDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<GoalEntity>> =
        db.goalDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val countdowns: StateFlow<List<CountDownEntity>> =
        db.countDownDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<List<NoteEntity>> =
        db.noteDao().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 启动即检查：过期回收站清理 + 周报生成
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { db.recycleBinDao().purgeOlderThan(System.currentTimeMillis() - 7L * 24 * 3600 * 1000) }
            runCatching { maybeGenerateWeeklyReport() }
        }
        // 小组件数据一致性：账单任何增删改（含导入/清空/回收站恢复）都会触发账单流
        // 发射 → 这里统一刷新全部小组件，保证桌面金额与 app 永远一致
        viewModelScope.launch(Dispatchers.IO) {
            txs.collect {
                com.inkledger.app.widget.WidgetSync.refreshAsync(getApplication())
            }
        }
    }

    fun habitChecks(goalId: Long): Flow<List<HabitCheckEntity>> = db.goalDao().observeChecks(goalId)

    fun deposits(goalId: Long): Flow<List<GoalDepositEntity>> = db.goalDao().observeDeposits(goalId)

    val allDeposits: StateFlow<List<GoalDepositEntity>> =
        db.goalDao().observeAllDeposits().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allHabitChecks: StateFlow<List<HabitCheckEntity>> =
        db.goalDao().observeAllChecks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---------- 记账 ----------

    fun addTx(
        amountMinor: Long,
        type: Int,
        categoryId: Long,
        note: String,
        timestamp: Long = nowMillis(),
        source: Int = 0
    ) {
        if (amountMinor <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            db.txDao().insert(
                TransactionEntity(
                    amountMinor = amountMinor,
                    type = type,
                    categoryId = categoryId,
                    note = note,
                    timestamp = timestamp,
                    createdAt = nowMillis(),
                    source = source
                )
            )
            refreshWidgets()
        }
    }

    fun updateTx(tx: TransactionEntity) = viewModelScope.launch(Dispatchers.IO) {
        db.txDao().update(tx)
        refreshWidgets()
    }

    /** 数据变化后刷新桌面小组件 */
    fun refreshWidgets() {
        com.inkledger.app.widget.WidgetSync.refreshAsync(getApplication())
    }

    // ---------- 回收站 ----------

    private fun binSnapshotTx(t: TransactionEntity) = org.json.JSONObject()
        .put("id", t.id).put("a", t.amountMinor).put("t", t.type).put("c", t.categoryId)
        .put("n", t.note).put("ts", t.timestamp).put("ca", t.createdAt).put("s", t.source)

    private fun binSnapshotGoal(g: GoalEntity) = org.json.JSONObject()
        .put("id", g.id).put("k", g.kind).put("n", g.name).put("e", g.emoji)
        .put("ta", g.targetAmountMinor ?: org.json.JSONObject.NULL)
        .put("dl", g.deadlineEpochDay ?: org.json.JSONObject.NULL)
        .put("ar", g.archived).put("ca", g.createdAt)

    private fun binSnapshotCountdown(c: CountDownEntity) = org.json.JSONObject()
        .put("id", c.id).put("n", c.name).put("e", c.emoji).put("d", c.targetEpochDay)
        .put("r", c.repeatYearly).put("p", c.pinned).put("bg", c.bgId)
        .put("bl", c.bgBlur).put("ca", c.createdAt)

    private fun binSnapshotNote(n: NoteEntity) = org.json.JSONObject()
        .put("id", n.id).put("ti", n.title).put("b", n.body).put("p", n.pinned)
        .put("u", n.updatedAt).put("m", n.media).put("ca", n.createdAt)

    private suspend fun toBin(type: String, payload: org.json.JSONObject, label: String, detail: String) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        db.recycleBinDao().purgeOlderThan(cutoff)
        db.recycleBinDao().insert(
            com.inkledger.app.data.db.RecycleBinEntity(
                type = type, payload = payload.toString(), label = label,
                detail = detail, deletedAt = System.currentTimeMillis()
            )
        )
    }

    val binItems = db.recycleBinDao().observeAll()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun restoreFromBin(e: com.inkledger.app.data.db.RecycleBinEntity) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val o = org.json.JSONObject(e.payload)
            when (e.type) {
                "tx" -> db.txDao().insert(
                    com.inkledger.app.data.db.TransactionEntity(
                        id = o.getLong("id"), amountMinor = o.getLong("a"), type = o.getInt("t"),
                        categoryId = o.getLong("c"), note = o.optString("n"),
                        timestamp = o.getLong("ts"), createdAt = o.getLong("ca"), source = o.optInt("s", 0)
                    )
                )
                "goal" -> {
                    db.goalDao().insert(com.inkledger.app.data.db.GoalEntity(
                        id = o.getLong("id"), kind = o.getInt("k"), name = o.getString("n"),
                        emoji = o.optString("e", "🎯"),
                        targetAmountMinor = if (o.isNull("ta")) null else o.getLong("ta"),
                        deadlineEpochDay = if (o.isNull("dl")) null else o.getLong("dl"),
                        archived = o.optBoolean("ar", false), createdAt = o.optLong("ca", 0)
                    ))
                    // 连存入记录一起还原
                    o.optJSONArray("deps")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val d = arr.optJSONObject(i) ?: continue
                            db.goalDao().insertDeposit(
                                com.inkledger.app.data.db.GoalDepositEntity(
                                    goalId = o.getLong("id"), amountMinor = d.getLong("a"),
                                    timestamp = d.getLong("ts")
                                )
                            )
                        }
                    }
                    // 打卡历史一并还原
                    o.optJSONArray("chks")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val c = arr.optJSONObject(i) ?: continue
                            db.goalDao().insertCheck(
                                com.inkledger.app.data.db.HabitCheckEntity(
                                    goalId = o.getLong("id"), epochDay = c.getLong("d")
                                )
                            )
                        }
                    }
                }
                "countdown" -> db.countDownDao().insert(com.inkledger.app.data.db.CountDownEntity(
                    id = o.getLong("id"), name = o.getString("n"), emoji = o.optString("e", "⏳"),
                    targetEpochDay = o.getLong("d"), repeatYearly = o.optBoolean("r", false),
                    pinned = o.optBoolean("p", false), bgId = o.getInt("bg"),
                    bgBlur = o.getInt("bl"), createdAt = o.optLong("ca", 0)
                ))
                "note" -> db.noteDao().insert(com.inkledger.app.data.db.NoteEntity(
                    id = o.getLong("id"), title = o.optString("ti"), body = o.optString("b"),
                    pinned = o.optBoolean("p", false), updatedAt = o.getLong("u"),
                    media = o.optString("m"), createdAt = o.optLong("ca", 0)
                ))
            }
            db.recycleBinDao().delete(e)
        } catch (ignore: Exception) {
        }
    }

    fun deleteFromBinForever(e: com.inkledger.app.data.db.RecycleBinEntity) =
        viewModelScope.launch(Dispatchers.IO) { db.recycleBinDao().delete(e) }

    fun purgeExpiredBinItems() = viewModelScope.launch(Dispatchers.IO) {
        db.recycleBinDao().purgeOlderThan(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
    }

    fun clearBin() = viewModelScope.launch(Dispatchers.IO) { db.recycleBinDao().clearAll() }

    fun deleteTx(tx: TransactionEntity) = viewModelScope.launch(Dispatchers.IO) {
        toBin(
            "tx", binSnapshotTx(tx),
            categoryMap.value[tx.categoryId]?.name ?: "记一笔",
            (if (tx.type == 0) "-" else "+") + com.inkledger.app.util.formatMoney(tx.amountMinor)
        )
        db.txDao().delete(tx)
    }

    fun deleteGoal(g: GoalEntity) = viewModelScope.launch(Dispatchers.IO) {
        val deposits = db.goalDao().listDeposits(g.id)
        val checks = db.goalDao().listAllChecks().filter { it.goalId == g.id }
        val payload = binSnapshotGoal(g).put(
            "deps", org.json.JSONArray(deposits.map {
                org.json.JSONObject().put("g", it.goalId).put("a", it.amountMinor).put("ts", it.timestamp)
            })
        ).put(
            // 打卡历史一起入快照：没有它，删除→恢复后打卡记录就永久丢了
            "chks", org.json.JSONArray(checks.map {
                org.json.JSONObject().put("d", it.epochDay)
            })
        )
        toBin("goal", payload, g.name, com.inkledger.app.util.formatMoney(g.targetAmountMinor ?: 0))
        db.goalDao().delete(g)
        db.goalDao().deleteDeposits(g.id)
        db.goalDao().deleteChecks(g.id)
        refreshWidgets()   // 被选中的打卡任务删掉时，组件自动回退
    }

    fun deleteCountdown(c: CountDownEntity) = viewModelScope.launch(Dispatchers.IO) {
        toBin(
            "countdown", binSnapshotCountdown(c), c.name,
            java.time.LocalDate.ofEpochDay(c.targetEpochDay).format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"))
        )
        db.countDownDao().delete(c)
        refreshWidgets()   // 被选中的倒数日删掉时，组件自动回退到置顶/最近
    }

    fun deleteNote(n: NoteEntity) = viewModelScope.launch(Dispatchers.IO) {
        toBin(
            "note", binSnapshotNote(n),
            n.title.ifBlank { n.body.take(12).ifBlank { "记事" } },
            java.time.Instant.ofEpochMilli(n.updatedAt).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("M月d日"))
        )
        db.noteDao().delete(n)
    }

    /** 撤销删除：重新插入（放弃原 id） */
    fun restoreTx(tx: TransactionEntity) =
        viewModelScope.launch(Dispatchers.IO) { db.txDao().insert(tx.copy(id = 0)) }

    /** 再来一笔：复制最近一笔 */
    fun repeatLast(): Boolean {
        val last = txs.value.firstOrNull() ?: return false
        addTx(last.amountMinor, last.type, last.categoryId, last.note, nowMillis(), source = 3)
        return true
    }

    /** 模板一键记账（MoneyThings 式 One Touch） */
    fun applyTemplate(t: TemplateEntity) {
        addTx(t.amountMinor, t.type, t.categoryId, t.note, nowMillis(), source = 2)
        viewModelScope.launch(Dispatchers.IO) {
            db.templateDao().update(t.copy(useCount = t.useCount + 1))
        }
    }

    fun saveTemplate(t: TemplateEntity) = viewModelScope.launch(Dispatchers.IO) {
        if (t.id == 0L) db.templateDao().insert(t.copy(createdAt = nowMillis()))
        else db.templateDao().update(t)
    }

    fun deleteTemplate(t: TemplateEntity) =
        viewModelScope.launch(Dispatchers.IO) { db.templateDao().delete(t) }

    // ---------- 目标 ----------

    fun saveGoal(g: GoalEntity) = viewModelScope.launch(Dispatchers.IO) {
        if (g.id == 0L) db.goalDao().insert(g.copy(createdAt = nowMillis()))
        else db.goalDao().update(g)
        refreshWidgets()   // 打卡小组件同步（新建/改名）
    }

fun addDeposit(goalId: Long, amountMinor: Long) = viewModelScope.launch(Dispatchers.IO) {
        if (amountMinor == 0L) return@launch
        db.goalDao().insertDeposit(
            GoalDepositEntity(goalId = goalId, amountMinor = amountMinor, timestamp = nowMillis())
        )
    }

    fun toggleHabit(goalId: Long, epochDay: Long = LocalDate.now().toEpochDay()) = viewModelScope.launch(Dispatchers.IO) {
        if (db.goalDao().countCheck(goalId, epochDay) > 0) db.goalDao().deleteCheck(goalId, epochDay)
        else db.goalDao().insertCheck(HabitCheckEntity(goalId = goalId, epochDay = epochDay))
        refreshWidgets()   // 打卡小组件等同步更新
    }

    // ---------- 倒数日 ----------

    fun saveCountdown(c: CountDownEntity) = viewModelScope.launch(Dispatchers.IO) {
        if (c.id == 0L) db.countDownDao().insert(c.copy(createdAt = nowMillis()))
        else db.countDownDao().update(c)
        refreshWidgets()   // 倒数日小组件同步
    }

fun toggleCountdownPin(c: CountDownEntity) = viewModelScope.launch(Dispatchers.IO) {
        if (c.pinned) {
            db.countDownDao().update(c.copy(pinned = false))
        } else {
            // 唯一置顶：置顶它之前先取消其它，保证首页展示目标明确
            db.countDownDao().listAll().filter { it.pinned && it.id != c.id }
                .forEach { db.countDownDao().update(it.copy(pinned = false)) }
            db.countDownDao().update(c.copy(pinned = true))
        }
        refreshWidgets()   // 倒数日小组件跟随置顶变化
    }

    // ---------- 记事 ----------

    fun saveNote(n: NoteEntity) = viewModelScope.launch(Dispatchers.IO) {
        if (n.id == 0L) db.noteDao().insert(n) else db.noteDao().update(n)
    }

    /** 编辑页自动保存用：返回笔记 id（新建时生成） */
    suspend fun saveNoteSuspend(n: NoteEntity): Long = withContext(Dispatchers.IO) {
        if (n.id == 0L) db.noteDao().insert(n) else {
            db.noteDao().update(n); n.id
        }
    }

suspend fun getNote(id: Long): NoteEntity? = db.noteDao().getById(id)

    // ---------- 设置 ----------

    fun setTheme(v: ThemePreset) = viewModelScope.launch {
        prefs.setTheme(v)
        // 主题换了，小组件背景跟着换
        refreshWidgets()
    }
    fun setCurrency(v: String) = viewModelScope.launch { prefs.setCurrency(v) }
    fun setMonthStartDay(v: Int) = viewModelScope.launch { prefs.setMonthStartDay(v) }
    fun setBudget(minor: Long) = viewModelScope.launch { prefs.setBudgetMinor(minor) }
    fun setFlexItems(items: List<com.inkledger.app.data.prefs.FlexItem>) =
        viewModelScope.launch { prefs.setFlexItems(items) }
    fun addFlexSpend(itemId: Long, minor: Long) =
        viewModelScope.launch { prefs.addFlexSpend(itemId, minor) }
    fun removeFlexSpend(itemId: Long, at: Long) =
        viewModelScope.launch { prefs.removeFlexSpend(itemId, at) }
    fun setPureMode(v: Boolean) = viewModelScope.launch { prefs.setPureMode(v) }
    fun setChineseSeed(v: Int) = viewModelScope.launch {
        prefs.setChineseSeed(v)
        refreshWidgets()
    }
    fun setWidgetBgMode(v: Int) = viewModelScope.launch {
        prefs.setWidgetBgMode(v)
        refreshWidgets()
    }
    fun setWidgetBgColor(v: Long) = viewModelScope.launch {
        prefs.setWidgetBgColor(v)
        refreshWidgets()
    }
    fun setWidgetBgBlur(v: Int) = viewModelScope.launch {
        prefs.setWidgetBgBlur(v)
        refreshWidgets()   // 模糊度变化 → 组件图片背景实时重渲染
    }
    fun refreshWidgetBgNow() = refreshWidgets()   // 换图保存后手动触发
    fun setWidgetCountdownPick(appWidgetId: Int, cdId: Long?) =
        viewModelScope.launch(Dispatchers.IO) { prefs.setWidgetCountdownPick(appWidgetId, cdId) }
    fun setWidgetHabitPick(appWidgetId: Int, hbId: Long?) =
        viewModelScope.launch(Dispatchers.IO) { prefs.setWidgetHabitPick(appWidgetId, hbId) }
    fun markTipShown() = viewModelScope.launch { prefs.markTipShown() }

    // ---------- 周报 ----------

    /**
     * 周报生成时机（惰性，打开应用/AI 面板时检查）：
     * - 周日 18 点后 → 生成本周（周一至周日）小结
     * - 上周小结未覆盖到最后 → 生成/刷新上周小结
     *   （每次打开都会保持上周小结与最新数据一致：期间改账/删账自动反映）
     */
    fun maybeGenerateWeeklyReport() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val s = prefs.flow.first()
            val today = java.time.LocalDate.now()
            val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val lastWeekStart = thisMonday.minusDays(7)
            val lastWeekEnd = lastWeekStart.plusDays(6)
            val coveredEnd = java.time.LocalDate.ofEpochDay(s.weeklyReportEnd)
            val isSundayEvening = today.dayOfWeek.value == 7 && java.time.LocalTime.now().hour >= 18

            val range = when {
                isSundayEvening && coveredEnd < thisMonday.plusDays(6) ->
                    thisMonday to thisMonday.plusDays(6)
                coveredEnd <= lastWeekEnd -> lastWeekStart to lastWeekEnd
                else -> return@launch
            }
            val (start, end) = range
            // 直接查库：txs StateFlow 在刚启动时可能还没加载完（曾因此存过全 0 的报告）
            val all = db.txDao().getAll()
            val list = all.filter {
                val d = it.timestamp.toLocalDate()
                d >= start && d <= end
            }
            val exp = list.filter { it.type == 0 }.sumOf { it.amountMinor }
            val inc = list.filter { it.type == 1 }.sumOf { it.amountMinor }
            val dayCount = list.map { it.timestamp.toLocalDate() }.distinct().size
            val catNames = db.categoryDao().listAll().associateBy { it.id }
            val topCat = list.filter { it.type == 0 }.groupBy { it.categoryId }
                .maxByOrNull { e -> e.value.sumOf { it.amountMinor } }
                ?.let { e -> "${catNames[e.key]?.name ?: ""} ${com.inkledger.app.util.formatCompact(e.value.sumOf { it.amountMinor })}" }
            val biggest = list.filter { it.type == 0 }.maxByOrNull { it.amountMinor }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA)
            val text = buildString {
                append("${start.format(fmt)}–${end.format(fmt)}：")
                append("支出 ${com.inkledger.app.util.formatCompact(exp)}")
                if (inc > 0) append("，收入 ${com.inkledger.app.util.formatCompact(inc)}")
                append("。记了 ${list.size} 笔、覆盖 $dayCount 天。")
                topCat?.let { append("花得最多的是$it。") }
                biggest?.let {
                    append("最大一笔 ${com.inkledger.app.util.formatCompact(it.amountMinor)}（${it.note.ifBlank { "无备注" }}）。")
                }
                if (list.size < 10) append("记得勤一点，账目才更准。")
            }
            prefs.setWeeklyReport(text, end.toEpochDay())
        } catch (ignore: Exception) {
        }
    }
    fun setAutoRecord(v: Boolean) = viewModelScope.launch { prefs.setAutoRecordEnabled(v) }
    fun setAutoConfirm(v: Boolean) = viewModelScope.launch { prefs.setAutoConfirmMode(v) }
    fun setOnboarded() = viewModelScope.launch { prefs.setOnboarded(true) }
    fun setAiEnabled(v: Boolean) = viewModelScope.launch { prefs.setAiEnabled(v) }
    fun setAiProvider(v: String) = viewModelScope.launch { prefs.setAiProvider(v) }
    fun setAiKey(v: String) = viewModelScope.launch { prefs.setAiKey(v) }
    fun setAiModel(v: String) = viewModelScope.launch { prefs.setAiModel(v) }
    fun setAiBase(v: String) = viewModelScope.launch { prefs.setAiBase(v) }
    fun setBgEnabled(v: Boolean) = viewModelScope.launch {
        prefs.setBgEnabled(v)
        // 背景变了 → 小组件背景同步（自定义图/恢复主题渐变）
        refreshWidgets()
    }
    fun setBgVersion(v: Int) = viewModelScope.launch { prefs.setBgVersion(v) }
    fun setBgBlur(v: Int) = viewModelScope.launch {
        prefs.setBgBlur(v)
        refreshWidgets()   // 组件的自定义图背景带模糊
    }
    fun setBgGradient(v: Int) = viewModelScope.launch {
        prefs.setBgGradient(v)
        // 渐变与自定义图互斥：后选的生效
        if (v >= 0) prefs.setBgEnabled(false)
        // 全屏渐变换色 → 小组件背景立即同步
        refreshWidgets()
    }
    fun markCelebratedGoal(id: Long) = viewModelScope.launch { prefs.addCelebratedGoal(id) }

    // ---------- AI ----------

    /** 全量上下文：账目 + 目标 + 习惯 + 倒数日 + 记事 + 模板 + 记账行为分析 */
    fun buildAiContext(): String {
        val s = settings.value
        val startDay = s.monthStartDay
        val now = LocalDate.now()
        val cur = now.monthPeriodStart(startDay)
        val prev = cur.minusMonths(1)
        val curT = txs.value.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == cur }
        val prevT = txs.value.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == prev }
        val cm = categoryMap.value
        val sb = StringBuilder()

        // ── 账目 ──
        sb.append("【账目】\n本月支出分类：")
        curT.filter { it.type == 0 }.groupBy { it.categoryId }
            .toList().sortedByDescending { e -> e.second.sumOf { it.amountMinor } }
            .forEach { (cid, list) ->
                sb.append(cm[cid]?.name ?: "其他").append("¥")
                    .append(list.sumOf { it.amountMinor } / 100.0).append("（").append(list.size).append("笔）；")
            }
        sb.append("\n上月支出合计¥").append(prevT.filter { it.type == 0 }.sumOf { it.amountMinor } / 100.0)
        sb.append("；本月收入¥").append(curT.filter { it.type == 1 }.sumOf { it.amountMinor } / 100.0)
        if (s.budgetMinor > 0) sb.append("；月预算¥").append(s.budgetMinor / 100.0)

        // 近3月走势
        sb.append("\n近3月支出：")
        (2L downTo 0L).forEach { back ->
            val p = cur.minusMonths(back)
            val sum = txs.value.filter {
                it.type == 0 && it.timestamp.toLocalDate().monthPeriodStart(startDay) == p
            }.sumOf { it.amountMinor }
            sb.append("${p.monthValue}月¥${sum / 100.0}；")
        }

        // 本月最大3笔
        curT.filter { it.type == 0 }.sortedByDescending { it.amountMinor }.take(3).forEach {
            sb.append("\n大额：").append(cm[it.categoryId]?.name ?: "其他").append("¥").append(it.amountMinor / 100.0)
                .append("「").append(it.note.ifBlank { "无备注" }).append("」")
        }

        // 记账行为分析
        val autoCount = curT.count { it.source == 4 }
        val manualCount = curT.size - autoCount
        sb.append("\n记账方式：自动").append(autoCount).append("笔、手动").append(manualCount).append("笔")

        // 时段分析
        val morning = curT.count { it.timestamp.toLocalDateTime().hour < 11 }
        val noon = curT.count { it.timestamp.toLocalDateTime().hour in 11..17 }
        val night = curT.count { it.timestamp.toLocalDateTime().hour > 17 }
        sb.append("；早").append(morning).append("午").append(noon).append("晚").append(night)

        // ── 存钱目标 ──
        val savingsGoals = goals.value.filter { it.kind == 0 && !it.archived }
        if (savingsGoals.isNotEmpty()) sb.append("\n【存钱目标】")
        savingsGoals.forEach { g ->
            val cur2 = allDeposits.value.filter { it.goalId == g.id }.sumOf { it.amountMinor }
            val pct = if ((g.targetAmountMinor ?: 0) > 0) cur2 * 100 / g.targetAmountMinor!! else 0
            sb.append("\n「").append(g.name).append("」").append(g.emoji)
                .append(" 已存¥").append(cur2 / 100.0)
            g.targetAmountMinor?.let { sb.append("/¥").append(it / 100.0).append("（").append(pct).append("%）") }
            g.deadlineEpochDay?.let { dl ->
                val left = java.time.temporal.ChronoUnit.DAYS.between(now, java.time.LocalDate.ofEpochDay(dl))
                sb.append(" 剩").append(left).append("天")
            }
        }

        // ── 习惯打卡 ──
        val habitGoals = goals.value.filter { it.kind == 1 }
        if (habitGoals.isNotEmpty()) sb.append("\n【习惯打卡】")
        habitGoals.forEach { g ->
            val checks = allHabitChecks.value.filter { it.goalId == g.id }.map { it.epochDay }.toSet()
            var streak = 0
            var d = now
            if (now.toEpochDay() !in checks) d = d.minusDays(1)
            while (d.toEpochDay() in checks) { streak++; d = d.minusDays(1) }
            val monthCount = checks.count { it >= now.withDayOfMonth(1).toEpochDay() }
            sb.append("\n「").append(g.name).append("」连续").append(streak).append("天·本月").append(monthCount).append("天")
        }

        // ── 倒数日 ──
        val cds = countdowns.value.sortedBy { it.targetEpochDay }.take(3)
        if (cds.isNotEmpty()) sb.append("\n【倒数日】")
        cds.forEach { c ->
            var target = java.time.LocalDate.ofEpochDay(c.targetEpochDay)
            if (c.repeatYearly) {
                var guard = 0
                while (target.isBefore(now) && guard < 200) { target = target.plusYears(1); guard++ }
            }
            val dd = java.time.temporal.ChronoUnit.DAYS.between(now, target)
            sb.append("\n").append(c.emoji).append("「").append(c.name).append("」还有").append(dd).append("天")
        }

        // ── 记事 ──
        val ns = notes.value
        if (ns.isNotEmpty()) {
            sb.append("\n【记事】共").append(ns.size).append("条")
            ns.take(3).forEach { n ->
                sb.append("；「").append(n.title.ifBlank { n.body.take(10) }).append("」")
            }
        }

        // ── 模板使用 ──
        val tps = templates.value
        if (tps.isNotEmpty()) {
            sb.append("\n【模板】")
            tps.take(3).forEach { t ->
                sb.append(t.name).append("¥").append(t.amountMinor / 100.0).append("×").append(t.useCount).append("次；")
            }
        }

        return sb.toString()
    }

    fun askAi(
        question: String,
        history: List<LlmClient.Msg>,
        onResult: (ok: Boolean, reply: String) -> Unit
    ) {
        val s = settings.value
        if (!s.aiEnabled || s.aiKey.isBlank()) {
            onResult(false, "请先到 设置 → AI 助手 填入 API Key")
            return
        }
        viewModelScope.launch {
            val base = LlmClient.endpointFor(s.aiProvider, s.aiBase)
            val model = s.aiModel.ifBlank { LlmClient.defaultModelFor(s.aiProvider) }
            val sys = LlmClient.Msg(
                "system",
                        "你是记账App「妙计」里的AI助手。只依据提供的用户账单统计回答；货币为人民币；简洁中文，120字以内；不给投资建议；没有的数据就说还没有记录。\n用户账单统计：${buildAiContext()}"
            )
            val (reply, err) = LlmClient.chat(
                base, s.aiKey, model,
                listOf(sys) + history + listOf(LlmClient.Msg("user", question))
            )
            onResult(reply != null, reply ?: err ?: "未知错误")
        }
    }

    fun isListenerEnabled(): Boolean = AutoRecordService.isListenerEnabled(getApplication())

    // ---------- 数据 ----------

    fun exportCsv(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val data = buildCsvBytes()
            getApplication<Application>().contentResolver.openOutputStream(uri)
                ?.use { it.write(data) }
            withContext(Dispatchers.Main) { onDone(true) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onDone(false) }
        }
    }

    /** 系统文件选择器不可用时的兜底：直接写入公共下载目录 */
    fun exportCsvToDownloads(fileName: String, onDone: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                writePublicFile(fileName, "text/csv", buildCsvBytes())
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }

    private suspend fun buildCsvBytes(): ByteArray {
        val list = db.txDao().getAll()
        val catMap = categories.value.associateBy { it.id }
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder("\uFEFF日期,类型,分类,金额,备注,来源\n")
        list.forEach { t ->
            val c = catMap[t.categoryId]
            sb.append(t.timestamp.toLocalDateTime().format(fmt)).append(',')
                .append(if (t.type == 0) "支出" else "收入").append(',')
                .append("${c?.emoji ?: ""}${c?.name ?: "未分类"}").append(',')
                .append(String.format(Locale.US, "%.2f", t.amountMinor / 100.0)).append(',')
                .append(t.note.replace(",", "，").replace("\n", " ")).append(',')
                .append(
                    when (t.source) {
                        1 -> "一句话"; 2 -> "模板"; 3 -> "再来一笔"; 4 -> "自动"; else -> "手动"
                    }
                )
                .append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** 查询下载目录里最新的墨账备份（离线导入，无需系统文件选择器） */
    fun findLatestBackupInDownloads(onFound: (Uri?) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            var found: Uri? = null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.query(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(android.provider.MediaStore.Downloads._ID),
                        "${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                        arrayOf("inkledger_%.inkbak"),
                        "${android.provider.MediaStore.Downloads.DATE_ADDED} DESC"
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            found = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("InkLedger", "findLatestBackup failed", e)
                }
            }
            withContext(Dispatchers.Main) { onFound(found) }
        }

    /** 写入公共下载目录（API 29+ 走 MediaStore；老系统写应用外部目录） */
    private fun writePublicFile(name: String, mime: String, data: ByteArray): Boolean {
        val ctx = getApplication<Application>()
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                resolver.openOutputStream(uri)?.use { it.write(data) } ?: return false
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: return false
                java.io.File(dir, name).writeBytes(data)
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("InkLedger", "writePublicFile failed", e)
            false
        }
    }

    fun clearAllData() = viewModelScope.launch(Dispatchers.IO) {
        db.clearAllTables()
        db.categoryDao().insertAll(SeedData.categories)
    }

    // ---------- 加密备份与恢复 ----------

    fun exportBackup(uri: Uri, password: String, includeMedia: Boolean, onDone: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = buildBackupBytes(password, includeMedia)
                getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?.use { it.write(data) }
                    ?: throw IllegalStateException("无法写入文件")
                withContext(Dispatchers.Main) { onDone(true) }
            } catch (e: Exception) {
                android.util.Log.e("InkLedger", "exportBackup failed", e)
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }

    /** 系统文件选择器不可用时的兜底：加密备份直接写入公共下载目录 */
    fun exportBackupToDownloads(fileName: String, password: String, includeMedia: Boolean, onDone: (Boolean) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                writePublicFile(fileName, "application/octet-stream", buildBackupBytes(password, includeMedia))
            } catch (e: Exception) {
                android.util.Log.e("InkLedger", "exportBackup fallback failed", e)
                false
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }

    private suspend fun buildBackupBytes(password: String, includeMedia: Boolean): ByteArray {
        val bundle = BackupManager.Bundle(
            txs = db.txDao().getAll(),
            categories = db.categoryDao().listAll(),
            templates = db.templateDao().listAll(),
            goals = db.goalDao().listAll(),
            deposits = db.goalDao().listAllDeposits(),
            checks = db.goalDao().listAllChecks(),
            countdowns = db.countDownDao().listAll(),
            notes = db.noteDao().listAll(),
            media = if (includeMedia) collectMediaFiles() else emptyList()
        )
        val json = BackupManager.toJson(bundle)
        return BackupManager.encrypt(json.toByteArray(Charsets.UTF_8), password)
    }

    /**
     * 收集全部本地媒体：记事图片（notes_media/）+ 倒数日背景（cd_*.jpg）+ 应用背景（app_bg.jpg）
     * 全部转 Base64 进备份，恢复时按文件名归位。
     */
    private fun collectMediaFiles(): List<BackupManager.MediaFile> {
        val ctx = getApplication<Application>()
        val out = mutableListOf<BackupManager.MediaFile>()
        val notesMediaDir = java.io.File(ctx.filesDir, "notes_media")
        notesMediaDir.listFiles()?.filter { it.isFile }?.forEach { f ->
            runCatching { out += BackupManager.MediaFile(f.name, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)) }
        }
        ctx.filesDir.listFiles()?.filter { it.isFile &&
            (it.name == "app_bg.jpg" || it.name.startsWith("cd_")) }?.forEach { f ->
            runCatching { out += BackupManager.MediaFile(f.name, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP)) }
        }
        return out
    }

    /** 恢复媒体文件：按文件名归位（cd_/app_bg → filesDir 根目录，其余 → notes_media/） */
    private fun restoreMediaFiles(media: List<BackupManager.MediaFile>) {
        val ctx = getApplication<Application>()
        val notesMediaDir = java.io.File(ctx.filesDir, "notes_media")
        if (!notesMediaDir.exists()) notesMediaDir.mkdirs()
        media.forEach { m ->
            runCatching {
                val bytes = android.util.Base64.decode(m.data, android.util.Base64.NO_WRAP)
                val f = if (m.name == "app_bg.jpg" || m.name.startsWith("cd_")) {
                    java.io.File(ctx.filesDir, m.name)
                } else {
                    java.io.File(notesMediaDir, m.name)
                }
                f.writeBytes(bytes)
            }
        }
    }

    /**
     * 导入备份。overwrite=true 清空后导入（保留原 id，目标/打卡记录引用不断）；
     * false 合并导入（账单按 金额+类型+时间+备注 去重，目标按 id 忽略冲突）。
     */
    fun importBackup(
        uri: Uri,
        password: String,
        overwrite: Boolean,
        onDone: (ok: Boolean, txCount: Int) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }
            if (bytes == null) {
                withContext(Dispatchers.Main) { onDone(false, 0) }
                return@launch
            }
            val plain = BackupManager.decrypt(bytes, password)
            if (plain == null) {
                withContext(Dispatchers.Main) { onDone(false, 0) }
                return@launch
            }
            val b = BackupManager.fromJson(String(plain, Charsets.UTF_8))
            restoreMediaFiles(b.media)
            if (overwrite) {
                db.clearAllTables()
                db.categoryDao().insertAll(b.categories.ifEmpty { SeedData.categories })
                b.txs.forEach { db.txDao().insert(it) }
                b.goals.forEach { db.goalDao().insert(it) }
                b.deposits.forEach { db.goalDao().insertDeposit(it) }
                b.checks.forEach { db.goalDao().insertCheck(it) }
                b.templates.forEach { db.templateDao().insert(it) }
                b.countdowns.forEach { db.countDownDao().insert(it) }
                b.notes.forEach { db.noteDao().insert(it) }
                withContext(Dispatchers.Main) { onDone(true, b.txs.size) }
            } else {
                db.categoryDao().insertAll(b.categories.ifEmpty { SeedData.categories })
                val existing = db.txDao().getAll()
                    .map { Triple(it.amountMinor, it.type, Pair(it.timestamp, it.note)) }
                    .toHashSet()
                var imported = 0
                b.txs.forEach { t ->
                    val key = Triple(t.amountMinor, t.type, Pair(t.timestamp, t.note))
                    if (key !in existing) {
                        db.txDao().insert(t.copy(id = 0))
                        imported++
                    }
                }
                b.goals.forEach { db.goalDao().insertGoalKeepingId(it) }
                b.deposits.forEach { db.goalDao().insertDeposit(it) }
                b.checks.forEach { db.goalDao().insertCheck(it) }
                val tplKeys = db.templateDao().listAll()
                    .map { Triple(it.name, it.amountMinor, it.categoryId) }.toHashSet()
                b.templates.forEach { t ->
                    if (Triple(t.name, t.amountMinor, t.categoryId) !in tplKeys) {
                        db.templateDao().insert(t)
                    }
                }
                val cdKeys = db.countDownDao().listAll()
                    .map { Triple(it.name, it.targetEpochDay, it.repeatYearly) }.toHashSet()
                val cdRemap = mutableListOf<Pair<Long, Long>>()   // 旧id → 新id（背景图文件名跟随 id）
                b.countdowns.forEach { c ->
                    if (Triple(c.name, c.targetEpochDay, c.repeatYearly) !in cdKeys) {
                        val newId = db.countDownDao().insert(c.copy(id = 0))
                        if (c.id != 0L && c.id != newId) cdRemap += c.id to newId
                    }
                }
                // 背景图文件按旧 id 命名：合并后复制到新 id 名下，自定义背景不丢
                cdRemap.forEach { (oldId, newId) ->
                    runCatching {
                        java.io.File(getApplication<Application>().filesDir, "cd_$oldId.jpg")
                            .copyTo(java.io.File(getApplication<Application>().filesDir, "cd_$newId.jpg"), overwrite = true)
                    }
                }
                val noteKeys = db.noteDao().listAll()
                    .map { Pair(it.title, it.updatedAt) }.toHashSet()
                b.notes.forEach { n ->
                    if (Pair(n.title, n.updatedAt) !in noteKeys) db.noteDao().insert(n)
                }
                withContext(Dispatchers.Main) { onDone(true, imported) }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onDone(false, 0) }
        }
    }
}
