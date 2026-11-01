package com.inkledger.app.ui.quickentry

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.nl.NlParser
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.nowMillis
import java.time.LocalDate

/**
 * 语音页专用路由（不影响面板）：
 * 先拦「距离/还有 N 天」与「倒数/倒计时」——其中的数字是天数不是金额。
 */
fun routeVoicePage(text: String): VoiceRoute {
    val t = text.trim()
    val dayMatch = Regex("""(?:还有?|距离)\s*([0-9一二三四五六七八九十百千万两]+)\s*天""").find(t)
    if (dayMatch != null || t.contains("倒数") || t.contains("倒计时")) {
        val days = dayMatch?.groupValues?.get(1)?.let { parseDayNumber(it) }
        val name = t.replace(Regex("""倒数日|倒数|倒计时|距离|还有|还|[0-9一二三四五六七八九十百千万两]+|天"""), "")
            .trim(' ', '，', '。', '的', '就', '要')
        return VoiceRoute.Countdown(name.ifBlank { "倒数日" }, days)
    }
    val primary = routeVoice(t)
    // 全场景兜底：NlParser 不认裸中文数字（「晚饭二十五」），把它们转成阿拉伯数字重试
    // 只在路由结果是 记事/没听清 时才重试——存钱/打卡等关键词意图优先级不变
    if (primary is VoiceRoute.Note || primary is VoiceRoute.Unknown) {
        val normalized = normalizeCjkNums(t)
        if (normalized != t) {
            NlParser.parse(normalized)?.let { return VoiceRoute.Tx(it) }
        }
    }
    return primary
}

/** 中文数字串 → 金额（一百五=150、一千五=1500、二十五=25、两百=200） */
private fun parseDayNumber(s: String): Long? {
    if (s.all { it.isDigit() }) return s.toLongOrNull()
    if (s.any { it !in "零一二两三四五六七八九十百千万" }) return null
    val digit = mapOf('零' to 0L, '一' to 1L, '二' to 2L, '两' to 2L, '三' to 3L, '四' to 4L,
        '五' to 5L, '六' to 6L, '七' to 7L, '八' to 8L, '九' to 9L)
    var total = 0L; var section = 0L; var cur = 0L; var lastUnit = 0L; var lastWasDigit = false
    for (ch in s) {
        when {
            digit.containsKey(ch) -> {
                // 连续数字拼接：二五→25；跟在单位后则是新数位：一百二→尾缀按规则处理
                cur = if (lastWasDigit) cur * 10 + digit[ch]!! else digit[ch]!!
                lastWasDigit = true
            }
            ch == '十' -> { section += (if (cur == 0L) 1L else cur) * 10; cur = 0; lastUnit = 10; lastWasDigit = false }
            ch == '百' -> { section += (if (cur == 0L) 1L else cur) * 100; cur = 0; lastUnit = 100; lastWasDigit = false }
            ch == '千' -> { section += (if (cur == 0L) 1L else cur) * 1000; cur = 0; lastUnit = 1000; lastWasDigit = false }
            ch == '万' -> { total = (total + section + cur) * 10000; section = 0; cur = 0; lastUnit = 10000; lastWasDigit = false }
        }
    }
    // 口语尾缀：一百五=150、一千五=1500（百/千后的裸数字 ×10）
    if (cur > 0 && (lastUnit == 100L || lastUnit == 1000L)) cur *= lastUnit / 10
    val r = total + section + cur
    return if (r > 0) r else null
}

/**
 * 把文本里的裸中文数字串转成阿拉伯数字：
 * 「晚饭二十五」→「晚饭25」。单字且无十/百/千/万的不转（周五、五月不误伤），
 * 后面跟量词（个/人/年/月/日…）的也不转（「五十个人」不是钱）。
 */
