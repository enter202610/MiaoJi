package com.inkledger.app

import android.graphics.RenderEffect
import android.graphics.Shader
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inkledger.app.data.media.BackgroundManager
import com.inkledger.app.ui.ai.AiPanel
import com.inkledger.app.ui.components.InkAiFab
import com.inkledger.app.ui.components.InkBottomBar
import com.inkledger.app.ui.goals.GoalsScreen
import com.inkledger.app.ui.home.HomeScreen
import com.inkledger.app.ui.notes.NoteEditScreen
import com.inkledger.app.ui.notes.NotesScreen
import com.inkledger.app.ui.onboarding.OnboardingScreen
import com.inkledger.app.ui.quickentry.QuickEntryRequest
import com.inkledger.app.ui.quickentry.QuickEntrySheet
import com.inkledger.app.ui.settings.SettingsScreen
import com.inkledger.app.ui.stats.StatsScreen
import com.inkledger.app.ui.templates.TemplatesScreen
import com.inkledger.app.ui.theme.InkTheme
import com.inkledger.app.util.monthPeriodStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 回到前台时刷新桌面小组件（数据可能在别处变了）
        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    com.inkledger.app.widget.WidgetSync.refreshAsync(applicationContext)
                }
            }
        })
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            InkTheme(settings.theme, settings.chineseSeedIndex) {
                InkBackdrop(
                    bgEnabled = settings.bgEnabled,
                    bgVersion = settings.bgVersion,
                    bgBlur = settings.bgBlur,
                    bgGradient = settings.bgGradient,
                    theme = settings.theme
                ) {
                    if (!settings.onboarded) OnboardingScreen(vm) else AppRoot(vm)
                }
            }
        }
    }
}

/** 全局背景层：渐变背景 / 自定义图（原图+实时毛玻璃层）/ 城市图案 / 主题纯色 */
@Composable
fun InkBackdrop(
    bgEnabled: Boolean,
    bgVersion: Int,
    bgBlur: Int,
    bgGradient: Int,
    theme: com.inkledger.app.data.prefs.ThemePreset,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(initialValue = null, bgEnabled, bgVersion) {
        if (bgEnabled && bgGradient < 0) {
            value = withContext(Dispatchers.IO) {
                BackgroundManager.loadProcessed(
                    context, Resources.getSystem().displayMetrics.widthPixels, 0
                )
            }
        } else {
            value = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        // 1) 全屏渐变背景（优先级最高，选了渐变就不用图片）
        if (bgGradient >= 0 && bgGradient < com.inkledger.app.ui.theme.APP_GRADIENTS.size) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            com.inkledger.app.ui.theme.APP_GRADIENTS[bgGradient]
                        )
                    )
            )
        }
        // 2) 自定义图片 + 精致毛玻璃（图片固定不动铺满全屏，模糊度滑杆控制）
        val b = bmp
        if (b != null && bgGradient < 0) {
            Image(
                b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (bgBlur > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.graphicsLayer {
                                renderEffect = RenderEffect.createBlurEffect(
                                    bgBlur / 100f * 40f,
                                    bgBlur / 100f * 40f,
                                    Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        } else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
            // 玻璃质感：白色轻纱提亮（毛玻璃的核心是一层「雾」而不是压暗）
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.10f + 0.22f * (bgBlur / 100f)))
            )
            // 上下极轻暗角：只照顾状态栏/导航栏边缘，不再整屏压黑
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.07f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.09f)
                            )
                        )
                    )
            )
        }
        // 内容层：渐变或图片存在时透明，否则用主题纯色
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (b != null || bgGradient >= 0) Modifier
                    else Modifier.background(MaterialTheme.colorScheme.background)
                )
        ) {
            // 自定义图片背景：铺一层主题色浅纱，保证无卡片的明细条目文字可读
            // 图片越清晰（模糊小）纱越浓；高模糊本身已成浅色底，纱淡一些
            if (b != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = 0.45f - 0.15f * (bgBlur / 100f)
                            )
                        )
                )
            }
            // 城市主题：铺入象征图案水印（极低透明度，不碍眼）
            if (bgGradient < 0 && b == null &&
                theme in listOf(
                    com.inkledger.app.data.prefs.ThemePreset.CITY_SHENZHEN,
                    com.inkledger.app.data.prefs.ThemePreset.CITY_SHANGHAI,
                    com.inkledger.app.data.prefs.ThemePreset.CITY_BEIJING,
                    com.inkledger.app.data.prefs.ThemePreset.CITY_GUANGZHOU,
                    com.inkledger.app.data.prefs.ThemePreset.CITY_HK,
                    com.inkledger.app.data.prefs.ThemePreset.CITY_MACAU
                )
            ) {
                com.inkledger.app.ui.theme.CityPattern(
                    preset = theme,
                    modifier = Modifier.fillMaxSize()
                )
            }
            content()
        }
    }
}

