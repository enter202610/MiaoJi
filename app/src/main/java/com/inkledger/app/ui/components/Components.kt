package com.inkledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.roundToInt
import com.inkledger.app.ui.theme.LocalInkExtras

/** 按压缩放：给已有 clickable 的卡片/按钮加"按下去"的物理反馈（配合同一 interactionSource 使用） */
@Composable
fun rememberPressScale(
    pressedScale: Float = 0.97f
): Pair<MutableInteractionSource, Modifier> {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f),
        label = "pressScale"
    )
    return interaction to Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * 提交成功动画（浮动式，不挡操作）：
 * 圆形带弹性放大 → 白色对勾描边画出 → 停顿 → 整体淡出。
 * 无遮罩、不拦截点击——动画期间照常操作其他区域。
 */
@Composable
fun SuccessOverlay(
    text: String,
    onDone: () -> Unit
) {
    val circleScale = androidx.compose.runtime.remember { Animatable(0.4f) }
    val checkProgress = androidx.compose.runtime.remember { Animatable(0f) }
    val fadeAlpha = androidx.compose.runtime.remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { circleScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        delay(140)
        checkProgress.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        delay(500)
        fadeAlpha.animateTo(0f, tween(180))
        onDone()
    }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = fadeAlpha.value }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .graphicsLayer {
                        scaleX = circleScale.value
                        scaleY = circleScale.value
                    }
                    .shadow(12.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
                    // 对勾描边：按进度画出
                    val w = size.width
                    val h = size.height
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.22f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.72f)
                        lineTo(w * 0.80f, h * 0.30f)
                    }
                    val measure = androidx.compose.ui.graphics.PathMeasure().apply {
                        setPath(path, false)
                    }
                    val partial = androidx.compose.ui.graphics.Path()
                    measure.getSegment(0f, measure.length * checkProgress.value, partial, true)
                    drawPath(
                        partial,
                        Color.White,
                        style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** 左右横滑手势：左滑=下一个，右滑=上一个（rememberUpdatedState 保证回调永远最新，修复滑动失灵） */
@Composable
fun Modifier.inkHorizontalSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
): Modifier {
    val left by androidx.compose.runtime.rememberUpdatedState(onSwipeLeft)
    val right by androidx.compose.runtime.rememberUpdatedState(onSwipeRight)
    return this.pointerInput(Unit) {
        val threshold = 100.dp.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, amount ->
                total += amount
                change.consume()
            },
            onDragEnd = {
                if (total < -threshold) left()
                else if (total > threshold) right()
                total = 0f
            },
            onDragCancel = { total = 0f }
        )
    }
}

/** 大触控区按钮（替换弹窗里的纯文字 TextButton）：≥46dp 高的圆角药丸 */
@Composable
fun InkDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false
) {
    val bg = when {
        danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        danger -> MaterialTheme.colorScheme.error
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = modifier
            .heightIn(min = 46.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(text, color = fg, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** 左滑删除容器：露出红色删除图标，滑过阈值触发 onDelete，松手弹回 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val anim = remember { Animatable(0f) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // 关键：pointerInput(Unit) 不会随重组重启，必须用 rememberUpdatedState
    // 否则列表重排/删除后滑到的位置仍触发旧条目的回调（删除到错误的条目）
    val currentOnDelete by androidx.compose.runtime.rememberUpdatedState(onDelete)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 背景删除图标：只在滑动时露出（平时完全透明）
        Box(
            Modifier
                .matchParentSize()
                .background(
                    if (anim.value < -10f) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    else Color.Transparent,
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "左滑删除",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(end = 22.dp)
                    .graphicsLayer { alpha = if (anim.value < -10f) 1f else 0f }
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(anim.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    val limit = 140.dp.toPx()
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            if (amount < 0) {
                                scope.launch { anim.snapTo((anim.value + amount).coerceIn(-limit, 0f)) }
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            if (anim.value < -limit * 0.55f) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                currentOnDelete()
                            }
                            scope.launch {
                                anim.animateTo(
                                    0f,
                                    spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/** 大圆角卡片（玻璃质感：92% 不透明表面，设置自定义背景后透出底色） */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 1.0f)  // 完全不透明，背景图不透进来
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun EmptyHint(emoji: String, title: String, hint: String = "", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 40.sp)
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 月份切换器：‹ 本月 › */
@Composable
fun MonthSwitcher(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = true,
    onBackToCurrent: (() -> Unit)? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一周期")
        }
        // 非当前周期时点标题也能一键跳回当前
        val titleModifier = if (!isCurrent && onBackToCurrent != null) {
            Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .clickable { onBackToCurrent() }
                .padding(horizontal = 4.dp)
        } else Modifier.padding(horizontal = 4.dp)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (!isCurrent && onBackToCurrent != null) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.Unspecified,
            modifier = titleModifier
                .weight(1f, fill = false)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一周期")
        }
        // 非当前周期时出现「今」徽章：一键跳回当前周期（紧凑不挤日期）
        if (!isCurrent && onBackToCurrent != null) {
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable { onBackToCurrent() }
            ) {
                Text(
                    "今",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** 占比环形图（Canvas 自绘） */
@Composable
fun RingChart(
    segments: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    center: (@Composable () -> Unit)? = null
) {
    val track = LocalInkExtras.current.track
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2 + 1.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            var start = -90f
            segments.forEach { (color, fraction) ->
                if (fraction > 0f) {
                    val sweep = 360f * fraction
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = (sweep - 2f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    start += sweep
                }
            }
        }
        center?.invoke()
    }
}

/** 横向分类占比条目 */
@Composable
fun RankBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val track = LocalInkExtras.current.track
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(track, RoundedCornerShape(3.dp))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
    }
}

/** 小圆点 */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
    )
}

/** 可点击的设置行（iOS 分组列表风：≥52dp 行高，可点击行尾随 ›） */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

/** 统一的玻璃质感弹窗：28dp 大圆角 + 半透明表面 */
@Composable
fun InkAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable () -> Unit = {},
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        modifier = modifier,
        title = title,
        text = text,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp
    )
}

/** 二级页面统一页头：状态栏留白 + 返回键（可选）+ 标题 + 右侧动作 */
@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = if (onBack != null) 4.dp else 20.dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(start = if (onBack != null) 0.dp else 0.dp)
                .weight(1f)
        )
        actions?.invoke()
    }
}
