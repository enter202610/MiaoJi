package com.inkledger.app.ui.goals

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.CountDownEntity
import com.inkledger.app.data.db.GoalEntity
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkAlertDialog
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.InkDialogButton
import com.inkledger.app.ui.components.SwipeToDeleteBox
import com.inkledger.app.ui.components.inkHorizontalSwipe
import com.inkledger.app.ui.theme.AmountBodyStyle
import com.inkledger.app.ui.theme.DaysBigStyle
import com.inkledger.app.ui.theme.LocalInkExtras
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.keypadToMinor
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.nextMonthPeriod
import com.inkledger.app.util.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val EMOJI_PRESETS = listOf(
    // 三行×8：最常用（存钱/打卡/倒数通用）
    "💰", "🎯", "🐷", "💪", "🏃", "📚", "💻", "🏠",
    "🚗", "📱", "✈️", "🏖️", "🎓", "💍", "🎂", "⏳",
    "❤️", "🎁", "🎵", "☕", "🍜", "🎮", "🐶", "🎄"
)

/** 固定三行的图标选择器：8 个一行，选中高亮 */
@Composable
private fun EmojiPickerGrid(selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EMOJI_PRESETS.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { e ->
                    Text(
                        e,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .background(
                                if (e == selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onPick(e) }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

/** 申报花销弹窗：本期记录列表（可删错填的）+ 金额输入提交 */
@Composable
private fun FlexSpendDialog(
    item: com.inkledger.app.data.prefs.FlexItem,
    spends: List<com.inkledger.app.data.prefs.FlexSpend>,
    currency: String,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val fmt = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("申报「${item.name}」花销") },
        text = {
            Column {
                if (spends.isEmpty()) {
                    Text(
                        "本期还没有申报记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        spends.sortedByDescending { it.at }.forEach { s ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    java.time.Instant.ofEpochMilli(s.at)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(fmt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatMoney(s.amountMinor, currency),
                                    style = AmountBodyStyle,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "删除这笔申报",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onDelete(s.at) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("本次花销（元）") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkDialogButton("取消", onClick = onDismiss)
                InkDialogButton(
                    "申报",
                    primary = true,
                    onClick = {
                        val m = com.inkledger.app.util.keypadToMinor(amountText) ?: 0L
                        if (m <= 0L) return@InkDialogButton
                        onAdd(m)
                    }
                )
            }
        },
        dismissButton = {}
    )
}

/** 灵活预算项配置（新建/编辑）：名称 + 金额上限 + 周期；花销走申报制，不关联账单 */
@Composable
private fun FlexBudgetDialog(
    initial: com.inkledger.app.data.prefs.FlexItem?,
    nextId: Long,
    onDismiss: () -> Unit,
    onSave: (com.inkledger.app.data.prefs.FlexItem) -> Unit,
    onRemove: (com.inkledger.app.data.prefs.FlexItem) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amountText by remember {
        val m = initial?.minor ?: 0L
        mutableStateOf(
            if (m > 0) if (m % 100 == 0L) "${m / 100}" else String.format(java.util.Locale.US, "%.2f", m / 100.0)
            else ""
        )
    }
    var period by remember { mutableStateOf(initial?.period ?: 0) }

    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("灵活预算项") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(8) },
                    label = { Text("名称，如：周末聚餐") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("每期上限（元）") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    listOf(0 to "每周", 1 to "每周期").forEach { (v, label) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (period == v) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { period = v }
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (period == v) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Text(
                    "这是专项提醒额度，独立于月度总预算：花完主动点「申报」记录，每期重新计；月度总预算仍按全部账单自动统计，两者互不改动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial != null) {
                    InkDialogButton("移除", onClick = { onRemove(initial) }, danger = true)
                }
                InkDialogButton("取消", onClick = onDismiss)
                InkDialogButton(
                    "保存",
                    primary = true,
                    onClick = {
                        val m = com.inkledger.app.util.keypadToMinor(amountText) ?: 0L
                        if (name.isBlank() || m <= 0L) return@InkDialogButton
                        onSave(
                            com.inkledger.app.data.prefs.FlexItem(
                                id = initial?.id ?: nextId,
                                name = name.trim(), minor = m, period = period
                            )
                        )
                    }
                )
            }
        },
        dismissButton = {}
    )
}

/** 目标页：月预算 + 三个子页签（存钱 / 打卡 / 倒数日，左右滑动切换）；新增统一走底栏 ＋ 的上下文信号 */
@Composable
fun GoalsScreen(
    vm: AppViewModel,
    onMessage: (String) -> Unit,
    tab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    addSignal: Int? = null,
    onAddConsumed: () -> Unit = {},
    onOpenCountdown: (Long) -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val txs by vm.txs.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val allDeposits by vm.allDeposits.collectAsStateWithLifecycle()
    val allChecks by vm.allHabitChecks.collectAsStateWithLifecycle()

    val currency = settings.currency
    val startDay = settings.monthStartDay

    var showBudgetDialog by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var showNewGoalKind by remember { mutableStateOf<Int?>(null) } // null / 0 存钱 / 1 打卡
    var depositGoal by remember { mutableStateOf<GoalEntity?>(null) }
    var editCountdown by remember { mutableStateOf<CountDownEntity?>(null) }
    var showNewCountdown by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GoalEntity?>(null) }
    var historyGoal by remember { mutableStateOf<GoalEntity?>(null) }  // 打卡历史查看器
    var deleteCountdownTarget by remember { mutableStateOf<CountDownEntity?>(null) }
    var celebrating by remember { mutableStateOf<Pair<GoalEntity, Long>?>(null) }
    var flexEditFor by remember { mutableStateOf<com.inkledger.app.data.prefs.FlexItem?>(null) }
    var flexNewDialog by remember { mutableStateOf(false) }
    var flexSpendFor by remember { mutableStateOf<com.inkledger.app.data.prefs.FlexItem?>(null) }
    var successOverlay by remember { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // 目标达成检测：首次越过目标金额 → 庆祝一次（持久化，重启/换页不再弹）
    LaunchedEffect(goals, allDeposits, settings.celebratedGoals) {
        goals.filter { it.kind == 0 && (it.targetAmountMinor ?: 0) > 0 }.forEach { g ->
            val sum = allDeposits.filter { it.goalId == g.id }.sumOf { it.amountMinor }
            if (sum >= (g.targetAmountMinor ?: 0) && g.id.toString() !in settings.celebratedGoals) {
                vm.markCelebratedGoal(g.id)
                celebrating = g to sum
            }
        }
    }

    // 底栏 ＋ 的上下文信号：按当前子页签弹对应新增弹窗
    LaunchedEffect(addSignal) {
        when (addSignal) {
            0 -> showNewGoalKind = 0
            1 -> showNewGoalKind = 1
            2 -> showNewCountdown = true
            else -> return@LaunchedEffect
        }
        onAddConsumed()
    }

    val currentPeriod = LocalDate.now().monthPeriodStart(startDay)
    val periodExpense = remember(txs, startDay) {
        txs.filter { it.type == 0 && it.timestamp.toLocalDate().monthPeriodStart(startDay) == currentPeriod }
            .sumOf { it.amountMinor }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .inkHorizontalSwipe(
                onSwipeLeft = { if (tab < 2) onTabChange(tab + 1) },
                onSwipeRight = { if (tab > 0) onTabChange(tab - 1) }
            )
    ) {
        if (!settings.pureMode) item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text("目标", style = MaterialTheme.typography.titleLarge) }
        }

        // ---- 月预算卡 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(if (settings.pureMode) Modifier.statusBarsPadding() else Modifier)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("月度预算", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable { showBudgetDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (settings.budgetMinor > 0) Icons.Rounded.Edit else Icons.Rounded.Add,
                                contentDescription = "设置预算",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (settings.budgetMinor > 0) {
                        val extras = LocalInkExtras.current
                        val over = periodExpense > settings.budgetMinor
                        val budgetProgress by animateFloatAsState(
                            targetValue = (periodExpense.toFloat() / settings.budgetMinor).coerceIn(0f, 1f),
                            animationSpec = tween(400),
                            label = "budgetProgress"
                        )
                        LinearProgressIndicator(
                            progress = { budgetProgress },
                            color = if (over) extras.expense else MaterialTheme.colorScheme.primary,
                            trackColor = extras.track,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(6.dp)
                        )
                        val daysLeft =
                            (ChronoUnit.DAYS.between(LocalDate.now(), currentPeriod.nextMonthPeriod(startDay)) + 1)
                                .coerceAtLeast(0)
                        val remaining = settings.budgetMinor - periodExpense
                        Text(
                            if (over) "已超支 ${formatMoney(-remaining, currency)}"
                            else "剩余 ${formatMoney(remaining, currency)}" +
                                if (daysLeft > 0) " · 日均可用 ${formatMoney(remaining / daysLeft, currency)}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (over) extras.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            "给这个月设个总预算，花销心里有数",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // ---- 灵活预算项（申报制，可配置多个）----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("灵活预算", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clickable {
                                    flexNewDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "添加灵活预算项",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (settings.flexItems.isEmpty()) {
                        Text(
                            "给自己立个专项额度，如：周末聚餐 每周 ¥150",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    settings.flexItems.forEach { item ->
                            val extras = LocalInkExtras.current
                            val weekly = item.period == 0
                            val mon = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                            val flexStart = if (weekly) mon else currentPeriod
                            val flexEnd = if (weekly) mon.plusDays(6) else currentPeriod.nextMonthPeriod(startDay).minusDays(1)
                            val startMillis = flexStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endMillis = flexEnd.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val flexSpent = settings.flexSpends
                                .filter { it.itemId == item.id && it.at >= startMillis && it.at < endMillis }
                                .sumOf { it.amountMinor }
                            val flexOver = flexSpent > item.minor
                            Column(Modifier.padding(top = 14.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        (if (weekly) "本周" else "本周期") + " · " + item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        formatMoney(flexSpent, currency),
                                        style = AmountBodyStyle,
                                        color = if (flexOver) extras.expense else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        " / " + formatMoney(item.minor, currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (flexSpent.toFloat() / item.minor).coerceIn(0f, 1f) },
                                    color = if (flexOver) extras.expense else MaterialTheme.colorScheme.primary,
                                    trackColor = extras.track,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .height(6.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    // 申报主按钮：大触控区
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (flexOver) extras.expense.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                flexSpendFor = item
                                            }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = if (flexOver) extras.expense else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                " 申报" + (if (flexOver) "（已超支）" else "花销"),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (flexOver) extras.expense else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    // 编辑小按钮
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                            .clickable { flexEditFor = item },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Tune,
                                            contentDescription = "编辑灵活预算",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }

        // ---- 子页签（新增统一走底栏 ＋，不再放小按钮） ----
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("存钱", "打卡", "倒数日").forEachIndexed { i, label ->
                    val (tabInteraction, tabScale) = com.inkledger.app.ui.components.rememberPressScale(0.94f)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (tab == i) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .then(tabScale)
                            .clickable(
                                interactionSource = tabInteraction,
                                indication = null
                            ) {
                                onTabChange(i)
                            }
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (tab == i) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        when (tab) {
            0 -> {
                val savings = goals.filter { it.kind == 0 }
                if (savings.isEmpty()) {
                    item { EmptyHint("🐷", "还没有存钱目标", "点右上角 ＋ 立一个，如「买相机 ¥8000」") }
                }
                items(savings.size, key = { savings[it].id }) { i ->
                    val g = savings[i]
                    val cur = allDeposits.filter { it.goalId == g.id }.sumOf { it.amountMinor }
                    Box(Modifier.animateItem()) {
                        SwipeToDeleteBox(onDelete = { deleteTarget = g }) {
                            SavingsCard(g, cur, currency, onDeposit = { depositGoal = g }, onEdit = { editGoal = g })
                        }
                    }
                }
            }
            1 -> {
                val habits = goals.filter { it.kind == 1 }
                if (habits.isEmpty()) {
                    item { EmptyHint("🔥", "还没有打卡习惯", "点右上角 ＋ 加一个，如「不喝奶茶」「运动半小时」") }
                }
                items(habits.size, key = { habits[it].id }) { i ->
                    val g = habits[i]
                    val checks = allChecks.filter { it.goalId == g.id }.map { it.epochDay }.toSet()
                    Box(Modifier.animateItem()) {
                        SwipeToDeleteBox(onDelete = { deleteTarget = g }) {
                            HabitCard(
                                g, checks,
                                onToggle = {
                                    if (LocalDate.now().toEpochDay() !in checks) {
                                        successOverlay = "打卡成功 · ${g.name}"
                                    }
                                    vm.toggleHabit(g.id)
                                },
                                onEdit = { editGoal = g },
                                onHistory = { historyGoal = g }
                            )
                        }
                    }
                }
            }
            else -> {
                val today = LocalDate.now()
                val sorted = countdowns.map { c ->
                    var target = LocalDate.ofEpochDay(c.targetEpochDay)
                    if (c.repeatYearly) {
                        var guard = 0
                        while (target.isBefore(today) && guard < 200) { target = target.plusYears(1); guard++ }
                    }
                    val days = ChronoUnit.DAYS.between(today, target)
                    Triple(c, target, days)
                }.sortedWith(
                    compareByDescending<Triple<CountDownEntity, LocalDate, Long>> { it.first.pinned }
                        .thenBy { if (it.third >= 0) it.third else 100_000 - it.third }
                )
                if (sorted.isEmpty()) {
                    item { EmptyHint("⏳", "还没有倒数日", "点右上角 ＋ 记一个重要日子，支持每年重复") }
                }
                items(sorted.size, key = { sorted[it].first.id }) { i ->
                    val (c, target, days) = sorted[i]
                    Box(Modifier.animateItem()) {
                        SwipeToDeleteBox(onDelete = { deleteCountdownTarget = c }) {
                            CountdownCard(
                                c = c, target = target, days = days,
                                onClick = { onOpenCountdown(c.id) },
                                onTogglePin = { vm.toggleCountdownPin(c) }
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    // ---- 弹窗们 ----
    if (showBudgetDialog) {
        AmountDialog(
            title = if (settings.budgetMinor > 0) "修改月预算" else "设置月预算",
            initial = settings.budgetMinor,
            confirmText = "保存",
            allowRemove = settings.budgetMinor > 0,
            onDismiss = { showBudgetDialog = false },
            onConfirm = { minor -> vm.setBudget(minor); showBudgetDialog = false },
            onRemove = { vm.setBudget(0); showBudgetDialog = false }
        )
    }
    if (flexNewDialog || flexEditFor != null) {
        FlexBudgetDialog(
            initial = flexEditFor,
            nextId = (settings.flexItems.maxOfOrNull { it.id } ?: 0L) + 1,
            onDismiss = { flexNewDialog = false; flexEditFor = null },
            onSave = { item ->
                vm.setFlexItems(settings.flexItems.filter { it.id != item.id } + item)
                flexNewDialog = false; flexEditFor = null
                onMessage("灵活预算已保存")
            },
            onRemove = { item ->
                vm.setFlexItems(settings.flexItems.filter { it.id != item.id })
                flexEditFor = null
                onMessage("已移除灵活预算项")
            }
        )
    }
    flexSpendFor?.let { item ->
        val weekly = item.period == 0
        val mon = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val wStart = (if (weekly) mon else currentPeriod)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val wEnd = (if (weekly) mon.plusDays(6) else currentPeriod.nextMonthPeriod(startDay).minusDays(1))
            .plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val windowSpends = settings.flexSpends
            .filter { it.itemId == item.id && it.at >= wStart && it.at < wEnd }
        FlexSpendDialog(
            item = item,
            spends = windowSpends,
            currency = currency,
            onDismiss = { flexSpendFor = null },
            onAdd = { minor ->
                vm.addFlexSpend(item.id, minor)
                flexSpendFor = null
                successOverlay = "已申报 ${formatMoney(minor, currency)} · ${item.name}"
            },
            onDelete = { at ->
                vm.removeFlexSpend(item.id, at)
                onMessage("已删除这笔申报")
            }
        )
    }
    showNewGoalKind?.let { kind ->
        GoalDialog(
            initial = null,
            kind = kind,
            onDismiss = { showNewGoalKind = null },
            onSave = { vm.saveGoal(it); showNewGoalKind = null }
        )
    }
    editGoal?.let { g ->
        GoalDialog(
            initial = g,
            kind = g.kind,
            onDismiss = { editGoal = null },
            onSave = { vm.saveGoal(it); editGoal = null },
            onDelete = { vm.deleteGoal(g); editGoal = null }
        )
    }
    depositGoal?.let { g ->
        DepositDialog(
            goal = g,
            currency = currency,
            onDismiss = { depositGoal = null },
            onConfirm = { minor ->
                vm.addDeposit(g.id, minor)
                onMessage(if (minor > 0) "已存入 ${formatMoney(minor, currency)}" else "已取出 ${formatMoney(-minor, currency)}")
                successOverlay = if (minor > 0) "已存入 ${formatMoney(minor, currency)}" else "已取出 ${formatMoney(-minor, currency)}"
                depositGoal = null
            }
        )
    }
    if (showNewCountdown) {
        CountdownDialog(
            initial = null,
            onDismiss = { showNewCountdown = false },
            onSave = { vm.saveCountdown(it); showNewCountdown = false }
        )
    }
    editCountdown?.let { c ->
        CountdownDialog(
            initial = c,
            onDismiss = { editCountdown = null },
            onSave = { vm.saveCountdown(it); editCountdown = null },
            onDelete = { vm.deleteCountdown(c); editCountdown = null }
        )
    }
    deleteTarget?.let { g ->
        InkAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${g.name}」？") },
            text = { Text("目标及其全部记录会被删除，无法恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { deleteTarget = null })
                    InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            vm.deleteGoal(g)
                            deleteTarget = null
                            onMessage("已删除「${g.name}」")
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
    celebrating?.let { (g, sum) ->
        CelebrationOverlay(g.name, sum, currency) { celebrating = null }
    }
    historyGoal?.let { g ->
        HabitHistoryDialog(
            g,
            allChecks.filter { it.goalId == g.id }.map { it.epochDay }.toSet(),
            onToggleDay = { date -> vm.toggleHabit(g.id, date.toEpochDay()) }
        ) { historyGoal = null }
    }
    successOverlay?.let { msg ->
        com.inkledger.app.ui.components.SuccessOverlay(text = msg) { successOverlay = null }
    }
    deleteCountdownTarget?.let { c ->
        InkAlertDialog(
            onDismissRequest = { deleteCountdownTarget = null },
            title = { Text("删除「${c.name}」？") },
            text = { Text("倒数日会被删除，无法恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { deleteCountdownTarget = null })
                    InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            vm.deleteCountdown(c)
                            deleteCountdownTarget = null
                            onMessage("已删除「${c.name}」")
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
private fun SavingsCard(
    g: GoalEntity,
    current: Long,
    currency: String,
    onDeposit: () -> Unit,
    onEdit: () -> Unit
) {
    val extras = LocalInkExtras.current
    InkCard(
        Modifier
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp).clickable(onClick = onEdit)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(g.emoji, fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium)
                    g.deadlineEpochDay?.let { dl ->
                        val deadline = LocalDate.ofEpochDay(dl)
                        val left = ChronoUnit.DAYS.between(LocalDate.now(), deadline)
                        val line = if (left >= 0) {
                            "期限 ${deadline.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))} · 剩 $left 天"
                        } else "已到期"
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 通栏「存入」大按钮：主要操作大触控区（用户要求按钮做大好点）
            val bigDepInteraction = remember { MutableInteractionSource() }
            val bigDepPressed by bigDepInteraction.collectIsPressedAsState()
            val bigDepScale by animateFloatAsState(
                if (bigDepPressed) 0.97f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "bigDepScale"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(46.dp)
                    .scale(bigDepScale)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = bigDepInteraction,
                        indication = null
                    ) { onDeposit() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    " 存入",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            val target = g.targetAmountMinor ?: 0L
            if (target > 0) {
                val p by animateFloatAsState(
                    targetValue = (current.toFloat() / target).coerceIn(0f, 1f),
                    animationSpec = tween(400),
                    label = "savingsProgress"
                )
                LinearProgressIndicator(
                    progress = { p },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = extras.track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(6.dp)
                )
                Row(Modifier.padding(top = 6.dp)) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
                        },
                        label = "savingsAmount"
                    ) { v ->
                        Text(formatMoney(v, currency), style = AmountBodyStyle)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${Math.round(current * 100.0 / target)}%",
                        style = AmountBodyStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    "已存 ${formatMoney(current, currency)}",
                    style = AmountBodyStyle,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitCard(
    g: GoalEntity,
    checks: Set<Long>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    val todayEpoch = today.toEpochDay()
    val checkedToday = todayEpoch in checks

    // 打卡大圆按钮：按压缩放的弹簧反馈
    val circleInteraction = remember { MutableInteractionSource() }
    val circlePressed by circleInteraction.collectIsPressedAsState()
    val circleScale by animateFloatAsState(
        targetValue = if (circlePressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "habitScale"
    )
    // 打卡成功弹跳动画
    val popAnim = remember { Animatable(1f) }
    LaunchedEffect(checkedToday) {
        if (checkedToday) {
            popAnim.snapTo(0.6f)
            popAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f))
        }
    }

    // 连续天数：今天没打则从昨天起算（宽容设计，不计断签惩罚）
    var streak = 0
    var d = if (checkedToday) today else today.minusDays(1)
    while (d.toEpochDay() in checks) { streak++; d = d.minusDays(1) }
    android.util.Log.d(
        "InkHabit",
        "id=${g.id} name=${g.name} today=$todayEpoch checkedToday=$checkedToday streak=$streak checks=${checks.sorted()}"
    )

    val monthStartDay = today.withDayOfMonth(1).toEpochDay()
    val monthCount = checks.count { it >= monthStartDay && it <= todayEpoch }

    InkCard(
        Modifier
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .scale(circleScale * popAnim.value)
                        .background(
                            if (checkedToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .clickable(
                            interactionSource = circleInteraction,
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "打卡",
                        tint = if (checkedToday) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .clickable(onClick = onEdit)
                ) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium)
                    androidx.compose.animation.AnimatedContent(
                        targetState = streak,
                        transitionSpec = {
                            (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                                (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
                        },
                        label = "habitStreak"
                    ) { s ->
                        Text(
                            if (checkedToday) "🔥 连续 $s 天 · 本月 $monthCount 天"
                            else if (s > 0) "🔥 连续 $s 天（截至昨天）· 本月 $monthCount 天"
                            else "本月 $monthCount 天，今天从一枚圆点开始",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onHistory, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = "打卡历史",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // 本月打卡日历：紧凑格；历史点右上 ↺ 查看（可补打卡）
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    "本月 $monthCount/${today.lengthOfMonth()} 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HabitMonthGrid(today.withDayOfMonth(1), checks, today, compact = true)
            }
        }
    }
}

/** 任意月份的打卡日历（周一对齐）：已打=主题色点，未来=浅灰，未打=轨道色；
 *  onToggleDay 非空时，过去的日期可点按补打卡/取消（历史修正） */
@Composable
private fun HabitMonthGrid(
    month: LocalDate,
    checks: Set<Long>,
    today: LocalDate,
    onToggleDay: ((LocalDate) -> Unit)? = null,
    compact: Boolean = false
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 2.dp else 6.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val firstOffset = month.withDayOfMonth(1).dayOfWeek.value - 1  // 周一=0
        val cells: List<Int?> = List(firstOffset) { null } + (1..month.lengthOfMonth()).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                week.forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (compact) 18.dp else 26.dp)
                            .then(
                                if (d != null && onToggleDay != null &&
                                    !month.withDayOfMonth(d).isAfter(today)
                                ) Modifier.clickable { onToggleDay(month.withDayOfMonth(d)) }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (d != null) {
                            val date = month.withDayOfMonth(d)
                            val done = date.toEpochDay() in checks
                            val future = date.isAfter(today)
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        when {
                                            done -> MaterialTheme.colorScheme.primary
                                            future -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            else -> LocalInkExtras.current.track
                                        },
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 打卡历史查看器：按月翻看全部历史打卡；点过去的日期可补打卡/取消 */
@Composable
private fun HabitHistoryDialog(
    g: GoalEntity,
    checks: Set<Long>,
    onToggleDay: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val currentMonth = today.withDayOfMonth(1)
    val createdMonth = java.time.Instant.ofEpochMilli(g.createdAt)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().withDayOfMonth(1)
    var month by remember { mutableStateOf(currentMonth) }
    val canPrev = month.isAfter(createdMonth)
    val canNext = month.isBefore(currentMonth)
    val monthCount = checks.count {
        val d = LocalDate.ofEpochDay(it)
        d >= month && d < month.plusMonths(1)
    }
    // 最长连续天数
    var longest = 0
    var run = 0
    var prev: Long? = null
    checks.sorted().forEach { d ->
        run = if (prev != null && d == prev + 1) run + 1 else 1
        if (run > longest) longest = run
        prev = d
    }
    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(g.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "打卡历史",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { month = month.minusMonths(1) }, enabled = canPrev) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "上一月",
                            tint = if (canPrev) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    Text(
                        "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = canNext) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "下一月",
                            tint = if (canNext) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                Text(
                    "本月打卡 $monthCount/${month.lengthOfMonth()} 天 · 点日期可补打卡/取消",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HabitMonthGrid(month, checks, today, onToggleDay)
                Text(
                    "累计打卡 ${checks.size} 天 · 最长连续 $longest 天",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        },
        confirmButton = { InkDialogButton("完成", onClick = onDismiss) },
        dismissButton = {}
    )
}

@Composable
private fun CountdownCard(
    c: CountDownEntity,
    target: LocalDate,
    days: Long,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val today = days == 0L
    val past = days < 0
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val (cardInteraction, cardScale) = com.inkledger.app.ui.components.rememberPressScale(0.98f)
    // 与 HabitCard 一致：卡片不加横向 padding（SwipeToDeleteBox 已带），宽度对齐打卡条目
    InkCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(16.dp)
                .then(cardScale)
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null
                ) {
                    onClick()
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(c.emoji, fontSize = 20.sp) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                // 名字前带「距离」：距离 考研 还有 30 天；已过/今天显示原名
                // 单行省略：名字过长折行会在「距离」和名称间产生空白，市面做法是截断
                Text(
                    if (past) c.name else "距离 ${c.name}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    (if (c.repeatYearly) "每年 · " else "") +
                        target.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (today) {
                    Text("🎉", style = DaysBigStyle)
                    Text(
                        "就是今天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${kotlin.math.abs(days)}",
                            style = DaysBigStyle,
                            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            " 天",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        if (past) "已过" else "还有",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(30.dp)
            ) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (c.pinned) "取消置顶" else "置顶",
                    tint = if (c.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ---- 弹窗 ----

@Composable
private fun AmountDialog(
    title: String,
    initial: Long,
    confirmText: String,
    allowRemove: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onRemove: () -> Unit
) {
    var input by remember {
        mutableStateOf(
            if (initial > 0) {
                if (initial % 100 == 0L) "${initial / 100}"
                else String.format(java.util.Locale.US, "%.2f", initial / 100.0)
            } else ""
        )
    }
            InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { s -> input = s.filter { it.isDigit() || it == '.' }.take(10) },
                label = { Text("金额（元）") },
                singleLine = true
            )
        },
        confirmButton = {
            Column {
                if (allowRemove) {
                    InkDialogButton("清除预算", onClick = onRemove, danger = true)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = onDismiss)
                    InkDialogButton(
                        confirmText,
                        primary = true,
                        onClick = { keypadToMinor(input)?.let(onConfirm) }
                    )
                }
            }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    initial: GoalEntity?,
    kind: Int,
    onDismiss: () -> Unit,
    onSave: (GoalEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: if (kind == 0) "🐷" else "💪") }
    var amountText by remember {
        mutableStateOf(
        initial?.targetAmountMinor?.let { m ->
            if (m % 100 == 0L) "${m / 100}"
            else String.format(java.util.Locale.US, "%.2f", m / 100.0)
        } ?: ""
    )
    }
    var deadlineEpoch by remember { mutableStateOf(initial?.deadlineEpochDay) }
    var showDp by remember { mutableStateOf(false) }

    if (showDp) {
        com.inkledger.app.ui.components.WheelDatePickerDialog(
            initial = LocalDate.ofEpochDay(deadlineEpoch ?: LocalDate.now().plusDays(90).toEpochDay()),
            onDismiss = { showDp = false },
            onConfirm = {
                deadlineEpoch = it.toEpochDay()
                showDp = false
            }
        )
    }

            InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) (if (kind == 0) "新建存钱目标" else "新建打卡习惯") else "编辑") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(if (kind == 0) "目标名称，如：买相机" else "习惯名称，如：不喝奶茶") },
                    singleLine = true
                )
                if (kind == 0) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { s -> amountText = s.filter { it.isDigit() || it == '.' }.take(10) },
                        label = { Text("目标金额（元），可不填") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        TextButton(onClick = { showDp = true }) {
                            Text(if (deadlineEpoch == null) "＋ 设期限" else "改期限")
                        }
                        deadlineEpoch?.let { dl ->
                            Text(
                                LocalDate.ofEpochDay(dl)
                                    .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            TextButton(onClick = { deadlineEpoch = null }) { Text("清除") }
                        }
                    }
                }
                Text("图标", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                EmojiPickerGrid(selected = emoji, onPick = { emoji = it })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    InkDialogButton("删除", onClick = onDelete, danger = true)
                }
                InkDialogButton("取消", onClick = onDismiss)
                InkDialogButton(
                    "保存",
                    primary = true,
                    onClick = {
                        if (name.isBlank()) return@InkDialogButton
                        onSave(
                            (initial ?: GoalEntity(kind = kind, name = "", createdAt = 0)).copy(
                                kind = kind,
                                name = name.trim(),
                                emoji = emoji,
                                targetAmountMinor = if (kind == 0) keypadToMinor(amountText) else null,
                                deadlineEpochDay = if (kind == 0) deadlineEpoch else null
                            )
                        )
                    }
                )
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun DepositDialog(
    goal: GoalEntity,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    fun fire(value: Long) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onConfirm(value)
    }
    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.emoji} ${goal.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { s -> input = s.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("金额（元）") },
                    singleLine = true
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    listOf("+100", "+500", "+1000").forEach { q ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                q,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clickable { input = q.removePrefix("+") }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 取出：圆角图标按钮
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable {
                        keypadToMinor(input)?.let { fire(-it) }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            " 取出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // 存入：圆角图标按钮（主色）
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        keypadToMinor(input)?.let { fire(it) }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Savings,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            " 存入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CountdownDialog(
    initial: CountDownEntity?,
    onDismiss: () -> Unit,
    onSave: (CountDownEntity) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "⏳") }
    var epochDay by remember { mutableStateOf(initial?.targetEpochDay ?: (LocalDate.now().plusDays(30).toEpochDay())) }
    var repeat by remember { mutableStateOf(initial?.repeatYearly ?: false) }
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        com.inkledger.app.ui.components.WheelDatePickerDialog(
            initial = LocalDate.ofEpochDay(epochDay),
            onDismiss = { showPicker = false },
            onConfirm = {
                epochDay = it.toEpochDay()
                showPicker = false
            }
        )
    }

            InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增倒数日" else "编辑倒数日") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称，如：考研 / 生日 / 在一起") },
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { showPicker = true }
                ) {
                    Text(
                        LocalDate.ofEpochDay(epochDay)
                            .format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("  （点选日期）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("每年重复（生日 / 纪念日）", Modifier.weight(1f))
                    Switch(checked = repeat, onCheckedChange = { repeat = it })
                }
                Text("图标", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                EmojiPickerGrid(selected = emoji, onPick = { emoji = it })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    InkDialogButton("删除", onClick = onDelete, danger = true)
                }
                InkDialogButton("取消", onClick = onDismiss)
                InkDialogButton(
                    "保存",
                    primary = true,
                    onClick = {
                        if (name.isBlank()) return@InkDialogButton
                        onSave(
                            (initial ?: CountDownEntity(name = "", targetEpochDay = 0, createdAt = 0)).copy(
                                name = name.trim(), emoji = emoji, targetEpochDay = epochDay, repeatYearly = repeat
                            )
                        )
                    }
                )
            }
        },
        dismissButton = {}
    )
}

/** 目标达成庆祝页：彩带下落 + 大字 + 触感 */
@Composable
private fun CelebrationOverlay(
    name: String,
    amountMinor: Long,
    currency: String,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xE60F1317))) {
            val particles = remember {
                List(24) { i ->
                    val r = kotlin.random.Random(i)
                    Triple(r.nextFloat(), 0.4f + r.nextFloat(), listOf("🎉", "🎊", "✨", "💰")[i % 4])
                }
            }
            particles.forEachIndexed { i, (xF, speed, e) ->
                val infinite = rememberInfiniteTransition(label = "cf$i")
                val y by infinite.animateFloat(
                    initialValue = -0.08f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween((2600 + speed * 1800).toInt(), delayMillis = (xF * 900).toInt(), easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "cfY$i"
                )
                Text(
                    e,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = xF * size.width
                            translationY = y * size.height
                        }
                )
            }
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🎉", fontSize = 72.sp)
                Text(
                    "目标达成！",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "累计存入 ${formatMoney(amountMinor, currency)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.padding(top = 30.dp)
                ) { Text("  太棒了 🎊  ", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// 倒数日展示页背景 = 全局渐变色（与设置→渐变背景同款流行配色）
private val CD_BGS get() = com.inkledger.app.ui.theme.APP_GRADIENTS

/** 倒数日展示页：全屏渐变背景（可换预置）、超大天数、编辑入口 */
@Composable
fun CountdownDetailScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit
) {
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val c = countdowns.firstOrNull { it.id == id }
    if (c == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var editNow by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }  // 编辑模式才显示背景控制
    var shareMode by remember { mutableStateOf(false) }      // 导出模式：隐藏所有控件，纯展示
    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 自定义背景版本号：换图后 +1，驱动 produceState 重新加载（bgId 不变也会刷新）
    var cdBgVersion by remember { mutableStateOf(0) }
    // bgId == -1 表示用自定义照片（深模糊+压暗，文件名 cd_{id}.jpg）
    val customBmp by produceState<android.graphics.Bitmap?>(null, c.bgId, c.id, cdBgVersion) {
        if (c.bgId == -1) {
            value = withContext(Dispatchers.IO) {
                com.inkledger.app.data.media.BackgroundManager.customFile(context, "cd_${c.id}.jpg")
                    ?.let { com.inkledger.app.data.media.NotesMedia.decode(it, 1440) }
            }
        } else {
            value = null
        }
    }
    val cdPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingCropUri = uri
    }
    val today = LocalDate.now()
    var target = LocalDate.ofEpochDay(c.targetEpochDay)
    if (c.repeatYearly) {
        var guard = 0
        while (target.isBefore(today) && guard < 200) {
            target = target.plusYears(1); guard++
        }
    }
    val days = ChronoUnit.DAYS.between(today, target)
    val bg = CD_BGS[c.bgId.coerceIn(0, CD_BGS.size - 1)]

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bg))
            .clickable(enabled = shareMode) { shareMode = false }  // 导出模式点任意处退出
    ) {
        // 自定义照片背景层：原图高清 + 实时毛玻璃 + Ken Burns 缓动（缓慢呼吸缩放）
        if (c.bgId == -1) {
            customBmp?.let {
                // Ken Burns：20秒周期缓慢缩放 1.0→1.12→1.0 + 轻微平移
                val kb = rememberInfiniteTransition(label = "kenburns")
                val kbScale by kb.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(20000, easing = androidx.compose.animation.core.EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "kbScale"
                )
                val kbX by kb.animateFloat(
                    initialValue = -12f,
                    targetValue = 12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(16000, easing = androidx.compose.animation.core.EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "kbX"
                )
                val kbY by kb.animateFloat(
                    initialValue = 8f,
                    targetValue = -8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(24000, easing = androidx.compose.animation.core.EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "kbY"
                )
                androidx.compose.foundation.Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // ×1.05 基础余量：平移 ±12px 时边缘永不露底（底层渐变含紫色会闪边）
                            scaleX = kbScale * 1.05f
                            scaleY = kbScale * 1.05f
                            translationX = kbX
                            translationY = kbY
                        }
                        .then(
                            if (c.bgBlur > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                Modifier.graphicsLayer {
                                    renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                        c.bgBlur / 100f * 60f,
                                        c.bgBlur / 100f * 60f,
                                        android.graphics.Shader.TileMode.CLAMP
                                    ).asComposeRenderEffect()
                                }
                            } else Modifier
                        ),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f + 0.35f * (c.bgBlur / 100f))))
            }
        }
        if (!shareMode) {
            androidx.compose.material3.IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 6.dp)
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(c.emoji, fontSize = 64.sp)
            Text(
                if (days > 0) "距离 ${c.name}" else c.name,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                if (days >= 0) "还有" else "已过",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 26.dp)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${kotlin.math.abs(days)}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White
                )
                Text(
                    " 天",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            Text(
                target.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) +
                    if (c.repeatYearly) " · 每年重复" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 10.dp)
            )
            if (days == 0L) {
                Text("🎉 就是今天", fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (!shareMode) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showControls) {
                // 默认：编辑 + 导出纪念卡
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable { showControls = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Text(" 编辑", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable {
                            shareMode = true  // 进入纯展示模式（点任意处退出）
                            // 渲染纪念卡图片并保存到相册
                            val ctx = context
                            val cid = c.id
                            val cEmoji = c.emoji
                            val cName = c.name
                            val tEpoch = c.targetEpochDay
                            val rep = c.repeatYearly
                            val bgIdV = c.bgId
                            val bgB = c.bgBlur
                            Thread {
                                val ok = CountdownCardShot.save(
                                    ctx, cid, cEmoji, cName,
                                    java.time.LocalDate.ofEpochDay(tEpoch), rep, bgIdV, bgB
                                )
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        if (ok) "📸 纪念卡已保存到相册（Pictures/妙计）"
                                        else "保存失败，请重试",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }.start()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Text("📸 纪念卡", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                // 编辑模式：显示全部控制
                Text("背景", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                // 可滚动，自定义放最前面
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    item {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (c.bgId == -1) 2.dp else 0.dp,
                                color = if (c.bgId == -1) Color.White else Color.Transparent
                            ),
                            modifier = Modifier.clickable {
                                cdPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = "从相册选背景",
                                tint = Color.White,
                                modifier = Modifier.padding(7.dp).size(16.dp)
                            )
                        }
                    }
                    items(CD_BGS.size) { i ->
                        Box(
                            Modifier
                                .size(30.dp)
                                .border(
                                    width = if (i == c.bgId) 2.dp else 0.dp,
                                    color = if (i == c.bgId) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .background(Brush.verticalGradient(CD_BGS[i]), CircleShape)
                                .clickable { vm.saveCountdown(c.copy(bgId = i)) }
                        )
                    }
                }
                if (c.bgId == -1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp)
                    ) {
                        Text(
                            "模糊 ${c.bgBlur}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.width(52.dp)
                        )
                        androidx.compose.material3.Slider(
                            value = c.bgBlur.toFloat(),
                            onValueChange = { vm.saveCountdown(c.copy(bgBlur = it.toInt())) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable { editNow = true }
                    ) {
                        Text("  编辑信息  ", color = Color.White, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 9.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.clickable { showControls = false }
                    ) {
                        Text("  完成  ", color = Color.White, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 9.dp))
                    }
                }
            }
        }
        } // end if (!shareMode)
    }
    if (pendingCropUri != null) {
        com.inkledger.app.ui.components.ImageCropDialog(
            uri = pendingCropUri!!,
            onDismiss = { pendingCropUri = null },
            saveTo = { b ->
                val ok = com.inkledger.app.data.media.BackgroundManager.saveBitmap(
                    context, b, "cd_${c.id}.jpg"
                )
                if (ok) {
                    vm.saveCountdown(c.copy(bgId = -1))
                    cdBgVersion++   // 同名文件被覆盖，必须驱动重新加载
                }
                ok
            }
        )
    }
    if (editNow) {
        CountdownDialog(
            initial = c,
            onDismiss = { editNow = false },
            onSave = { vm.saveCountdown(it); editNow = false },
            onDelete = {
                vm.deleteCountdown(c)
                editNow = false
                onBack()
            }
        )
    }
}