private data class TabItem(val route: String, val label: String)

@Composable
fun AppRoot(vm: AppViewModel, openQuick: Boolean = false) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()

    // 小组件「记一笔」直达：打开应用即弹记账面板（声明见下方 quickRequest）
    val quickFromWidget = openQuick

    // Snackbar 防堆积：每次新消息先取消旧的，只显示最新一条
    var snackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun showMessage(msg: String) {
        snackbarJob?.cancel()
        snackbarJob = scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    var quickRequest by remember { mutableStateOf<QuickEntryRequest?>(null) }

    // 小组件「记一笔」：应用存活期间点小组件 → ON_RESUME 读取 intent 直弹记账面板
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(activity) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                activity?.intent?.getStringExtra("open") == "quick"
            ) {
                activity.intent.removeExtra("open")
                quickRequest = QuickEntryRequest()
            }
        }
        activity?.lifecycle?.addObserver(obs)
        onDispose { activity?.lifecycle?.removeObserver(obs) }
    }

    var aiOpen by remember { mutableStateOf(false) }
    var voiceOnlyOpen by remember { mutableStateOf(false) }  // 长按＋独立语音页
    var successOverlay by remember { mutableStateOf<String?>(null) }  // 提交成功对勾动画

    // 目标页子页签（0 存钱 / 1 打卡 / 2 倒数日）：提升到这里，让底栏 ＋ 上下文感知
    var goalsTab by rememberSaveable { mutableIntStateOf(0) }
    var goalsAddSignal by remember { mutableStateOf<Int?>(null) }

    // 启动即请求麦克风权限：保证长按＋的语音一气呵成，不被授权弹窗打断
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                appContext, android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    var periodEpochDay by rememberSaveable {
        mutableStateOf(LocalDate.now().monthPeriodStart(settings.monthStartDay).toEpochDay())
    }
    val period = remember(periodEpochDay) { LocalDate.ofEpochDay(periodEpochDay) }
    val startDay = settings.monthStartDay

    // 起始日设置变化时，查看周期立即按新算法重新对齐
    // （否则停留的周期还是旧起始日算出的日期，过滤不到任何账单，显示为空）
    LaunchedEffect(startDay) {
        periodEpochDay = LocalDate.now().monthPeriodStart(startDay).toEpochDay()
    }

    val mainRoutes = listOf("home", "stats", "goals", "notes")

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in mainRoutes

    Box(Modifier.fillMaxSize()) {
        // 内容层：打开记账面板/AI 面板时整体高斯模糊 = 液态玻璃底衬（Android 12+）
        val blurModifier =
            if ((quickRequest != null || aiOpen) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(
                        72f, 72f, Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
            } else {
                Modifier
            }

        Box(
            Modifier
                .fillMaxSize()
                .then(blurModifier)
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                // 二级页面：iOS 式推入/返回滑动特效；四个主标签页在各自 composable 里覆盖为淡入缩放
                enterTransition = {
                    slideInHorizontally(tween(320, easing = EaseInOutCubic)) { it } +
                        fadeIn(tween(320))
                },
                exitTransition = {
                    slideOutHorizontally(tween(320, easing = EaseInOutCubic)) { -it / 4 } +
                        fadeOut(tween(320))
                },
                popEnterTransition = {
                    slideInHorizontally(tween(320, easing = EaseInOutCubic)) { -it / 4 } +
                        fadeIn(tween(320))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(320, easing = EaseInOutCubic)) { it } +
                        fadeOut(tween(320))
                }
            ) {
                composable(
                    "home",
                    enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) }
                ) {
                    HomeScreen(
                        vm = vm,
                        periodStart = period,
                        visible = currentRoute == "home",
                        onChangePeriod = { periodEpochDay = it.monthPeriodStart(startDay).toEpochDay() },
                        onEditTx = { quickRequest = QuickEntryRequest(editTx = it) },
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenTemplates = { navController.navigate("templates") },
                        onMessage = ::showMessage
                    )
                }
                composable(
                    "stats",
                    enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) }
                ) {
                    StatsScreen(
                        vm = vm,
                        periodStart = period,
                        onChangePeriod = { periodEpochDay = it.monthPeriodStart(startDay).toEpochDay() }
                    )
                }
                composable(
                    "goals",
                    enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) }
                ) {
                    GoalsScreen(
                        vm = vm,
                        onMessage = ::showMessage,
                        tab = goalsTab,
                        onTabChange = { goalsTab = it },
                        addSignal = goalsAddSignal,
                        onAddConsumed = { goalsAddSignal = null },
                        onOpenCountdown = { id -> navController.navigate("countdown/$id") }
                    )
                }
                composable("countdown/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                    com.inkledger.app.ui.goals.CountdownDetailScreen(
                        vm = vm,
                        id = id,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "notes",
                    enterTransition = { fadeIn(tween(200)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) }
                ) {
                    NotesScreen(vm, onOpenNote = { id -> navController.navigate("note/$id") })
                }
                composable("note/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                    NoteEditScreen(vm = vm, noteId = id, onBack = { navController.popBackStack() })
                }
                composable("templates") {
                    TemplatesScreen(vm, onBack = { navController.popBackStack() }, onMessage = ::showMessage)
                }
                composable("settings") {
                    SettingsScreen(vm, onBack = { navController.popBackStack() }, onMessage = ::showMessage,
                        onOpenRecycle = { navController.navigate("recycle") })
                }
                composable("recycle") {
                    com.inkledger.app.ui.recycle.RecycleBinScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onMessage = ::showMessage
                    )
                }
            }
        }

        if (showBar) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                InkBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route -> navController.navigateTab(route) },
                    pureMode = settings.pureMode,
                    onRecord = {
                        // 点按：上下文感知。路由必须在【调用时】实时读取——
                        // 组合期算好的 currentRoute 在手势回调里是过期值，导致所有页面都弹记账面板
                        when (navController.currentBackStackEntry?.destination?.route) {
                            "goals" -> goalsAddSignal = goalsTab
                            "notes" -> navController.navigate("note/-1")
                            else -> quickRequest = QuickEntryRequest()
                        }
                    },
                    // 按住＋：超过长按阈值即弹出语音页（自动拉起系统识别，说完自动执行）
                    onLongRecordStart = { voiceOnlyOpen = true },
                    onLongRecordEnd = { },
                    onLongRecordCancel = { }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
        ) { data ->
            // 主题化胶囊条：主色容器底 + 主题文字，与整体风格统一（不再是黑条）
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 3.dp
            ) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
                )
            }
        }

        quickRequest?.let { req ->
            QuickEntrySheet(
                vm = vm,
                request = req,
                onDismiss = { quickRequest = null },
                onMessage = ::showMessage,
                onSuccess = { msg -> successOverlay = msg }
            )
        }

        successOverlay?.let { msg ->
            com.inkledger.app.ui.components.SuccessOverlay(text = msg) { successOverlay = null }
        }

        // AI 悬浮球：可拖动、自动吸边、闲置半透明，不挡内容（记事页/纯净模式隐藏）
        com.inkledger.app.ui.components.InkAiFab(
            onClick = { aiOpen = true },
            visible = showBar && currentRoute != "notes" && !settings.pureMode,
            modifier = Modifier.fillMaxSize()
        )

        if (voiceOnlyOpen) {
            com.inkledger.app.ui.quickentry.VoiceOnlyOverlay(
                vm = vm,
                onDismiss = { voiceOnlyOpen = false },
                onMessage = ::showMessage,
                onSuccess = { msg -> successOverlay = msg }
            )
        }

        if (aiOpen) {
            AiPanel(
                vm = vm,
                onDismiss = { aiOpen = false },
                onOpenSettings = {
                    aiOpen = false
                    navController.navigate("settings")
                }
            )
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}