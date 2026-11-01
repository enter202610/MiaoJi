package com.inkledger.app.ui.quickentry

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.db.CategoryEntity
import com.inkledger.app.data.db.TemplateEntity
import com.inkledger.app.data.db.TransactionEntity
import com.inkledger.app.data.nl.NlParser
import com.inkledger.app.data.nl.ParsedEntry
import com.inkledger.app.ui.theme.AmountLargeStyle
import com.inkledger.app.ui.theme.LocalInkExtras
import com.inkledger.app.util.formatKeypadDisplay
import com.inkledger.app.util.formatMoney
import com.inkledger.app.util.keypadToMinor
import com.inkledger.app.util.nowMillis
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

/** 记一笔的触发请求：编辑已有交易 / 预解析的一句话 */
data class QuickEntryRequest(
    val editTx: TransactionEntity? = null,
    val prefill: ParsedEntry? = null
)

private fun minorToInput(minor: Long): String =
    if (minor % 100 == 0L) (minor / 100).toString()
    else String.format(Locale.US, "%.2f", minor / 100.0)

/**
 * 鲨鱼式快速记账面板：分类宫格 + 自定义数字键盘，3 秒记完一笔。
 * 支持：一句话（文本/语音）智能预填、昨天/前天、存为模板、编辑已有交易。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    vm: AppViewModel,
    request: QuickEntryRequest,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onSuccess: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val edit = request.editTx
    val prefill = request.prefill

    var type by remember { mutableIntStateOf(edit?.type ?: if (prefill?.isIncome == true) 1 else 0) }
    var categoryId by remember {
        mutableStateOf(edit?.categoryId ?: prefill?.categoryId ?: 1L)
    }
    var amountInput by remember {
        mutableStateOf(
            edit?.let { minorToInput(it.amountMinor) }
                ?: prefill?.let { minorToInput(it.amountMinor) }
                ?: ""
        )
    }
    var note by remember { mutableStateOf(edit?.note ?: prefill?.note ?: "") }
    var dayOffset by remember { mutableIntStateOf(if (edit != null) 0 else (prefill?.dayOffset ?: 0)) }
    var nlMode by remember { mutableStateOf(false) }
    var nlText by remember { mutableStateOf("") }
    var nlError by remember { mutableStateOf(false) }
    var voiceWaiting by remember { mutableStateOf(false) }
    var inAppVoiceFailed by remember { mutableStateOf(false) }  // 应用内识别失败后本会话内直走系统识别
    var voiceRelaunchTick by remember { mutableStateOf(0) }     // >0 表示需要转系统识别

    val cats = categories.filter { it.type == type }
    LaunchedEffect(type, categories) {
        if (cats.none { it.id == categoryId }) categoryId = cats.firstOrNull()?.id ?: 1L
    }

    fun applyParsed(p: ParsedEntry) {
        amountInput = minorToInput(p.amountMinor)
        type = if (p.isIncome) 1 else 0
        p.categoryId?.let { categoryId = it }
        note = p.note
        dayOffset = p.dayOffset
        nlMode = false
        nlText = ""
        nlError = false
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val context = LocalContext.current

    /** 应用内语音识别（自绘界面，不再弹系统丑页面） */
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val session = remember { VoiceSession(context) }
    DisposableEffect(Unit) { onDispose { session.stop() } }

    fun handleVoiceText(text: String) {
        val route = routeVoice(text)
        when (route) {
            is VoiceRoute.Tx -> {
                val p = route.parsed
                applyParsed(p)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.addTx(
                    p.amountMinor,
                    if (p.isIncome) 1 else 0,
                    p.categoryId ?: 13L,
                    p.note,
                    nowMillis() - p.dayOffset * 86_400_000L,
                    source = 1
                )
                onMessage("🎙 已记 ${formatMoney(p.amountMinor, settings.currency)} · ${p.note}")
                close()
            }
            is VoiceRoute.SavingsGoal -> {
                vm.saveGoal(
                    com.inkledger.app.data.db.GoalEntity(
                        kind = 0,
                        name = route.name,
                        emoji = "💰"
                    )
                )
                onMessage("🎙 已创建存钱目标「${route.name}」，去目标页设置金额")
                close()
            }
            is VoiceRoute.Habit -> {
                vm.saveGoal(
                    com.inkledger.app.data.db.GoalEntity(
                        kind = 1,
                        name = route.name,
                        emoji = "💪"
                    )
                )
                onMessage("🎙 已创建打卡习惯「${route.name}」")
                close()
            }
            is VoiceRoute.Countdown -> {
                val targetDay = route.daysHint?.let { LocalDate.now().plusDays(it) }
                    ?: LocalDate.now().plusDays(30)
                vm.saveCountdown(
                    com.inkledger.app.data.db.CountDownEntity(
                        name = route.name,
                        emoji = "⏳",
                        targetEpochDay = targetDay.toEpochDay()
                    )
                )
                onMessage("🎙 已创建倒数日「${route.name}」")
                close()
            }
            is VoiceRoute.Note -> {
                vm.saveNote(
                    com.inkledger.app.data.db.NoteEntity(
                        title = route.text.take(16),
                        body = route.text,
                        updatedAt = nowMillis(),
                        createdAt = nowMillis()
                    )
                )
                onMessage("🎙 已记事「${route.text.take(10)}…」")
                close()
            }
            is VoiceRoute.Unknown -> {
                nlMode = true
                nlText = text
                nlError = true
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        voiceWaiting = false
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) handleVoiceText(text)
            else onMessage("没听清，再点一次麦克风")
        } else {
            onMessage("已取消语音")
        }
    }

    fun startInAppVoice() {
        listening = true
        partial = ""
        session.start(
            onPartial = { partial = it },
            onFinal = { res ->
                listening = false
                if (!res.isNullOrBlank()) handleVoiceText(res)
                else if (!inAppVoiceFailed) {
                    // 应用内识别失败（绑定不上/空结果）：静默记住并自动转系统识别页（不弹提示）
                    inAppVoiceFailed = true
                    voiceRelaunchTick++
                }
                // 已转过系统页再失败：系统页自己有重试按钮，不再弹提示
            }
        )
    }

    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startInAppVoice()
        else onMessage("需要麦克风权限才能语音记账；也可用「一句话」输入")
    }

    fun startVoice() {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context) && !inAppVoiceFailed) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startInAppVoice()
            } else {
                micPerm.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        // 极少数无识别服务的设备：回退系统识别页
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句，如：午饭32")
        }
        try {
            voiceWaiting = true
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            voiceWaiting = false
            onMessage("此设备未安装语音识别服务，可改用「一句话」输入")
        }
    }

    // 应用内识别失败 → 自动转系统识别页（一次性信号触发）
    LaunchedEffect(voiceRelaunchTick) {
        if (voiceRelaunchTick > 0) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说一句，如：午饭32")
            }
            try {
                voiceWaiting = true
                speechLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                voiceWaiting = false
                onMessage("此设备未安装语音识别服务，可改用「一句话」输入")
            }
        }
    }

    val minor = keypadToMinor(amountInput)
    val currency = settings.currency
    val catName = cats.firstOrNull { it.id == categoryId }?.name ?: ""

    fun doSave() {
        val m = keypadToMinor(amountInput) ?: run {
            onMessage("先输入金额再保存"); return
        }
        val ts = edit?.timestamp ?: (nowMillis() - dayOffset * 86_400_000L)
        if (edit != null) {
            vm.updateTx(
                edit.copy(amountMinor = m, type = type, categoryId = categoryId, note = note, timestamp = ts)
            )
        } else {
            vm.addTx(
                m, type, categoryId, note, ts,
                source = if (prefill != null) 1 else 0
            )
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val msg = if (edit != null) "已更新 ${formatMoney(m, currency)}" else "已记 ${formatMoney(m, currency)} · $catName"
        close()
        onSuccess(msg)
    }

    fun saveAsTemplate() {
        val m = keypadToMinor(amountInput) ?: run { onMessage("先输入金额"); return }
        val cat = cats.firstOrNull { it.id == categoryId }
        vm.saveTemplate(
            TemplateEntity(
                name = cat?.name ?: "常用",
                emoji = cat?.emoji ?: "⭐",
                categoryId = categoryId,
                type = type,
                amountMinor = m,
                note = note
            )
        )
        onMessage("已存为模板，首页可一键记账")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
        dragHandle = null
    ) {
        // 面板尽量占满（92%），去掉顶部把手空白，内容完整显示
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxH)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .imePadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 支出 / 收入 切换
            SegmentedType(type = type, onSelect = { type = it })
            Spacer(Modifier.height(16.dp))

            // 金额 + 日期
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (amountInput.isEmpty()) "$currency 0" else "$currency $amountInput",
                        style = AmountLargeStyle,
                        color = if (type == 0) LocalInkExtras.current.expense
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateChip("今天", dayOffset == 0) { dayOffset = 0 }
                    DateChip("昨天", dayOffset == 1) { dayOffset = 1 }
                    DateChip("前天", dayOffset == 2) { dayOffset = 2 }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 分类宫格
            val rows = ceil(cats.size / 6f).toInt().coerceAtLeast(1)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .height((rows * 58).dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false
            ) {
                items(cats, key = { it.id }) { c ->
                    CategoryChip(
                        cat = c,
                        selected = c.id == categoryId,
                        onClick = {
                            categoryId = c.id
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // 备注 / 一句话
            if (nlMode) {
                OutlinedTextField(
                    value = nlText,
                    onValueChange = { nlText = it; nlError = false },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("试试：午饭32 / 昨天打车15.5 / 工资8000") },
                    isError = nlError,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        Row {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = "语音",
                                modifier = Modifier
                                    .clickable { startVoice() }
                                    .padding(6.dp)
                            )
                            Icon(
                                Icons.Rounded.Keyboard,
                                contentDescription = "返回普通备注",
                                modifier = Modifier
                                    .clickable { nlMode = false }
                                    .padding(6.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions.Default
                )
                Button(
                    onClick = {
                        val p = NlParser.parse(nlText)
                        if (p == null) nlError = true else applyParsed(p)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("解析并填入") }
            } else {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("备注（可选）") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    text = "⌨ 一句话记账：午饭32",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { nlMode = true },
                )
            }
            Spacer(Modifier.height(12.dp))

            // 数字键盘
            Keypad(
                onDigit = { amountInput = formatKeypadDisplay(amountInput + it) },
                onDot = {
                    if (!amountInput.contains('.')) {
                        amountInput = if (amountInput.isEmpty()) "0." else "$amountInput."
                    }
                },
                onBackspace = { long ->
                    amountInput = if (long) "" else amountInput.dropLast(1)
                }
            )
            Spacer(Modifier.height(14.dp))

            // 底部操作行：保存（与键盘等高56dp）左侧 + 语音大按钮右侧
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = { doSave() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    enabled = minor != null && minor > 0,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        if (edit != null) "保存修改"
                        else "保存${if (minor != null && minor > 0) " · ${formatMoney(minor, currency)}" else ""}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                VoiceMicButton(
                    waiting = voiceWaiting,
                    onSpeak = { startVoice() }
                )
            }
            if (edit == null) {
                // 反馈直接显示在按钮上：Snackbar 会被面板挡住，之前看起来像没反应
                var tplSaved by remember { mutableStateOf(false) }
                Text(
                    text = if (tplSaved) "✓ 已存为模板" else "⭐ 存为模板",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tplSaved) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable {
                            if (!tplSaved) {
                                saveAsTemplate()
                                tplSaved = true
                            }
                        }
                )
            }
        }
    }

    // 应用内语音识别界面（全屏、实时显示识别文字）
    if (listening) {
        Dialog(
            onDismissRequest = { session.stop(); listening = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xD90E1216))
            ) {
                val transition = rememberInfiniteTransition(label = "vp")
                val ring by transition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1100, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "vr"
                )
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .scale(ring)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    CircleShape
                                )
                        )
                        Box(
                            Modifier
                                .fillMaxSize(0.8f)
                                .scale(ring)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                        Box(
                            Modifier
                                .size(96.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Text(
                        partial.ifBlank { "请说一句…" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 22.dp)
                    )
                    Text(
                        "试试：午饭 32 · 昨天打车 15.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 46.dp)
                        .clickable { session.stop(); listening = false }
                ) {
                    Text(
                        "  取消  ",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/** 语音记账按钮（底部操作行）：60dp，等待识别时按钮内转圈 */
@Composable
private fun VoiceMicButton(
    waiting: Boolean,
    onSpeak: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "micScale"
    )
    Box(
        modifier = Modifier
            .size(74.dp)
            .scale(scale)
            .shadow(8.dp, CircleShape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSpeak()
            },
        contentAlignment = Alignment.Center
    ) {
        if (waiting) {
            androidx.compose.material3.CircularProgressIndicator(
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(30.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                Icons.Rounded.Mic,
                contentDescription = "语音记账，说一句即入账",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** iOS 式分段控件：滑块带弹性滑动动画 */
@Composable
private fun SegmentedType(type: Int, onSelect: (Int) -> Unit) {
    val half = 120.dp
    Box(Modifier.width(246.dp)) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                Modifier
                    .padding(3.dp)
                    .height(40.dp)
            ) {
                val offset by animateDpAsState(
                    targetValue = if (type == 0) 0.dp else half,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "segThumb"
                )
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(half)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                )
                Row(Modifier.fillMaxSize()) {
                    listOf(0 to "支出", 1 to "收入").forEach { (t, label) ->
                        Box(
                            modifier = Modifier
                                .width(half)
                                .fillMaxHeight()
                                .clickable { onSelect(t) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (type == t) {
                                    if (t == 0) LocalInkExtras.current.expense
                                    else LocalInkExtras.current.income
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CategoryChip(cat: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(cat.emoji, fontSize = 19.sp)
        }
        Text(
            if (selected) cat.name else cat.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDot: () -> Unit,
    onBackspace: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { rowKeys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKeys.forEach { k ->
                    KeypadKey(
                        text = k,
                        modifier = Modifier.weight(1f)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit(k)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadKey(text = ".", modifier = Modifier.weight(1f), onClick = onDot)
            KeypadKey(text = "0", modifier = Modifier.weight(1f)) { onDigit("0") }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onBackspace(false) },
                            onLongPress = { onBackspace(true) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "删除，长按清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 22.sp,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** 语音路由结果：根据说的话自动判断要创建什么 */
sealed class VoiceRoute {
    data class Tx(val parsed: ParsedEntry) : VoiceRoute()
    data class SavingsGoal(val name: String) : VoiceRoute()
    data class Habit(val name: String) : VoiceRoute()
    data class Countdown(val name: String, val daysHint: Long? = null) : VoiceRoute()
    data class Note(val text: String) : VoiceRoute()
    data class Unknown(val text: String) : VoiceRoute()
}

/** 语音智能路由：金额→记账，存钱/打卡/倒数关键词→对应创建，纯文字→记事 */
fun routeVoice(text: String): VoiceRoute {
    val t = text.trim()
    val parsed = NlParser.parse(t)
    // 无单位（无 块/元/数字）→ 裸中文数字是「目标额度」意图，关键词分支优先
    val hasUnit = Regex("""[元块¥￥]|\d""").containsMatchIn(t)
    val bareIntent = parsed == null || !hasUnit
    return when {
        bareIntent && (t.contains("存钱") || t.contains("攒") || t.contains("目标")) -> {
            val name = t.replace(Regex("""存钱|攒钱|目标|想|要|买|个"""), "").trim().ifBlank { t.trim() }
            VoiceRoute.SavingsGoal(name)
        }
        bareIntent && (t.contains("打卡") || t.contains("习惯")) -> {
            val name = t.replace(Regex("""每天|打卡|习惯|坚持"""), "").trim().ifBlank { t.trim() }
            VoiceRoute.Habit(name)
        }
        bareIntent && (t.contains("倒数") || t.contains("纪念") || t.contains("生日") || Regex("""还有\d+天""").containsMatchIn(t)) -> {
            val name = t.replace(Regex("""倒数|倒数日|还有\d+天|生日|纪念日"""), "").trim().ifBlank { t.trim() }
            val days = Regex("""还有(\d+)天""").find(t)?.groupValues?.get(1)?.toLongOrNull()
            VoiceRoute.Countdown(name, days)
        }
        parsed != null -> VoiceRoute.Tx(parsed)
        t.isNotBlank() -> VoiceRoute.Note(t)
        else -> VoiceRoute.Unknown(t)
    }
}
internal class VoiceSession(private val context: android.content.Context) {
    private var sr: SpeechRecognizer? = null
    @Volatile private var cancelled = false

    fun start(onPartial: (String) -> Unit, onFinal: (String?) -> Unit, manualStop: Boolean = false) {
        stop()
        cancelled = false
        sr = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { if (!cancelled) onFinal(null) }
                override fun onResults(results: Bundle?) {
                    if (!cancelled) {
                        onFinal(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (cancelled) return
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let(onPartial)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // 主动听模式：大幅拉长静音阈值，由用户按「完成」结束（引擎兜底仍会超时结束）
                    if (manualStop) {
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                    }
                }
            )
        }
    }

    /** 按住说话松手：结束采音并等待识别结果回调 */
    fun finishListening() {
        sr?.stopListening()
    }

    /** 手指移开取消：销毁识别器且忽略后续回调 */
    fun cancelListening() {
        cancelled = true
        sr?.destroy()
        sr = null
    }

    fun stop() {
        sr?.destroy()
        sr = null
    }
}
