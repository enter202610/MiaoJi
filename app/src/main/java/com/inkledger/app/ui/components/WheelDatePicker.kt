package com.inkledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

/**
 * iOS 滚轮式日期选择器（三列年/月/日，带居中指示条与上下渐隐）。
 * 自绘实现，规避系统 DatePicker 兼容性问题。
 */
@Composable
fun WheelDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val years = (initial.year - 10..initial.year + 40).toList()
    val yearInit = years.indexOf(initial.year).coerceAtLeast(0)
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }

    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    if (day > daysInMonth) day = daysInMonth

    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择日期") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                WheelColumn(
                    items = years.map { "${it}年" },
                    initialIndex = yearInit,
                    modifier = Modifier.weight(1.1f)
                ) { idx -> year = years[idx] }
                WheelColumn(
                    items = (1..12).map { "${it}月" },
                    initialIndex = month - 1,
                    modifier = Modifier.weight(1f)
                ) { idx -> month = idx + 1 }
                WheelColumn(
                    items = (1..daysInMonth).map { "${it}日" },
                    initialIndex = day - 1,
                    key = "$year-$month",
                    modifier = Modifier.weight(1f)
                ) { idx -> day = idx + 1 }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkDialogButton("取消", onClick = onDismiss)
                InkDialogButton(
                    "确定",
                    primary = true,
                    onClick = {
                        val safeDays = YearMonth.of(year, month).lengthOfMonth()
                        onConfirm(LocalDate.of(year, month, day.coerceAtMost(safeDays)))
                    }
                )
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    key: String? = null,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    key(key ?: items.hashCode()) {
        val selected by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(Unit) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect { onSelect(it.coerceIn(0, items.size - 1)) }
        }
        Box(
            modifier = modifier.height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            val surface = MaterialTheme.colorScheme.surface
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 68.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items.size) { i ->
                    Text(
                        items[i],
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                }
            }
            // 居中指示条
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = (-22).dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = 22.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            )
            // 上下渐隐遮罩
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(surface, Color.Transparent)))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, surface)))
            )
        }
    }
}