private fun normalizeCjkNums(text: String): String {
    val run = Regex("""[零一二两三四五六七八九十百千万]+(?![个年月日号岁次遍层米斤页])""")
    return run.replace(text) { m ->
        val v = m.value
        val convertible = v.length >= 2 || v.any { it in "十百千万" }
        val n = if (convertible) parseDayNumber(v) else null
        if (n != null && n > 0) n.toString() else v
    }
}

/**
 * 长按＋的语音记账（隐形入口，无任何自绘界面）：
 * 触发后直接拉起系统语音识别页（真机验证的唯一可靠路径），
 * 说完自动路由创建：记账 / 存钱目标 / 打卡 / 倒数日 / 记事。
 * 与收支面板完全无关（面板代码零改动）。
 */
@Composable
fun VoiceOnlyOverlay(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var retried by remember { mutableStateOf(false) }
    var relaunchTick by remember { mutableStateOf(0) }

    fun handleResult(text: String) {
        val route = routeVoicePage(text)
        when (route) {
            is VoiceRoute.Tx -> {
                val p = route.parsed
                vm.addTx(p.amountMinor, if (p.isIncome) 1 else 0, p.categoryId ?: 13L, p.note,
                    nowMillis() - p.dayOffset * 86_400_000L, source = 1)
                onSuccess("已记 ${formatMoney(p.amountMinor, vm.settings.value.currency)} · ${p.note}")
            }
            is VoiceRoute.SavingsGoal -> {
                vm.saveGoal(com.inkledger.app.data.db.GoalEntity(
                    kind = 0, name = route.name.take(20), emoji = "💰"))
                onSuccess("已创建存钱目标「${route.name.take(12)}」")
            }
            is VoiceRoute.Habit -> {
                vm.saveGoal(com.inkledger.app.data.db.GoalEntity(
                    kind = 1, name = route.name.take(20), emoji = "💪"))
                onSuccess("已创建打卡习惯「${route.name.take(12)}」")
            }
            is VoiceRoute.Countdown -> {
                val target = route.daysHint?.let { LocalDate.now().plusDays(it) } ?: LocalDate.now().plusDays(30)
                vm.saveCountdown(com.inkledger.app.data.db.CountDownEntity(
                    name = route.name.take(20), emoji = "⏳", targetEpochDay = target.toEpochDay()))
                onSuccess("已创建倒数日「${route.name.take(12)}」")
            }
            is VoiceRoute.Note -> {
                vm.saveNote(com.inkledger.app.data.db.NoteEntity(
                    title = route.text.take(16), body = route.text,
                    updatedAt = nowMillis(), createdAt = nowMillis()))
                onSuccess("已记事「${route.text.take(10)}…」")
            }
            is VoiceRoute.Unknown -> onMessage("没听清，再试一次")
        }
        onDismiss()
    }

    val systemVoice = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            android.util.Log.d("InkVoice", "system result: $text")
            if (!text.isNullOrBlank()) handleResult(text)
            else if (retried) {
                onMessage("没听清，长按＋再试一次")
                onDismiss()
            } else {
                // 听不清自动重试一次，不用再长按
                retried = true
                relaunchTick++
            }
        }
        onDismiss()   // 成功/失败/取消都收尾
    }

    fun startSystemVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句，如：早饭三块五 / 存钱买相机 / 倒数考研")
            // 静音自动断句：说完约 1 秒自动结束，不等手动点「说完了」
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        try {
            systemVoice.launch(intent)
        } catch (e: Exception) {
            onMessage("此设备没有可用的语音识别服务")
            onDismiss()
        }
    }

    LaunchedEffect(relaunchTick) {
        if (relaunchTick > 0) startSystemVoice()
    }

    // 无需等权限：启动时已全局申请过；没授权直接提示（系统识别页也会向系统要权限）
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startSystemVoice()
        else {
            onMessage("请先允许麦克风权限（应用启动时申请过），再长按＋")
            onDismiss()
        }
    }

    // 隐形占位：无任何可见界面
    Box(Modifier.fillMaxSize())
}
