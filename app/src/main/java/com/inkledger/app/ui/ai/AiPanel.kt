package com.inkledger.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.ai.Insight
import com.inkledger.app.data.ai.LlmClient
import com.inkledger.app.ui.components.EmptyHint
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.nextMonthPeriod
import com.inkledger.app.util.toLocalDate
import java.time.LocalDate

/** AI 面板：本机分析（始终可用）+ 大模型问答（设置开启后），悬浮玻璃面板 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiPanel(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val txs by vm.txs.collectAsStateWithLifecycle()

    // 打开面板时检查周报生成
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.maybeGenerateWeeklyReport() }
    val categoryMap by vm.categoryMap.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val allDeposits by vm.allDeposits.collectAsStateWithLifecycle()
    val allChecks by vm.allHabitChecks.collectAsStateWithLifecycle()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()

    val startDay = settings.monthStartDay
    val today = LocalDate.now()
    val curPeriod = today.monthPeriodStart(startDay)
    val prevPeriod = curPeriod.minusMonths(1)

    val insights = remember(txs, goals, allDeposits, allChecks, countdowns, notes, settings, categoryMap) {
        val cur = txs.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == curPeriod }
            .map { com.inkledger.app.data.ai.TxLite(it.categoryId, it.amountMinor, it.type, it.timestamp.toLocalDate().toEpochDay()) }
        val prev = txs.filter { it.timestamp.toLocalDate().monthPeriodStart(startDay) == prevPeriod }
            .map { com.inkledger.app.data.ai.TxLite(it.categoryId, it.amountMinor, it.type, it.timestamp.toLocalDate().toEpochDay()) }
        val savings = goals.filter { it.kind == 0 }.map { g ->
            g to allDeposits.filter { it.goalId == g.id }.sumOf { it.amountMinor }
        }
        val streaks = goals.filter { it.kind == 1 }.map { g ->
            val set = allChecks.filter { it.goalId == g.id }.map { it.epochDay }.toSet()
            var st = 0
            var d = today
            if (today.toEpochDay() !in set) d = d.minusDays(1)
            while (d.toEpochDay() in set) { st++; d = d.minusDays(1) }
            g to st
        }
        val checkSets = goals.filter { it.kind == 1 }.map { g ->
            g to allChecks.filter { it.goalId == g.id }.map { it.epochDay }.toSet()
        }
        com.inkledger.app.data.ai.InsightEngine.generate(
            cur, prev, categoryMap, settings.budgetMinor, settings.currency,
            savings, streaks, checkSets, countdowns, notes,
            today, curPeriod, curPeriod.nextMonthPeriod(startDay)
        )
    }

    var chat by remember { mutableStateOf(listOf<Pair<Boolean, String>>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    val aiReady = settings.aiEnabled && settings.aiKey.isNotBlank()

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text("✨ AI 助手", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (aiReady) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        if (aiReady) "大模型已连接" else "本地分析",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (aiReady) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                // 周报只在周一显示，其他时间不占版面
                if (settings.weeklyReportText.isNotBlank() && java.time.LocalDate.now().dayOfWeek.value == 1) {
                    item {
                        com.inkledger.app.ui.components.InkCard(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("上周小结", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    settings.weeklyReportText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "本月分析",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                if (insights.isEmpty()) {
                    item { EmptyHint("🧠", "数据还不够", "多记几笔，这里会出现本月的智能分析") }
                }
                items(insights) { ins -> InsightCard(ins) }

                item {
                    Text(
                        "问答",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                }
                if (!aiReady) {
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "连接大模型解锁问答",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "设置 → AI 助手 → 填入 API Key（支持 智谱GLM / DeepSeek / 自定义 OpenAI 兼容接口）。未开启时 App 零联网；开启后只发送汇总统计与提问，不包含任何账单明细，Key 仅存本机。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                                com.inkledger.app.ui.components.InkDialogButton(
                                    "去设置",
                                    primary = true,
                                    onClick = onOpenSettings
                                )
                            }
                        }
                    }
                } else {
                    if (chat.isEmpty()) {
                        item {
                            Text(
                                "试试问：这个月餐饮花了多少？ / 帮我看看哪里能省一点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                    items(chat.size) { i ->
                        ChatBubble(chat[i].first, chat[i].second)
                    }
                    if (busy) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "  墨小账思考中…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (aiReady) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("问点什么…") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = !busy && input.isNotBlank(),
                        onClick = {
                            val q = input.trim()
                            if (q.isEmpty()) return@IconButton
                            input = ""
                            chat = chat + (true to q)
                            busy = true
                            val history = chat.dropLast(1).map { (isUser, t) ->
                                LlmClient.Msg(if (isUser) "user" else "assistant", t)
                            }
                            vm.askAi(q, history) { _, reply ->
                                chat = chat + (false to reply)
                                busy = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发送",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun InsightCard(ins: Insight) {
    InkCard(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(ins.emoji, fontSize = 22.sp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(ins.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    ins.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(isUser: Boolean, text: String) {
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
            )
        }
    }
}
