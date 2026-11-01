package com.inkledger.app.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.TemplateEntity
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkAlertDialog
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.theme.AmountBodyStyle
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.keypadToMinor

/** 模板管理页（MoneyThings 式 One Touch 模板库） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit
) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    val categoryMap by vm.categoryMap.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var edit by remember { mutableStateOf<TemplateEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TemplateEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            com.inkledger.app.ui.components.PageHeader(
                title = "模板",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showNew = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建模板")
                    }
                }
            )
        }
        item {
            Text(
                "点一下模板 = 直接记一笔；常用高频消费最适合（早饭、通勤…）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        if (templates.isEmpty()) {
            item { EmptyHint("⭐", "还没有模板", "记账面板里「⭐ 存为模板」，或点右上角 ＋ 新建") }
        }
        items(templates, key = { it.id }) { t ->
            val cat = categoryMap[t.categoryId]
            val (rowInteraction, rowScale) = com.inkledger.app.ui.components.rememberPressScale(0.97f)
            com.inkledger.app.ui.components.SwipeToDeleteBox(
                onDelete = { deleteTarget = t }
            ) {
                InkCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .then(rowScale)
                            .clickable(
                                interactionSource = rowInteraction,
                                indication = null
                            ) {
                                vm.applyTemplate(t)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMessage("已记 ${formatMoney(t.amountMinor, settings.currency)} · ${t.name}")
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(t.emoji, fontSize = 18.sp) }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${cat?.name ?: ""}${if (t.type == 1) " · 收入" else ""} · 用过 ${t.useCount} 次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatMoney(t.amountMinor, settings.currency), style = AmountBodyStyle)
                        IconButton(onClick = { edit = t }) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(96.dp)) }
    }

    deleteTarget?.let { t ->
        InkAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除模板「${t.name}」？") },
            text = { Text("只删模板，不影响已记的账单") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.inkledger.app.ui.components.InkDialogButton("取消", onClick = { deleteTarget = null })
                    com.inkledger.app.ui.components.InkDialogButton(
                        "删除",
                        danger = true,
                        onClick = {
                            vm.deleteTemplate(t)
                            deleteTarget = null
                            onMessage("已删除模板「${t.name}」")
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }

    if (showNew) {
        TemplateDialog(
            vm = vm,
            initial = null,
            onDismiss = { showNew = false },
            onSave = { vm.saveTemplate(it); showNew = false }
        )
    }
    edit?.let { t ->
        TemplateDialog(
            vm = vm,
            initial = t,
            onDismiss = { edit = null },
            onSave = { vm.saveTemplate(it); edit = null }
            // 删除走列表左滑，弹窗里不再放删除按钮
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateDialog(
    vm: AppViewModel,
    initial: TemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (TemplateEntity) -> Unit
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var type by remember { mutableIntStateOf(initial?.type ?: 0) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: 1L) }
    var amount by remember {
        mutableStateOf(
            initial?.let { t ->
                if (t.amountMinor % 100 == 0L) "${t.amountMinor / 100}"
                else String.format(java.util.Locale.US, "%.2f", t.amountMinor / 100.0)
            } ?: ""
        )
    }
    var name by remember { mutableStateOf(initial?.name ?: "") }

    val cats = categories.filter { it.type == type }
    if (cats.none { it.id == categoryId }) categoryId = cats.firstOrNull()?.id ?: 1L

    InkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建模板" else "编辑模板") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "支出", 1 to "收入").forEach { (t, label) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (type == t) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { type = t }
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (type == t) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { s -> amount = s.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 10.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称，默认用分类名") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text("分类", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                // 紧凑 chips + 限高滚动：分类多时不再撑爆弹窗
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    cats.forEach { c ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (c.id == categoryId) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { categoryId = c.id }
                        ) {
                            Text(
                                "${c.emoji} ${c.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (c.id == categoryId) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.inkledger.app.ui.components.InkDialogButton("取消", onClick = onDismiss)
                com.inkledger.app.ui.components.InkDialogButton(
                    "保存",
                    primary = true,
                    onClick = {
                        val minor = keypadToMinor(amount) ?: return@InkDialogButton
                        val cat = cats.firstOrNull { it.id == categoryId } ?: return@InkDialogButton
                        onSave(
                            (initial ?: TemplateEntity(
                                name = "", emoji = "⭐", categoryId = 1, type = 0,
                                amountMinor = 0, createdAt = 0
                            )).copy(
                                name = name.ifBlank { cat.name },
                                emoji = cat.emoji,
                                categoryId = categoryId,
                                type = type,
                                amountMinor = minor
                            )
                        )
                    }
                )
            }
        },
        dismissButton = {}
    )
}
