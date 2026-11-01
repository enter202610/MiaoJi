package com.inkledger.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.TemplateEntity
import com.inkledger.app.data.db.TransactionEntity
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.MonthSwitcher
import com.inkledger.app.ui.components.SwipeToDeleteBox
import com.inkledger.app.ui.components.inkHorizontalSwipe
import com.inkledger.app.ui.theme.AmountBodyStyle
import com.inkledger.app.ui.theme.AmountLargeStyle
import com.inkledger.app.ui.theme.LocalInkExtras
import com.inkledger.app.util.dayLabel
import com.inkledger.app.util.daysBetween
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.monthTitle
import com.inkledger.app.util.nextMonthPeriod
import com.inkledger.app.util.prevMonthPeriod
import com.inkledger.app.util.toLocalDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class DayGroup(val day: LocalDate, val list: List<TransactionEntity>)

/** 首页：月汇总卡（含预算）+ 模板一键行 + 按日分组明细 */
/** 标题轮换文案池：文学/趣味为主，全部 ≤12 字；隔三差五展示一次 */
private val TITLE_TIPS = listOf(
    // 古典诗文
    "一蓑烟雨任平生",
    "人间有味是清欢",
    "诗酒趁年华",
    "此心安处是吾乡",
    "人生如逆旅，我亦是行人",
    "一点浩然气，千里快哉风",
    "浮生若梦，为欢几何",
    "偷得浮生半日闲",
    "人闲桂花落",
    "晚来天欲雪，能饮一杯无",
    "陌上花开，可缓缓归矣",
    "春水碧于天，画船听雨眠",
    "小舟从此逝，江海寄余生",
    "山中何事？松花酿酒，春水煎茶",
    "行到水穷处，坐看云起时",
    "也无风雨也无晴",
    "不畏浮云遮望眼",
    "岁月不居，时节如流",
    "盛年不重来，一日难再晨",
    "及时当勉励，岁月不待人",
    // 文名著作
    "凡是过往，皆为序章",
    "生活在别处",
    "人是一根会思考的芦苇",
    "我思故我在",
    "从前慢",
    "你站在桥上看风景",
    "面朝大海，春暖花开",
    "知足者富",
    "千里之行，始于足下",
    "一粥一饭，当思来处不易",
    // 妙计谐音
    "好记性，不如妙计",
    "记账的妙计，都在这里",
    "妙计在手，账目不愁",
    "你的每一笔，都有妙计",
    // 功能 · 引导
    "长按 ＋ 说话就能记账",
    "说「早饭三块五」试试",
    "模板一点，3 秒记完",
    "左滑账单可以删除",
    "倒数日支持每年重复",
    "灵活预算，花完自己申报",
    "AI 能帮你分析这个月的钱",
    "纯净模式在设置里"
)

/** 城市主题专属文案：选中城市主题时优先展示 */
private val CITY_TIPS = mapOf(
    "深圳" to listOf(
        "来了，就是深圳人",
        "相约深圳，见证奇迹"
    ),
    "上海" to listOf(
        "魔都的夜，每盏灯都值得",
        "海派精明，账目清爽"
    ),
    "北京" to listOf(
        "大城小事，皆入账中",
        "皇城根下，日子有光"
    ),
    "广州" to listOf(
        "食在广州，账在妙计",
        "一盅两件，烟火人间"
    ),
    "香港" to listOf(
        "维港灯火，账目分明",
        "中环节奏，笔笔清晰"
    ),
    "澳门" to listOf(
        "小城故事，笔笔清楚",
        "精打细算，方得从容"
    )
)

