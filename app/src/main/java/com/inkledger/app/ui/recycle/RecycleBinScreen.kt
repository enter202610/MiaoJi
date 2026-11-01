package com.inkledger.app.ui.recycle

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkAlertDialog
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.PageHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TYPE_LABEL = mapOf("tx" to "账单", "goal" to "目标", "countdown" to "倒数日", "note" to "记事")

/** 回收站：7 天内误删内容可恢复，过期自动清理 */
@Composable
fun RecycleBinScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val items by vm.binItems.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<com.inkledger.app.data.db.RecycleBinEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("M月d日 HH:mm", java.util.Locale.CHINA)

    // 打开时清理过期（>7 天）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.purgeExpiredBinItems()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            PageHeader(title = "回收站", onBack = onBack,
                actions = {
                    if (items.isNotEmpty()) {
                        Text(
                            "清空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { confirmClear = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                })
        }
        item {
            Text(
                "误删的内容在这里保留 7 天，可随时恢复；过期自动清理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        if (items.isEmpty()) {
            item { EmptyHint("🗑", "回收站是空的", "删除的账单、目标、倒数日、记事会先到这里") }
        }
        items(items, key = { it.binId }) { e ->
            val whenDeleted = Instant.ofEpochMilli(e.deletedAt).atZone(ZoneId.systemDefault()).format(fmt)
            val daysLeft = 7 - ((System.currentTimeMillis() - e.deletedAt) / (24 * 3600 * 1000)).toInt()
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(TYPE_LABEL[e.type] ?: "条目", fontSize = 13.sp) }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(e.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$whenDeleted 删除" + if (daysLeft > 0) " · 剩 $daysLeft 天" else " · 即将清理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (e.detail.isNotBlank()) {
                            Text(
                                e.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.restoreFromBin(e)
                        onMessage("已恢复「${e.label}」")
                    }) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = "恢复",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { confirmDelete = e }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.DeleteForever,
                            contentDescription = "彻底删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    confirmDelete?.let { e ->
        InkAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("彻底删除？") },
            text = { Text("「${e.label}」将被永久删除，无法再恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.inkledger.app.ui.components.InkDialogButton("取消", onClick = { confirmDelete = null })
                    com.inkledger.app.ui.components.InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            vm.deleteFromBinForever(e)
                            confirmDelete = null
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }

    if (confirmClear) {
        InkAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空回收站？") },
            text = { Text("全部 ${items.size} 条记录将被永久删除，无法恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.inkledger.app.ui.components.InkDialogButton("取消", onClick = { confirmClear = false })
                    com.inkledger.app.ui.components.InkDialogButton(
                        "清空",
                        danger = true,
                        onClick = {
                            vm.clearBin()
                            confirmClear = false
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
}
