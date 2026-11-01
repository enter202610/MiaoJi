package com.inkledger.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inkledger.app.AppViewModel
import com.inkledger.app.data.prefs.ThemePreset
import com.inkledger.app.ui.components.InkAlertDialog
import com.inkledger.app.ui.components.InkCard
import com.inkledger.app.ui.components.InkDialogButton
import com.inkledger.app.ui.components.SettingRow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private data class ThemeOption(val preset: ThemePreset, val label: String, val dots: List<Color>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenRecycle: () -> Unit = {}
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val txs by vm.txs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showMonthDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showExportPwd by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPwd by remember { mutableStateOf("") }
    var pendingExportMedia by remember { mutableStateOf(true) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var showWidgetBgDialog by remember { mutableStateOf(false) }
    var widgetCropUri by remember { mutableStateOf<Uri?>(null) }
    val widgetBgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) widgetCropUri = uri }

    // 主流做法：导出弹系统文件选择器自选位置（文件名预填），选择器不可用时兜底直写下载目录
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPwd
        pendingExportPwd = ""
                if (uri != null && pwd.isNotBlank()) {
                    vm.exportBackup(uri, pwd, pendingExportMedia) { ok ->
                        onMessage(if (ok) "加密备份已导出，请妥善保管文件与密码" else "导出失败")
                    }
                }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importUri = uri }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            vm.exportCsv(it) { ok ->
                onMessage(if (ok) "已导出 CSV" else "导出失败")
            }
        }
    }

    val notifPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onMessage("通知权限已开启") }

    val listenerEnabled = vm.isListenerEnabled()
    val notifGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    // 浅色系列：4 基础 + 6 城市 + 动态取色（深色已下架，老偏好自动转晴空蓝）
    val themeOptions = listOf(
        ThemeOption(ThemePreset.LIGHT_SKY, "晴空蓝 ★默认", listOf(Color(0xFFF2F6FA), Color(0xFFDBE9F5), Color(0xFF5B8DB8))),
        ThemeOption(ThemePreset.LIGHT_CREAM, "奶油白", listOf(Color(0xFFFAF6F0), Color(0xFFDFEEE5), Color(0xFF6FA98C))),
        ThemeOption(ThemePreset.LIGHT_MATCHA, "抹茶奶绿", listOf(Color(0xFFF5F8F2), Color(0xFFE2EED9), Color(0xFF7C9E6B))),
        ThemeOption(ThemePreset.LIGHT_LAVENDER, "淡烟紫", listOf(Color(0xFFF6F4F9), Color(0xFFE6E0F5), Color(0xFF8F82BE))),
        ThemeOption(ThemePreset.CITY_SHENZHEN, "城市 · 深圳 湾区青", listOf(Color(0xFFF3FAF7), Color(0xFFD6F0E7), Color(0xFF2E9C86))),
        ThemeOption(ThemePreset.CITY_SHANGHAI, "城市 · 上海 外滩蓝", listOf(Color(0xFFF3F6FB), Color(0xFFD9E4F6), Color(0xFF4A72B8))),
        ThemeOption(ThemePreset.CITY_BEIJING, "城市 · 北京 宫墙金", listOf(Color(0xFFFAF5EF), Color(0xFFF5E3CE), Color(0xFFB4763C))),
        ThemeOption(ThemePreset.CITY_GUANGZHOU, "城市 · 广州 木棉暖", listOf(Color(0xFFFBF5F1), Color(0xFFF8DFCF), Color(0xFFD07A50))),
        ThemeOption(ThemePreset.CITY_HK, "城市 · 香港 维港紫", listOf(Color(0xFFF3F2FB), Color(0xFFDFDCF7), Color(0xFF6A63C9))),
        ThemeOption(ThemePreset.CITY_MACAU, "城市 · 澳门 鹅黄金", listOf(Color(0xFFFAF8F1), Color(0xFFEEE7CB), Color(0xFF95854C)))
    )

    fun pickTheme(p: ThemePreset) {
        android.util.Log.d("InkTheme", "pick $p bgEnabled=${settings.bgEnabled} bgGradient=${settings.bgGradient}")
        vm.setTheme(p)
        // 只应用主题色：渐变 / 自定义图 / 是否跟随等背景设置保持不变
        onMessage("已切换主题")
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            com.inkledger.app.ui.components.PageHeader(title = "设置", onBack = onBack)
        }

        // ---- 外观 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "外观",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    // 中国传统色：下拉选择，选中后应用主题强调色 + 小组件背景一起生效
                    var chineseMenuOpen by remember { mutableStateOf(false) }
                    val selectedChinese = settings.chineseSeedIndex
                        .coerceIn(0, com.inkledger.app.ui.theme.CHINESE_COLORS.lastIndex)
                        .let { com.inkledger.app.ui.theme.CHINESE_COLORS[it] }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chineseMenuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(
                                            Color(com.inkledger.app.ui.theme.shadeColor(selectedChinese.seed, 0.85f).toLong()),
                                            Color(com.inkledger.app.ui.theme.shadeColor(selectedChinese.seed, 1.25f).toLong())
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                        Text(
                            selectedChinese.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        )
                        // 右侧标注：告诉用户这行是「中国传统色」下拉，当前主题生效时打勾
                        Text(
                            if (settings.theme == ThemePreset.CHINESE) "中国传统色 ✓" else "中国传统色 ▼",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.theme == ThemePreset.CHINESE) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = chineseMenuOpen,
                            onDismissRequest = { chineseMenuOpen = false },
                            modifier = Modifier.heightIn(max = 320.dp)
                        ) {
                            com.inkledger.app.ui.theme.CHINESE_COLORS.forEachIndexed { i, cc ->
                                val selected = settings.theme == ThemePreset.CHINESE && settings.chineseSeedIndex == i
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier
                                                    .size(22.dp)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.linearGradient(
                                                            listOf(
                                                                Color(com.inkledger.app.ui.theme.shadeColor(cc.seed, 0.85f).toLong()),
                                                                Color(com.inkledger.app.ui.theme.shadeColor(cc.seed, 1.25f).toLong())
                                                            )
                                                        ),
                                                        CircleShape
                                                    )
                                            )
                                            Text(
                                                cc.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(start = 12.dp)
                                            )
                                            if (selected) {
                                                Text(
                                                    "✓",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 10.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        vm.setTheme(ThemePreset.CHINESE)
                                        vm.setChineseSeed(i)
                                        // 主题 + 小组件一起生效：清掉自定义背景/渐变，应用所选中国色
                                        if (settings.bgEnabled) {
                                            com.inkledger.app.data.media.BackgroundManager.clear(context)
                                            vm.setBgEnabled(false)
                                        }
                                        if (settings.bgGradient != -1) {
                                            vm.setBgGradient(-1)
                                        }
                                        chineseMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    // 小组件背景：跟随主题 / 自定义图片（裁剪+模糊）/ 纯色，独立于应用设置
                    SettingRow(
                        title = "小组件背景",
                        subtitle = when (settings.widgetBgMode) {
                            1 -> if (com.inkledger.app.data.media.BackgroundManager.customFile(context, "widget_bg.jpg") != null)
                                "自定义图片 · 模糊 ${settings.widgetBgBlur}%"
                            else "自定义图片（还没选图）"
                            2 -> "纯色"
                            else -> "跟随主题（与设置同步）"
                        },
                        onClick = { showWidgetBgDialog = true }
                    )
                    themeOptions.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickTheme(opt.preset) }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            if (opt.preset.name.startsWith("CITY_")) {
                                // 城市主题：迷你地标剪影预览
                                Box(
                                    Modifier
                                        .size(width = 56.dp, height = 34.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .background(opt.dots[0])
                                ) {
                                    com.inkledger.app.ui.theme.CitySilhouetteMini(
                                        opt.preset, opt.dots[2],
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    opt.dots.forEach { c ->
                                        Box(
                                            Modifier
                                                .size(16.dp)
                                                .background(c, CircleShape)
                                        )
                                    }
                                }
                            }
                            Text(opt.label, Modifier.weight(1f))
                            RadioButton(selected = settings.theme == opt.preset, onClick = { pickTheme(opt.preset) })
                        }
                    }
                    // 渐变背景（全屏，与倒数日详情页同款效果）
                    Text(
                        "渐变背景",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    Text(
                        "选择一款全屏渐变色铺满整个 App（与自定义图片二选一，后选的生效）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )
                    // 「不使用」+ 12 个预置色
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (settings.bgGradient == -1) 2.dp else 0.dp,
                                    color = if (settings.bgGradient == -1) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                                ),
                                modifier = Modifier.clickable { vm.setBgGradient(-1) }
                            ) {
                                Text(
                                    "关",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                        items(com.inkledger.app.ui.theme.APP_GRADIENTS.size) { i ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            com.inkledger.app.ui.theme.APP_GRADIENTS[i]
                                        ),
                                        CircleShape
                                    )
                                    .clickable {
                                        vm.setBgGradient(i)
                                        // 选渐变时自动关闭图片背景
                                        if (settings.bgEnabled) vm.setBgEnabled(false)
                                    }
                                    .then(
                                        if (settings.bgGradient == i)
                                            Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                CircleShape
                                            )
                                        else Modifier
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val bgPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri != null) cropUri = uri
                    }
                    SettingRow(
                        title = "自定义背景",
                        subtitle = if (settings.bgEnabled) "已设置 · 点此更换（深度模糊 + 压暗）"
                        else "选一张图做全局背景，自动深度模糊+压暗，卡片变玻璃",
                        onClick = {
                            bgPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    if (settings.bgEnabled) {
                        // 本地滑杆状态：拖动流畅预览，松手才提交（每帧写库会和状态回写打架导致滑不动）
                        var bgBlurLocal by remember(settings.bgBlur) {
                            mutableFloatStateOf(settings.bgBlur.toFloat())
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                "毛玻璃模糊 ${bgBlurLocal.toInt()}%（拖动实时预览↓，松手保存）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.Slider(
                                value = bgBlurLocal,
                                onValueChange = { bgBlurLocal = it },
                                onValueChangeFinished = { vm.setBgBlur(bgBlurLocal.toInt()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // ===== 实时预览：模拟明细页在当前模糊度下的效果 =====
                            val previewCtx = LocalContext.current
                            val previewBmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
                                initialValue = null, settings.bgVersion
                            ) {
                                value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.inkledger.app.data.media.BackgroundManager.loadProcessed(
                                        previewCtx,
                                        android.content.res.Resources.getSystem().displayMetrics.widthPixels,
                                        0  // 原图，不加预处理模糊
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            ) {
                                // 背景图 + 毛玻璃层
                                previewBmp?.let { bmp ->
                                    androidx.compose.foundation.Image(
                                        bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (bgBlurLocal > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                                    Modifier.graphicsLayer {
                                                        renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                                            bgBlurLocal / 100f * 60f,
                                                            bgBlurLocal / 100f * 60f,
                                                            android.graphics.Shader.TileMode.CLAMP
                                                        ).asComposeRenderEffect()
                                                    }
                                                } else Modifier
                                            ),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    // 压暗层
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(alpha = 0.18f + 0.4f * (bgBlurLocal / 100f))
                                            )
                                    )
                                }
                                // 模拟明细页内容（摘要卡 + 两行交易）
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text("本月支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("¥2,348.50", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            listOf("🍜 餐饮 · 午饭" to "-¥25.00", "🚌 交通 · 地铁" to "-¥4.00").forEach { (name, amt) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                    Text(amt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingRow(
                            title = "清除背景",
                            subtitle = "恢复纯色主题背景",
                            onClick = {
                                com.inkledger.app.data.media.BackgroundManager.clear(context)
                                vm.setBgEnabled(false)
                                onMessage("已恢复默认背景")
                            }
                        )
                    }
                }
            }
        }

        // ---- 记账提速 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "记账提速",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    SettingRow(
                        title = "自动记账",
                        subtitle = "监听微信 / 支付宝的支付通知，自动入账",
                        trailing = {
                            Switch(
                                checked = settings.autoRecordEnabled,
                                onCheckedChange = { on ->
                                    if (on && !listenerEnabled) {
                                        vm.setAutoRecord(true)
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                        onMessage("请在本页允许「妙计」使用通知使用权")
                                    } else {
                                        vm.setAutoRecord(on)
                                    }
                                }
                            )
                        }
                    )
                    if (settings.autoRecordEnabled && !listenerEnabled) {
                        Text(
                            "⚠ 通知使用权还没开启：点上面开关后会跳到系统设置，找到「妙计」打开即可",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                    SettingRow(
                        title = "记完弹确认通知",
                        subtitle = "关闭后静默入账，可在明细里核对",
                        trailing = {
                            Switch(
                                checked = settings.autoConfirmMode,
                                onCheckedChange = { vm.setAutoConfirm(it) }
                            )
                        }
                    )
                    if (!notifGranted) {
                        SettingRow(
                            title = "开启通知权限",
                            subtitle = "用于展示自动记账的结果通知",
                            onClick = { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                    Text(
                        "说明：仅读取支付通知的文字内容，数据全部留在本机；微信需保持「服务通知-微信支付」消息详情开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ---- 通用 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "通用",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    SettingRow(
                        title = "每月起始日",
                        subtitle = "当前从每月 ${settings.monthStartDay} 号开始算",
                        onClick = { showMonthDialog = true }
                    )
                    SettingRow(
                        title = "货币符号",
                        subtitle = settings.currency,
                        onClick = { showCurrencyDialog = true }
                    )
                }
            }
        }

        // ---- 数据 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "数据",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    SettingRow(
                        title = "回收站",
                        subtitle = "误删 7 天内可恢复",
                        onClick = onOpenRecycle
                    )
                    SettingRow(
                        title = "纯净模式",
                        subtitle = "首页只留核心数字与账单，隐藏预算条和倒数日",
                        trailing = {
                            Switch(checked = settings.pureMode, onCheckedChange = { vm.setPureMode(it) })
                        },
                        onClick = { vm.setPureMode(!settings.pureMode) }
                    )
                    SettingRow(
                        title = "导出 CSV",
                        subtitle = "共 ${txs.size} 条记录，选择位置保存，Excel 可直接打开",
                        onClick = {
                            val name = "inkledger_" +
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".csv"
                            try {
                                csvLauncher.launch(name)
                            } catch (e: Exception) {
                                // ROM 没有系统文件选择器：直接写下载目录
                                vm.exportCsvToDownloads(name) { ok ->
                                    onMessage(
                                        if (ok) "已直接保存到 下载/$name"
                                        else "导出失败：${e.message}"
                                    )
                                }
                            }
                        }
                    )
                    SettingRow(
                        title = "清空所有数据",
                        subtitle = "删除全部账目、目标、记事，不可恢复",
                        onClick = { showClearConfirm = true },
                        trailing = {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "清空",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    )
                }
            }
        }

        // ---- AI 助手 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "AI 助手",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    SettingRow(
                        title = "大模型问答",
                        subtitle = "关闭＝纯本机洞察零联网；开启后可自然语言问答账单",
                        trailing = {
                            Switch(
                                checked = settings.aiEnabled,
                                onCheckedChange = { vm.setAiEnabled(it) }
                            )
                        }
                    )
                    if (settings.aiEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            listOf("glm" to "智谱GLM", "deepseek" to "DeepSeek", "custom" to "自定义").forEach { (v, label) ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (settings.aiProvider == v) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        vm.setAiProvider(v)
                                        // 模型名自动填该提供商默认值，留空亦可
                                        vm.setAiModel(com.inkledger.app.data.ai.LlmClient.defaultModelFor(v))
                                    }
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (settings.aiProvider == v) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            OutlinedTextField(
                                value = settings.aiKey,
                                onValueChange = { vm.setAiKey(it) },
                                label = { Text("API Key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = settings.aiModel,
                                onValueChange = { vm.setAiModel(it) },
                                label = {
                                    Text("模型名（默认 ${com.inkledger.app.data.ai.LlmClient.defaultModelFor(settings.aiProvider).ifBlank { "必填" }}）")
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                            if (settings.aiProvider == "custom") {
                                OutlinedTextField(
                                    value = settings.aiBase,
                                    onValueChange = { vm.setAiBase(it) },
                                    label = { Text("API 地址（自动拼接 /chat/completions）") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                )
                            }
                            Text(
                                "隐私：仅发送本月/上月汇总统计与你的提问，不含账单明细；API Key 只存本机；关闭开关后 App 恢复零联网。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- 备份与恢复 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "备份与恢复",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                    )
                    SettingRow(
                        title = "导出加密备份",
                        subtitle = "全部数据加密为 .inkbak 文件（AES-256），换机/存档用",
                        onClick = { showExportPwd = true }
                    )
                    SettingRow(
                        title = "导入备份恢复",
                        subtitle = "选择备份文件 + 密码，支持合并导入或覆盖导入",
                        onClick = {
                            // 主流做法：弹系统文件选择器挑备份文件
                            try {
                                importLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                // ROM 没有选择器：自动读下载目录里最近的备份
                                vm.findLatestBackupInDownloads { latest ->
                                    if (latest != null) importUri = latest
                                    else onMessage("未找到文件选择器，且下载目录里没有妙计备份")
                                }
                            }
                        }
                    )
                }
            }
        }

        // ---- 关于 ----
        item {
            InkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("妙计 v2.2 · 好记性，不如妙计", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "离线记账 · 无网络权限 · 数据全部保存在本机。\n快速记账：记账面板（3 秒）、模板一键、一句话「午饭32」、微信/支付宝通知自动记账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        item { Spacer(Modifier.size(96.dp)) }
    }

    if (showMonthDialog) {
        InkAlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = { Text("每月从几号开始算？") },
            text = {
                Column {
                    Text(
                        "自然月：选 1，每月 1 日到月底算一个月\n发薪周期：15 号发工资就选 15，本月 15 日到次月 14 日为一个周期，预算/统计/AI 全部按此计算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf(1 to "自然月 · 1日", 15 to "发薪周期 · 15日").forEach { (d, label) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (settings.monthStartDay == d) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    vm.setMonthStartDay(d)
                                    showMonthDialog = false
                                }
                            ) {
                                Text(
                                    label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (settings.monthStartDay == d) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                    (1..28).forEach { d ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (d == settings.monthStartDay) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                vm.setMonthStartDay(d)
                                showMonthDialog = false
                            }
                        ) {
                            Text(
                                "$d",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = if (d == settings.monthStartDay) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            },
            confirmButton = {
                InkDialogButton("取消", onClick = { showMonthDialog = false })
            }
        )
    }
    if (showCurrencyDialog) {
        var input by remember { mutableStateOf(settings.currency) }
        InkAlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("货币符号") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(3) },
                    singleLine = true
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { showCurrencyDialog = false })
                    InkDialogButton(
                        "保存",
                        primary = true,
                        onClick = {
                            vm.setCurrency(input)
                            showCurrencyDialog = false
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
    if (showClearConfirm) {
        InkAlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("确定清空所有数据？") },
            text = { Text("全部账目、模板、目标、倒数日、记事都会被删除，且无法恢复。") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { showClearConfirm = false })
                    InkDialogButton(
                        "全部删除",
                        danger = true,
                        onClick = {
                            vm.clearAllData()
                            showClearConfirm = false
                            onMessage("已清空")
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
    if (showExportPwd) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var includeMedia by remember { mutableStateOf(true) }
        InkAlertDialog(
            onDismissRequest = { showExportPwd = false },
            title = { Text("导出加密备份") },
            text = {
                Column {
                    OutlinedTextField(
                        value = p1, onValueChange = { p1 = it },
                        label = { Text("设置密码（至少 4 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = p2, onValueChange = { p2 = it },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = p2.isNotBlank() && p1 != p2,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Checkbox(checked = includeMedia, onCheckedChange = { includeMedia = it })
                        Text(
                            "包含图片（记事图片/倒数日背景/应用背景）",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                    Text(
                        "不勾选 = 纯数据备份，文件更小；换机后图片不跟随",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { showExportPwd = false })
                    InkDialogButton(
                        "导出",
                        primary = true,
                        onClick = {
                            if (p1.length >= 4 && p1 == p2) {
                                pendingExportPwd = p1
                                pendingExportMedia = includeMedia
                                showExportPwd = false
                                val name = "inkledger_" + LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".inkbak"
                                try {
                                    // 主流做法：弹系统文件选择器自选保存位置
                                    exportBackupLauncher.launch(name)
                                } catch (e: Exception) {
                                    // ROM 没有选择器：直接写下载目录
                                    vm.exportBackupToDownloads(name, p1, pendingExportMedia) { ok ->
                                        onMessage(
                                            if (ok) "已直接保存到 下载/$name，请妥善保管密码"
                                            else "导出失败：${e.message}"
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
    cropUri?.let { cu ->
        com.inkledger.app.ui.components.ImageCropDialog(
            uri = cu,
            onDismiss = { cropUri = null },
            saveTo = { b ->
                val ok = com.inkledger.app.data.media.BackgroundManager.saveBitmap(context, b)
                if (ok) {
                    vm.setBgEnabled(true)
                    vm.setBgGradient(-1)   // 自定义图直接顶掉渐变（互斥：后选的生效）
                    vm.setBgVersion(settings.bgVersion + 1)
                    onMessage("背景已应用")
                }
                ok
            }
        )
    }
    if (showWidgetBgDialog) {
        var widgetBgStep by remember { mutableStateOf(0) }  // 0=选模式 1=图片设置 2=颜色选择
        InkAlertDialog(
            onDismissRequest = { showWidgetBgDialog = false },
            title = {
                Text(
                    when (widgetBgStep) {
                        1 -> "小组件背景 · 自定义图片"
                        2 -> "小组件背景 · 纯色"
                        else -> "小组件背景"
                    }
                )
            },
            text = {
                Column {
                    when (widgetBgStep) {
                        1 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InkDialogButton("选择图片（横向裁剪）", primary = true, onClick = {
                                    widgetBgPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                })
                            }
                            Text(
                                "模糊 ${settings.widgetBgBlur}%（拖动实时刷新桌面）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            androidx.compose.material3.Slider(
                                value = settings.widgetBgBlur / 100f,
                                onValueChange = { vm.setWidgetBgBlur((it * 100).toInt()) }
                            )
                        }
                        2 -> {
                            Text(
                                "点颜色立即应用到桌面",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                val colors: List<Long> = listOf(0xFF5B8DB8L) +
                                    com.inkledger.app.ui.theme.CHINESE_COLORS.map { it.seed } +
                                    listOf(0xFFF6F2EAL, 0xFF2B2B2BL)
                                items(colors.size) { i ->
                                    val c = colors[i]
                                    androidx.compose.foundation.layout.Box(
                                        Modifier
                                            .size(30.dp)
                                            .background(Color(c), CircleShape)
                                            .border(
                                                width = if (settings.widgetBgColor == c) 2.dp else 0.dp,
                                                color = if (settings.widgetBgColor == c) MaterialTheme.colorScheme.primary
                                                else androidx.compose.ui.graphics.Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { vm.setWidgetBgColor(c) }
                                    )
                                }
                            }
                        }
                        else -> {
                            listOf(
                                Triple(0, "跟随主题（与设置同步）", settings.widgetBgMode == 0),
                                Triple(1, "自定义图片", settings.widgetBgMode == 1),
                                Triple(2, "纯色", settings.widgetBgMode == 2)
                            ).forEach { (m, label, active) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.setWidgetBgMode(m)
                                            if (m == 1) widgetBgStep = 1
                                            if (m == 2) widgetBgStep = 2
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (active) {
                                        Text(
                                            "✓",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (widgetBgStep == 0) {
                    InkDialogButton("完成", onClick = { showWidgetBgDialog = false })
                } else {
                    InkDialogButton("返回", onClick = { widgetBgStep = 0 })
                }
            },
            dismissButton = {}
        )
    }
    widgetCropUri?.let { cu ->
        com.inkledger.app.ui.components.ImageCropDialog(
            uri = cu,
            onDismiss = { widgetCropUri = null },
            saveTo = { b ->
                val ok = com.inkledger.app.data.media.BackgroundManager.saveBitmap(context, b, "widget_bg.jpg")
                if (ok) vm.refreshWidgetBgNow()
                ok
            },
            aspect = 4f / 3f   // 手机横拍原生比例，取景不浪费；多余部分渲染时按组件比例居中裁齐
        )
    }
    importUri?.let { uri ->
        var pwd by remember { mutableStateOf("") }
        var overwrite by remember { mutableStateOf(false) }
        InkAlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("导入备份") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pwd, onValueChange = { pwd = it },
                        label = { Text("备份密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clickable { overwrite = false }
                    ) {
                        RadioButton(selected = !overwrite, onClick = { overwrite = false })
                        Text("合并导入（保留现有数据，自动去重）")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { overwrite = true }
                    ) {
                        RadioButton(selected = overwrite, onClick = { overwrite = true })
                        Text("覆盖导入（清空现有全部数据）", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkDialogButton("取消", onClick = { importUri = null })
                    InkDialogButton(
                        "导入",
                        primary = true,
                        onClick = {
                            val u = uri
                            importUri = null
                            if (u != null && pwd.isNotBlank()) {
                                vm.importBackup(u, pwd, overwrite) { ok, count ->
                                    onMessage(
                                        if (ok) {
                                            if (overwrite) "已覆盖导入 $count 笔记录"
                                            else "合并导入 $count 笔新记录"
                                        } else "导入失败：密码错误或文件损坏"
                                    )
                                }
                            }
                        }
                    )
                }
            },
            dismissButton = {}
        )
    }
}
