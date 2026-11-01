package com.inkledger.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.ui.components.Dot
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.MonthSwitcher
import com.inkledger.app.ui.components.RankBar
import com.inkledger.app.ui.components.RingChart
import com.inkledger.app.ui.components.inkHorizontalSwipe
import com.inkledger.app.ui.theme.AmountBodyStyle
import com.inkledger.app.ui.theme.LocalInkExtras
import com.inkledger.app.util.formatChart
import com.inkledger.app.util.formatCompact
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.monthTitle
import com.inkledger.app.util.nextMonthPeriod
import com.inkledger.app.util.prevMonthPeriod
import com.inkledger.app.util.toLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 统计页：占比环形 + 分类排行 + 近 6 个月趋势（Timi / Cashew 三件套） */
@Composable
fun StatsScreen(
    vm: AppViewModel,
    periodStart: LocalDate,
    onChangePeriod: (LocalDate) -> Unit
) {
    val txs by vm.txs.collectAsStateWithLifecycle()
    val categoryMap by vm.categoryMap.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val extras = LocalInkExtras.current
    val startDay = settings.monthStartDay
    val currency = settings.currency

    val monthTxs = remember(txs, periodStart, startDay) {
        txs.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == periodStart }
    }

    val expenseByCat = remember(monthTxs, categoryMap) {
        monthTxs.filter { it.type == 0 }
            .groupBy { it.categoryId }
            .map { (catId, list) -> catId to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
    }
    val totalExpense = expenseByCat.sumOf { it.second }

    // 环形取前 5 + 其他
    val ringData = remember(expenseByCat, categoryMap) {
        val top = expenseByCat.take(5)
        val rest = expenseByCat.drop(5).sumOf { it.second }
        buildList {
            top.forEach { add(RingEntry(categoryMap[it.first], it.second, false)) }
            if (rest > 0) add(RingEntry(null, rest, true))
        }
    }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        extras.income,
        extras.expense.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 本月概览：收入/支出/结余 + 日均 + 笔数 + 环比上月
    val overview = remember(monthTxs, txs, periodStart, startDay) {
        val income = monthTxs.filter { it.type == 1 }.sumOf { it.amountMinor }
        val expense = monthTxs.filter { it.type == 0 }.sumOf { it.amountMinor }
        val prevStart = periodStart.minusMonths(1)
        val prevExpense = txs.filter {
            it.type == 0 && it.timestamp.toLocalDate().monthPeriodStart(startDay) == prevStart
        }.sumOf { it.amountMinor }
        val daysSoFar = java.time.temporal.ChronoUnit.DAYS
            .between(periodStart, LocalDate.now()).toInt() + 1
        Triple(income, expense, prevExpense) to (daysSoFar to monthTxs.size)
    }

    val trend = remember(txs, periodStart, startDay) {
        val byPeriod = txs.groupBy { it.timestamp.toLocalDate().monthPeriodStart(startDay) }
        (5 downTo 0).map { offset ->
            val p = periodStart.minusMonths(offset.toLong())
            val list = byPeriod[p].orEmpty()
            TrendPoint(
                label = p.format(DateTimeFormatter.ofPattern("M月", Locale.CHINA)),
                expense = list.filter { it.type == 0 }.sumOf { it.amountMinor },
                income = list.filter { it.type == 1 }.sumOf { it.amountMinor }
            )
        }
    }
    val trendMax = maxOf(
        trend.maxOf { maxOf(it.expense, it.income) }.toFloat(), 1f
    )

    LazyColumn(
        Modifier
            .fillMaxSize()
            .inkHorizontalSwipe(
                onSwipeLeft = { onChangePeriod(periodStart.nextMonthPeriod(startDay)) },
                onSwipeRight = { onChangePeriod(periodStart.prevMonthPeriod(startDay)) }
            )
    ) {
        if (!settings.pureMode) item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("统计", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            MonthSwitcher(
                    title = monthTitle(periodStart, startDay),
                    onPrev = { onChangePeriod(periodStart.prevMonthPeriod(startDay)) },
                    onNext = { onChangePeriod(periodStart.nextMonthPeriod(startDay)) },
                    isCurrent = periodStart == LocalDate.now().monthPeriodStart(startDay),
                    onBackToCurrent = { onChangePeriod(LocalDate.now().monthPeriodStart(startDay)) },
                    modifier = Modifier
                        .then(if (settings.pureMode) Modifier.statusBarsPadding() else Modifier)
                        .padding(start = 4.dp)
            )
        }

        if (totalExpense == 0L) {
            item { EmptyHint("📊", "本月还没有支出", "记账之后这里会出现分类占比和趋势") }
        } else {
            item {
                InkCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val (inc, exp, prevExp) = overview.first
                    val (daysSoFar, txCount) = overview.second
                    val balance = inc - exp
                    Column(Modifier.padding(18.dp)) {
                        // 三列核心：支出/收入/结余
                        Row(Modifier.fillMaxWidth()) {
                            listOf(
                                Triple("支出", exp, extras.expense),
                                Triple("收入", inc, extras.income),
                                Triple("结余", balance, MaterialTheme.colorScheme.onSurface)
                            ).forEachIndexed { i, (label, v, color) ->
                                Column(
                                    horizontalAlignment = when (i) {
                                        0 -> Alignment.Start
                                        1 -> Alignment.CenterHorizontally
                                        else -> Alignment.End
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        formatCompact(v, currency),
                                        style = AmountBodyStyle, color = color, maxLines = 1
                                    )
                                }
                            }
                        }
                        val mom = if (prevExp > 0) Math.round((exp - prevExp) * 100.0 / prevExp).toInt() else null
                        Text(
                            buildString {
                                append("共 ${txCount} 笔")
                                if (daysSoFar > 0) append(" · 日均 ${formatCompact(kotlin.math.round(exp * 1.0 / daysSoFar).toLong(), currency)}")
                                if (mom != null && mom != 0) {
                                    append(if (mom > 0) " · 比上月 +$mom%" else " · 比上月 ${-mom}%")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            item {
                InkCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingChart(
                            segments = ringData.mapIndexed { i, e ->
                                palette[i % palette.size] to
                                    (e.amount.toFloat() / totalExpense)
                            },
                            modifier = Modifier.size(150.dp),
                            strokeWidth = 16.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "支出",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatMoney(totalExpense, currency),
                                    style = AmountBodyStyle,
                                    color = extras.expense
                                )
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            ringData.forEachIndexed { i, e ->
                                val name = e.cat?.name ?: "其他"
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Dot(palette[i % palette.size])
                                    Text(
                                        " $name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${Math.round(e.amount * 100.0 / totalExpense)}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                InkCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        expenseByCat.forEach { (catId, sum) ->
                            val cat = categoryMap[catId]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(cat?.emoji ?: "📦", fontSize = 16.sp)
                                Text(
                                    " ${cat?.name ?: "未分类"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatMoney(sum, currency),
                                    style = AmountBodyStyle
                                )
                            }
                            RankBar(
                                fraction = sum.toFloat() / totalExpense,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            item {
                InkCard(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("近 6 个月", style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            trend.forEach { p ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // 柱顶只标支出数值（收入看图例）；≥1万用万缩写，小数精确
                                    // 标签与支出柱左对齐 + 支出色着色，归属一目了然
                                    val valueLabel = if (p.expense > 0) formatChart(p.expense) else ""
                                    // 标签行与柱行同构：标签盒=支出柱槽位（9dp），文字以其中心溢出居中
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(
                                            Modifier.height(16.dp).width(9.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(
                                                valueLabel,
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = extras.expense,
                                                modifier = Modifier.wrapContentWidth(
                                                    align = Alignment.CenterHorizontally,
                                                    unbounded = true
                                                )
                                            )
                                        }
                                        Spacer(Modifier.width(9.dp))
                                    }
                                    Row(
                                        Modifier.height(96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(
                                            Modifier
                                                .fillMaxHeight(
                                                    (p.expense.toFloat() / trendMax).coerceIn(0.02f, 1f)
                                                )
                                                .width(9.dp)
                                                .background(
                                                    extras.expense.copy(alpha = 0.8f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Box(
                                            Modifier
                                                .fillMaxHeight(
                                                    (p.income.toFloat() / trendMax).coerceIn(0.02f, 1f)
                                                )
                                                .width(9.dp)
                                                .background(
                                                    extras.income.copy(alpha = 0.8f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }
                                    Text(
                                        p.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            Dot(extras.expense.copy(alpha = 0.8f))
                            Text("支出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Dot(extras.income.copy(alpha = 0.8f))
                            Text("收入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

private data class RingEntry(val cat: CategoryEntity?, val amount: Long, val isOther: Boolean)
private data class TrendPoint(val label: String, val expense: Long, val income: Long)
