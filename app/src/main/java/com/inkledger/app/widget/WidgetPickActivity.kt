package com.inkledger.app.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inkledger.app.AppViewModel
import com.inkledger.app.ui.components.PageHeader
import com.inkledger.app.ui.theme.InkTheme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 添加「倒数日 / 打卡」小组件时的系统配置页（android:configure）：
 * 选择组件显示哪一条；选「自动」= 置顶/最近（倒数日）、第一个（打卡）。
 * 选择按 appWidgetId 独立存储，多放几个组件可以各显各的。
 */
class WidgetPickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(RESULT_CANCELED)  // 用户返回键退出时不放置小组件

        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val isHabit = provider.endsWith("HabitWidgetReceiver")

        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            InkTheme(settings.theme, settings.chineseSeedIndex) {
                Surface(Modifier.fillMaxSize()) {
                    Column {
                        PageHeader(
                            title = if (isHabit) "选择打卡任务" else "选择倒数日",
                            onBack = { finish() }
                        )
                        if (isHabit) HabitPickList(vm, appWidgetId) else CountdownPickList(vm, appWidgetId)
                    }
                }
            }
        }
    }

    @Composable
    private fun CountdownPickList(vm: AppViewModel, appWidgetId: Int) {
        val countdowns by vm.countdowns.collectAsState()
        val today = LocalDate.now()
        fun target(c: com.inkledger.app.data.db.CountDownEntity): LocalDate {
            var t = LocalDate.ofEpochDay(c.targetEpochDay)
            if (c.repeatYearly) {
                var guard = 0
                while (t.isBefore(today) && guard < 200) { t = t.plusYears(1); guard++ }
            }
            return t
        }
        val sorted = countdowns.sortedWith(
            compareByDescending<com.inkledger.app.data.db.CountDownEntity> { it.pinned }
                .thenBy { target(it).toEpochDay() }
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                PickRow(
                    title = "自动（置顶 / 未来最近）",
                    subtitle = "跟随应用内的置顶与新增自动变化",
                    selected = true,
                    onClick = { pick { vm.setWidgetCountdownPick(appWidgetId, null) } }
                )
            }
            items(sorted.size, key = { sorted[it].id }) { i ->
                val c = sorted[i]
                val t = target(c)
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, t)
                PickRow(
                    title = (if (c.pinned) "📌 " else "") + c.name,
                    subtitle = t.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) +
                        when {
                            days == 0L -> " · 就是今天"
                            days > 0L -> " · 还有 $days 天"
                            else -> " · 已过 ${-days} 天"
                        },
                    selected = false,
                    onClick = { pick { vm.setWidgetCountdownPick(appWidgetId, c.id) } }
                )
            }
        }
    }

    @Composable
    private fun HabitPickList(vm: AppViewModel, appWidgetId: Int) {
        val goals by vm.goals.collectAsState()
        val allChecks by vm.allHabitChecks.collectAsState()
        val habits = goals.filter { it.kind == 1 }
        val todayEpoch = LocalDate.now().toEpochDay()
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                PickRow(
                    title = "自动（第一个任务）",
                    subtitle = "列表第一个打卡习惯",
                    selected = true,
                    onClick = { pick { vm.setWidgetHabitPick(appWidgetId, null) } }
                )
            }
            items(habits.size, key = { habits[it].id }) { i ->
                val g = habits[i]
                val checks = allChecks.filter { it.goalId == g.id }.map { it.epochDay }.toSet()
                var streak = 0
                var d = if (todayEpoch in checks) LocalDate.now() else LocalDate.now().minusDays(1)
                while (d.toEpochDay() in checks) { streak++; d = d.minusDays(1) }
                PickRow(
                    title = g.name,
                    subtitle = if (streak > 0) "🔥 连续 $streak 天" else "还没有连续记录",
                    selected = false,
                    onClick = { pick { vm.setWidgetHabitPick(appWidgetId, g.id) } }
                )
            }
        }
    }

    @Composable
    private fun PickRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    /** 保存选择 → 刷新组件 → 回系统完成放置 */
    private fun pick(save: suspend () -> Unit) {
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        lifecycleScope.launch {
            save()
            WidgetSync.refreshAsync(applicationContext)
            setResult(
                RESULT_OK,
                android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
}
