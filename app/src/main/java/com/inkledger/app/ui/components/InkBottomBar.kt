package com.inkledger.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.remember

/**
 * 悬浮胶囊玻璃底栏：明细 / 统计 / (＋记账) / 目标 / 记事
 * 半透明玻璃面板 + 高光描边，记录键是底栏的一部分，五槽位互不遮挡。
 * 中央键：点按 = 上下文新增；按住不放 = 立即开始语音（松手执行/挪开取消，QQ 式）。
 */
@Composable
fun InkBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
    pureMode: Boolean = false,
    onLongRecordStart: () -> Unit = {},
    onLongRecordEnd: () -> Unit = {},
    onLongRecordCancel: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val barShape = RoundedCornerShape(32.dp)

    // 关键：pointerInput(Unit) 永不重启，回调必须用 rememberUpdatedState 包装
    // 否则页面切换后 ＋ 键仍持有首次组合的旧回调（上下文新增/语音全部失效）
    val currentOnRecord by androidx.compose.runtime.rememberUpdatedState(onRecord)
    val currentOnLongStart by androidx.compose.runtime.rememberUpdatedState(onLongRecordStart)
    val currentOnLongEnd by androidx.compose.runtime.rememberUpdatedState(onLongRecordEnd)
    val currentOnLongCancel by androidx.compose.runtime.rememberUpdatedState(onLongRecordCancel)

    // ＋ 号键（明细/统计两布局共用，位置由所在槽位决定）
    val recordKey: @Composable () -> Unit = {
        var pressedState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (pressedState) 1.12f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "recordScale"
        )
        Box(
            Modifier
                .size(60.dp)
                .scale(scale)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressedState = true
                        var movedOut = false
                        // 长按等待：超时前抬起返回 null（普通点按）
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            pressedState = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnRecord()
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnLongStart()
                            // 按住期间持续追踪：松手=执行，挪出=取消
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) { change.consume(); break }
                                val pos = change.position
                                val out = pos.x < -40f || pos.y < -40f ||
                                    pos.x > size.width + 40f || pos.y > size.height + 40f
                                if (out && !movedOut) {
                                    movedOut = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    currentOnLongCancel()
                                }
                                change.consume()
                            }
                            pressedState = false
                            if (!movedOut) currentOnLongEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "记一笔（按住说话记账）",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clip(barShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.74f))
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)
                    )
                ),
                shape = barShape
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pureMode) {
            // 纯净三槽：明细 | ＋ | 统计
            BarItem("明细", Icons.AutoMirrored.Rounded.ReceiptLong, currentRoute == "home", Modifier.weight(1f)) { onSelect("home") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { recordKey() }
            BarItem("统计", Icons.Rounded.PieChart, currentRoute == "stats", Modifier.weight(1f)) { onSelect("stats") }
        } else {
            BarItem("明细", Icons.AutoMirrored.Rounded.ReceiptLong, currentRoute == "home", Modifier.weight(1f)) { onSelect("home") }
            BarItem("统计", Icons.Rounded.PieChart, currentRoute == "stats", Modifier.weight(1f)) { onSelect("stats") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { recordKey() }
            BarItem("目标", Icons.Rounded.Flag, currentRoute == "goals", Modifier.weight(1f)) { onSelect("goals") }
            BarItem("记事", Icons.AutoMirrored.Rounded.Notes, currentRoute == "notes", Modifier.weight(1f)) { onSelect("notes") }
        }
    }
}

/**
 * 独立的 AI 圆形玻璃按钮（可拖动悬浮球）：
 * - 拖动后自动吸附到左/右边缘，永远不挡正文中间
 * - 闲置时半透明（能透出底下内容），触摸/拖动时变实
 * - 位置在本次会话内记忆（AppRoot 里 fillMaxSize 容器内自由移动）
 */
@Composable
fun InkAiFab(
    onClick: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fabSize = 44.dp
    val margin = 10.dp

    var pos by remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null) }
    var containerSize by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var dragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fabAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging || pressed) 1f else 0.55f,
        animationSpec = tween(200),
        label = "fabAlpha"
    )

    if (!visible) {
        Box(modifier)
        return
    }
    Box(
        modifier
            .onGloballyPositioned { containerSize = it.size }
    ) {
        val containerW = containerSize.width
        val containerH = containerSize.height
        val fabPx = with(density) { fabSize.toPx() }.toInt()
        val marginPx = with(density) { margin.toPx() }.toInt()

        if (containerW > 0 && containerH > 0) {
            val offset = pos ?: androidx.compose.ui.unit.IntOffset(
                containerW - fabPx - marginPx,
                containerH - fabPx - marginPx - with(density) { 150.dp.toPx() }.toInt()
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                        )
                    )
                ),
                modifier = Modifier
                    .offset { offset }
                    .size(fabSize)
                    .graphicsLayer { alpha = fabAlpha }
                    .pointerInput(containerW, containerH) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                // 吸边：吸附到较近的左右边缘
                                val snappedX = if (offset.x + fabPx / 2 < containerW / 2) marginPx
                                else containerW - fabPx - marginPx
                                val clampedY = offset.y.coerceIn(marginPx, (containerH - fabPx - marginPx).coerceAtLeast(0))
                                pos = androidx.compose.ui.unit.IntOffset(snappedX, clampedY)
                            },
                            onDragCancel = { dragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            val nx = (offset.x + dragAmount.x).roundToInt()
                                .coerceIn(marginPx, (containerW - fabPx - marginPx).coerceAtLeast(0))
                            val ny = (offset.y + dragAmount.y).roundToInt()
                                .coerceIn(marginPx, (containerH - fabPx - marginPx).coerceAtLeast(0))
                            pos = androidx.compose.ui.unit.IntOffset(nx, ny)
                        }
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick
                    )
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = "AI 助手",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val (interaction, scaleMod) = rememberPressScale(0.88f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(scaleMod)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                onClick()
            }
            .padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