/** 挑选标题文案：城市主题优先城市文案；否则全池随机（排除上一条） */
private fun pickTitleTip(last: String?, theme: com.inkledger.app.data.prefs.ThemePreset): String? {
    val cityName = when (theme) {
        com.inkledger.app.data.prefs.ThemePreset.CITY_SHENZHEN -> "深圳"
        com.inkledger.app.data.prefs.ThemePreset.CITY_SHANGHAI -> "上海"
        com.inkledger.app.data.prefs.ThemePreset.CITY_BEIJING -> "北京"
        com.inkledger.app.data.prefs.ThemePreset.CITY_GUANGZHOU -> "广州"
        com.inkledger.app.data.prefs.ThemePreset.CITY_HK -> "香港"
        com.inkledger.app.data.prefs.ThemePreset.CITY_MACAU -> "澳门"
        else -> null
    }
    val today = java.time.LocalDate.now()
    val hour = java.time.LocalTime.now().hour
    return if (cityName != null) {
        (CITY_TIPS[cityName] ?: emptyList()).filter { it != last }.randomOrNull()
    } else {
        val pool = buildList {
            addAll(TITLE_TIPS)
            if (hour in 6..10) add("早安，从今天第一笔开始")
            if (hour >= 21) add("晚上好，今天的账记了吗")
            if (today.dayOfWeek.value == 1) add("新的一周，从第一笔开始")
        }
        pool.filter { it != last }.randomOrNull()
    }
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    periodStart: LocalDate,
    visible: Boolean = true,
    onChangePeriod: (LocalDate) -> Unit,
    onEditTx: (TransactionEntity) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTemplates: () -> Unit,
    onMessage: (String) -> Unit
) {
    val txs by vm.txs.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val categoryMap by vm.categoryMap.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val extras = LocalInkExtras.current
    val haptic = LocalHapticFeedback.current

    val startDay = settings.monthStartDay
    val currency = settings.currency

    val monthTxs = remember(txs, periodStart, startDay) {
        txs.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == periodStart }
    }
    val expense = monthTxs.filter { it.type == 0 }.sumOf { it.amountMinor }
    val income = monthTxs.filter { it.type == 1 }.sumOf { it.amountMinor }

    val groups = remember(monthTxs) {
        monthTxs.groupBy { it.timestamp.toLocalDate() }
            .toSortedMap(compareByDescending<LocalDate> { it })
            .map { (day, list) -> DayGroup(day, list.sortedByDescending { it.timestamp }) }
    }

    // ---- 搜索与筛选 ----
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var fType by remember { mutableIntStateOf(0) }   // 0 全部 1 支出 2 收入
    var fCat by remember { mutableStateOf(0L) }      // 0L = 全部分类
    var allScope by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsStateWithLifecycle()

    val results = remember(txs, monthTxs, query, fType, fCat, allScope, categoryMap, searching, currency) {
        if (!searching) emptyList()
        else {
            val base = if (allScope) txs else monthTxs
            base.filter { t ->
                (fType == 0 || (fType == 1 && t.type == 0) || (fType == 2 && t.type == 1)) &&
                    (fCat == 0L || t.categoryId == fCat) &&
                    (query.isBlank() || t.note.contains(query, true) ||
                        (categoryMap[t.categoryId]?.name ?: "").contains(query, true) ||
                        formatMoney(t.amountMinor, currency).contains(query))
            }
        }
    }
    val resultExpense = results.filter { it.type == 0 }.sumOf { it.amountMinor }
    val resultIncome = results.filter { it.type == 1 }.sumOf { it.amountMinor }

    // 首页展示哪个倒数日：置顶的优先（多个置顶取最近），没置顶取未来最近的
    val nearestCountdown = remember(countdowns) {
        val today = LocalDate.now()
        countdowns.mapNotNull { c ->
            var target = LocalDate.ofEpochDay(c.targetEpochDay)
            if (c.repeatYearly) {
                var guard = 0
                while (target.isBefore(today) && guard < 200) {
                    target = target.plusYears(1); guard++
                }
            }
            val days = ChronoUnit.DAYS.between(today, target)
            if (days >= 0) Triple(c, target, days) else null
        }.let { list ->
            list.sortedWith(
                compareByDescending<Triple<com.inkledger.app.data.db.CountDownEntity, LocalDate, Long>> { it.first.pinned }
                    .thenBy { it.third }
            ).firstOrNull()
        }
    }

    var deleteTxTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var successOverlay by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .inkHorizontalSwipe(
                onSwipeLeft = { onChangePeriod(periodStart.nextMonthPeriod(startDay)) },
                onSwipeRight = { onChangePeriod(periodStart.prevMonthPeriod(startDay)) }
            )
    ) {
        if (!settings.pureMode) item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 标题：妙计 ↔ 轮换文案
                // 触发规则：冷启动/从外面切回应用 → 必显示；应用内切到明细 → 距上次 ≥1 分钟才显示
                var showTip by remember { mutableStateOf(false) }
                var lastTip by rememberSaveable { mutableStateOf<String?>(null) }
                var titleTip by remember { mutableStateOf<String?>(null) }
                val lastShownAt = rememberSaveable { androidx.compose.runtime.mutableLongStateOf(0L) }
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                        if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) showTip = true
                    }
                    lifecycleOwner.lifecycle.addObserver(obs)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
                }
                LaunchedEffect(visible) {
                    if (visible && System.currentTimeMillis() - lastShownAt.longValue >= 60_000) showTip = true
                }
                LaunchedEffect(showTip) {
                    if (showTip) {
                        titleTip = pickTitleTip(lastTip, settings.theme)
                        lastTip = titleTip
                        lastShownAt.longValue = System.currentTimeMillis()
                        kotlinx.coroutines.delay(3000)
                        showTip = false
                    }
                }
                val iconAlpha by animateFloatAsState(
                    targetValue = if (showTip) 0f else 1f,
                    animationSpec = tween(450),
                    label = "titleIconAlpha"
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = showTip,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(450)) +
                                androidx.compose.animation.slideInHorizontally { it / 3 }) togetherWith
                                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(450)) +
                                androidx.compose.animation.slideOutHorizontally { -it / 3 })
                        },
                        modifier = Modifier.align(Alignment.CenterStart),
                        label = "homeTitle"
                    ) { tipShown ->
                        Text(
                            if (tipShown) titleTip ?: "妙计" else "妙计",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // 搜索 + 设置：玻璃胶囊（与底栏同质感），文案期整体淡为隐形（仍可点击）
                    Row(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer { alpha = iconAlpha }
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
                            .border(
                                width = 1.dp,
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)
                                    )
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        IconButton(onClick = {
                            searching = true
                        }) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索与筛选")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Rounded.Settings, contentDescription = "设置")
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (settings.pureMode) Modifier.statusBarsPadding() else Modifier)
                    .padding(start = 4.dp, end = 20.dp, top = if (settings.pureMode) 6.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    MonthSwitcher(
                            title = monthTitle(periodStart, startDay),
                            onPrev = { onChangePeriod(periodStart.prevMonthPeriod(startDay)) },
                            onNext = { onChangePeriod(periodStart.nextMonthPeriod(startDay)) },
                            isCurrent = periodStart == LocalDate.now().monthPeriodStart(startDay),
                            onBackToCurrent = { onChangePeriod(LocalDate.now().monthPeriodStart(startDay)) },
                            modifier = Modifier
                        )
                }
                if (settings.pureMode) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                }
            }
        }
        // 汇总卡（纯净模式也保留：本月支出三列核心数字）
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "本月支出",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 本月支出大数字独占一行
                    Text(
                        formatMoney(expense, currency),
                        style = AmountLargeStyle,
                        color = extras.expense,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1
                    )
                    // 收入 / 结余 各占一半宽度，标签在上金额在下，空间充裕小数永远完整
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "收入",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.animation.AnimatedContent(
                                targetState = income,
                                transitionSpec = {
                                    (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                                        (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
                                },
                                label = "incomeNum"
                            ) { v ->
                                Text(
                                    formatMoney(v, currency),
                                    style = AmountBodyStyle,
                                    color = extras.income,
                                    maxLines = 1
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "结余",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.animation.AnimatedContent(
                                targetState = income - expense,
                                transitionSpec = {
                                    (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                                        (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
                                },
                                label = "balanceNum"
                            ) { v ->
                                Text(
                                    formatMoney(v, currency),
                                    style = AmountBodyStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 纯净模式：隐藏预算条与倒数日，只留三列核心数字
                    if (!settings.pureMode) {
                        if (settings.budgetMinor > 0) {
                            val over = expense > settings.budgetMinor
                            LinearProgressIndicator(
                                progress = {
                                    (expense.toFloat() / settings.budgetMinor).coerceIn(0f, 1f)
                                },
                                color = if (over) extras.expense else MaterialTheme.colorScheme.primary,
                                trackColor = extras.track,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp)
                                    .height(6.dp)
                            )
                            val today = LocalDate.now()
                            val periodEnd = periodStart.nextMonthPeriod(startDay)
                            val daysLeft = ChronoUnit.DAYS.between(today, periodEnd).toInt() + 1
                            val remaining = settings.budgetMinor - expense
                            Text(
                                if (over) "已超支 ${formatMoney(-remaining, currency)}"
                                else "预算剩余 ${formatMoney(remaining, currency)}" +
                                    if (daysLeft > 0) " · 日均可用 ${formatMoney(remaining / daysLeft, currency)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (over) extras.expense
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        nearestCountdown?.let { (c, _, days) ->
                            Text(
                                if (days >= 0) "${c.emoji} 距离 ${c.name} 还有 ${days} 天"
                                else "${c.emoji} ${c.name} 已过 ${-days} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (searching) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        placeholder = { Text("搜备注、分类、金额…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            IconButton(onClick = {
                                searching = false; query = ""; fType = 0; fCat = 0L; allScope = false
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "关闭搜索")
                            }
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        listOf(0 to "全部", 1 to "支出", 2 to "收入").forEach { (v, label) ->
                            HomeFilterChip(label, fType == v) { fType = v }
                        }
                        HomeFilterChip(
                            if (allScope) "全部记录" else "仅本月",
                            allScope
                        ) { allScope = !allScope }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        item { HomeFilterChip("全部分类", fCat == 0L) { fCat = 0L } }
                        items(
                            categories.sortedWith(compareBy({ it.type }, { it.sortOrder })),
                            key = { it.id }
                        ) { c ->
                            HomeFilterChip("${c.emoji} ${c.name}", fCat == c.id) { fCat = c.id }
                        }
                    }
                    Text(
                        "找到 ${results.size} 笔 · 支出 ${formatMoney(resultExpense, currency)} · 收入 ${formatMoney(resultIncome, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
            }
            items(results, key = { "search${it.id}" }) { tx ->
                TxRow(
                    tx = tx,
                    cat = categoryMap[tx.categoryId],
                    currency = currency,
                    onClick = { onEditTx(tx) },
                    onDelete = {
                        vm.deleteTx(tx)
                        onMessage("已删除 ${formatMoney(tx.amountMinor, currency)}")
                    }
                )
            }
        }

        if (!searching && templates.isNotEmpty() && !settings.pureMode) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(templates, key = { "tpl${it.id}" }) { t ->
                        val cat = categoryMap[t.categoryId]
                        val tplCooldown = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable {
                                val now = System.currentTimeMillis()
                                if (now - tplCooldown.longValue < 1000) return@clickable
                                tplCooldown.longValue = now
                                vm.applyTemplate(t)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMessage("已记 ${formatMoney(t.amountMinor, currency)} · ${t.name}")
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(cat?.emoji ?: t.emoji, fontSize = 16.sp)
                                Text(
                                    " ${t.name} ${formatMoney(t.amountMinor, currency)}",
                                    style = AmountBodyStyle
                                )
                            }
                        }
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable(onClick = onOpenTemplates)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "管理模板",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        if (!searching && groups.isEmpty()) {
            item {
                EmptyHint("🍃", "这个月还没有记录", "点右下角 ＋ 记第一笔，或用上方模板一键记")
            }
        }
        if (!searching) {
            groups.forEach { group ->
            item(key = "day${group.day.toEpochDay()}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        dayLabel(group.day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val daySum = group.list.sumOf { if (it.type == 0) -it.amountMinor else it.amountMinor }
                    Text(
                        "小计 ${formatMoney(daySum, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(group.list, key = { it.id }) { tx ->
                Box(Modifier.animateItem()) {
                    SwipeToDeleteBox(onDelete = { deleteTxTarget = tx }) {
                        TxRow(
                            tx = tx,
                            cat = categoryMap[tx.categoryId],
                            currency = currency,
                            onClick = { onEditTx(tx) },
                            onDelete = {
                                vm.deleteTx(tx)
                                onMessage("已删除 ${formatMoney(tx.amountMinor, currency)}")
                            }
                        )
                    }
                }
            }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }


    deleteTxTarget?.let { tx ->
        com.inkledger.app.ui.components.InkAlertDialog(
            onDismissRequest = { deleteTxTarget = null },
            title = { Text("删除这笔？") },
            text = {
                Text("${categoryMap[tx.categoryId]?.name ?: "支出"} ${formatMoney(tx.amountMinor, currency)}${if (tx.note.isNotBlank()) " · ${tx.note}" else ""}")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.inkledger.app.ui.components.InkDialogButton("取消", onClick = { deleteTxTarget = null })
                    com.inkledger.app.ui.components.InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.deleteTx(tx)
                            deleteTxTarget = null
                            onMessage("已删除 ${formatMoney(tx.amountMinor, currency)}")
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
private fun HomeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun TxRow(
    tx: TransactionEntity,
    cat: CategoryEntity?,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val extras = LocalInkExtras.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(cat?.emoji ?: "📦", fontSize = 19.sp) }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                cat?.name ?: "未分类",
                style = MaterialTheme.typography.bodyLarge
            )
            if (tx.note.isNotBlank()) {
                Text(
                    tx.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            (if (tx.type == 0) "-" else "+") + formatMoney(tx.amountMinor, currency),
            style = AmountBodyStyle,
            color = if (tx.type == 0) extras.expense else extras.income,
            textAlign = TextAlign.End
        )
    }
}
