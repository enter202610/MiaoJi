package com.inkledger.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.inkledger.app.MainActivity
import com.inkledger.app.R
import com.inkledger.app.data.db.AppDatabase
import com.inkledger.app.data.db.TransactionEntity
import com.inkledger.app.data.prefs.SettingsRepository
import com.inkledger.app.data.prefs.ThemePreset
import com.inkledger.app.ui.quickentry.QuickEntryActivity
import com.inkledger.app.ui.theme.CHINESE_COLORS
import com.inkledger.app.ui.theme.readableSeed
import com.inkledger.app.ui.theme.shadeColor
import com.inkledger.app.util.formatCompact
import com.inkledger.app.util.monthPeriodStart
import com.inkledger.app.util.toLocalDate
import com.inkledger.app.util.toLocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

const val WIDGET_LAYOUT_SMALL = 0
const val WIDGET_LAYOUT_MEDIUM = 1
const val WIDGET_LAYOUT_LARGE = 2

/** 各主题对应的深→浅渐变（白字高对比）；传统色用种子色实时派生（过亮自动压深） */
private fun themeGradient(theme: ThemePreset, chineseSeed: Int): Pair<Long, Long> =
    when (theme) {
        ThemePreset.LIGHT_LAVENDER -> 0xFF5D5183L to 0xFF8F82BEL
        ThemePreset.LIGHT_CREAM -> 0xFF3F6B56L to 0xFF6FA98CL
        ThemePreset.LIGHT_MATCHA -> 0xFF546B45L to 0xFF7C9E6BL
        ThemePreset.CITY_SHENZHEN -> 0xFF1F6E5DL to 0xFF2E9C86L
        ThemePreset.CITY_SHANGHAI -> 0xFF35547FL to 0xFF4A72B8L
        ThemePreset.CITY_BEIJING -> 0xFF84552BL to 0xFFB4763CL
        ThemePreset.CITY_GUANGZHOU -> 0xFF96552FL to 0xFFD07A50L
        ThemePreset.CITY_HK -> 0xFF443E85L to 0xFF6A63C9L
        ThemePreset.CITY_MACAU -> 0xFF6B5F32L to 0xFF95854CL
        ThemePreset.CHINESE -> {
            val seed = readableSeed(
                CHINESE_COLORS[chineseSeed.coerceIn(0, CHINESE_COLORS.lastIndex)].seed
            )
            shadeColor(seed, 0.72f).toLong() to shadeColor(seed, 1.08f).toLong()
        }
        // 晴空蓝 / 跟随系统 / 动态取色 / 未知值兜底
        else -> 0xFF2B5F8FL to 0xFF5B8DB8L
    }

private fun gradientBitmap(startColor: Int, endColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 64f, 64f,
            intArrayOf(startColor, endColor), null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, 64f, 64f, paint)
    return bmp
}

/** 背景规格：所有小组件共用（0 跟随主题[含应用渐变预设] 1 自定义图片 2 纯色） */
private data class BgSpec(val mode: Int, val sc: Int, val ec: Int, val customBase: Bitmap?)

private suspend fun resolveBg(app: Context, s: com.inkledger.app.data.prefs.AppSettings): BgSpec {
    val themePair = themeGradient(s.theme, s.chineseSeedIndex)
    var sc = themePair.first
    var ec = themePair.second
    var customBase: Bitmap? = null
    when (s.widgetBgMode) {
        0 -> {
            val grad = if (s.bgGradient in com.inkledger.app.ui.theme.APP_GRADIENTS.indices)
                com.inkledger.app.ui.theme.APP_GRADIENTS[s.bgGradient] else null
            if (grad != null) {
                sc = grad[0].toArgb().toLong() and 0xFFFFFFFFL
                ec = grad[1].toArgb().toLong() and 0xFFFFFFFFL
            }
        }
        1 -> {
            customBase = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.inkledger.app.data.media.BackgroundManager.loadProcessed(
                    app, 320, s.widgetBgBlur, "widget_bg.jpg"
                )?.let { bmp ->
                    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    android.graphics.Canvas(out).drawRect(
                        0f, 0f, out.width.toFloat(), out.height.toFloat(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(
                                (255 * (0.22f + 0.28f * s.widgetBgBlur / 100f)).toInt(), 0, 0, 0
                            )
                        }
                    )
                    out
                }
            }
        }
        2 -> if (s.widgetBgColor != 0L) {
            sc = com.inkledger.app.ui.theme.shadeColor(s.widgetBgColor, 0.94f).toLong() and 0xFFFFFFFFL
            ec = com.inkledger.app.ui.theme.shadeColor(s.widgetBgColor, 1.06f).toLong() and 0xFFFFFFFFL
        }
    }
    return BgSpec(s.widgetBgMode, sc.toInt(), ec.toInt(), customBase)
}

/** 本期数据快照，三个布局共用一次读取 */
private data class WidgetData(
    val expense: Long,
    val income: Long,
    val currency: String,
    val recent: List<TransactionEntity>,
    val txCount: Int,
    val bgMode: Int,                // 0 跟随主题 1 自定义图片 2 纯色
    val startColor: Int,
    val endColor: Int,              // 主题渐变/纯色（纯色 sc==ec 微渐变）
    val customBase: Bitmap?         // 自定义图片底图（已模糊+压暗），按组件宽高比裁切
)

private suspend fun readWidgetData(context: Context): WidgetData {
    val app = context.applicationContext
    val s = SettingsRepository(app).flow.first()
    val all = AppDatabase.get(app).txDao().getAll()
    val periodStart = LocalDate.now().monthPeriodStart(s.monthStartDay)
    val month = all.filter { it.timestamp.toLocalDate() >= periodStart }
    val expense = month.filter { it.type == 0 }.sumOf { it.amountMinor }
    val income = month.filter { it.type == 1 }.sumOf { it.amountMinor }
    val recent = month.sortedByDescending { it.timestamp }.take(3)
    val bg = resolveBg(app, s)
    return WidgetData(
        expense, income, s.currency, recent, month.size,
        bg.mode, bg.sc, bg.ec, bg.customBase
    )
}

/** 按组件真实宽高比中心裁切（杜绝 fitXY 无脑拉伸），输出约 2px/dp 的清晰度 */
private fun cropToAspect(src: Bitmap, wDp: Int, hDp: Int): Bitmap {
    val targetAspect = wDp.toFloat() / hDp.coerceAtLeast(1)
    val sw = src.width.toFloat()
    val sh = src.height.toFloat()
    val srcAspect = sw / sh
    var cropW = sw
    var cropH = sh
    if (srcAspect > targetAspect) cropW = sh * targetAspect else cropH = sw / targetAspect
    val x = (sw - cropW) / 2f
    val y = (sh - cropH) / 2f
    val outW = (wDp * 2f).toInt().coerceIn(64, 640)
    val outH = (outW / targetAspect).toInt().coerceAtLeast(32)
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(
        src,
        android.graphics.Rect(x.toInt(), y.toInt(), (x + cropW).toInt(), (y + cropH).toInt()),
        android.graphics.Rect(0, 0, outW, outH),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    )
    return out
}

/** 每个 widgetId 用自己的 options（真实摆放尺寸）生成背景位图 */
private fun bgBitmapFor(mgr: AppWidgetManager, awid: Int, bg: BgSpec): Bitmap {
    if (bg.mode == 1 && bg.customBase != null) {
        val opts = mgr.getAppWidgetOptions(awid)
        val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        return cropToAspect(bg.customBase, wDp.coerceAtLeast(40), hDp.coerceAtLeast(40))
    }
    return gradientBitmap(bg.sc, bg.ec)
}

/** 原生 RemoteViews 渲染：不走 Glance 组合（在 MIUI 上排主线程队列会被冻结/延迟），binder 直写桌面立即生效 */
private fun buildViews(
    app: Context, appWidgetId: Int, layout: Int, d: WidgetData, bg: Bitmap
): RemoteViews {
    val layoutId = when (layout) {
        WIDGET_LAYOUT_SMALL -> R.layout.widget_small
        WIDGET_LAYOUT_MEDIUM -> R.layout.widget_medium
        else -> R.layout.widget_large
    }
    val views = RemoteViews(app.packageName, layoutId)
    views.setImageViewBitmap(R.id.widget_bg, bg)
    // 圆角：根布局背景 drawable 提供轮廓，clipToOutline 把渐变图/列表一起裁圆
    views.setBoolean(R.id.widget_root, "setClipToOutline", true)
    // 根点击进应用；＋ 按钮打开桌面快速记账
    val openApp = PendingIntent.getActivity(
        app, 1, Intent(app, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(R.id.widget_root, openApp)
    views.setOnClickPendingIntent(
        R.id.widget_btn,
        PendingIntent.getActivity(
            app, 0, Intent(app, QuickEntryActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
    views.setTextViewText(R.id.widget_value_expense, formatCompact(d.expense, d.currency))
    if (layout != WIDGET_LAYOUT_SMALL) {
        views.setTextViewText(R.id.widget_value_income, formatCompact(d.income, d.currency))
        // 记录列表：RemoteViewsService 适配器按需生成，ListView 原生可滑动
        views.setRemoteAdapter(appWidgetId, R.id.widget_list_view, Intent(app, WidgetListService::class.java))
        views.setEmptyView(R.id.widget_list_view, R.id.widget_list_empty)
        views.setPendingIntentTemplate(R.id.widget_list_view, openApp)
    }
    return views
}

abstract class BaseWidgetReceiver(private val layout: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (appWidgetIds.isNotEmpty()) {
                    val d = readWidgetData(context)
                    appWidgetIds.forEach { awid ->
                        appWidgetManager.updateAppWidget(
                            intArrayOf(awid),
                            buildViews(
                                context, awid, layout, d,
                                bgBitmapFor(appWidgetManager, awid, BgSpec(d.bgMode, d.startColor, d.endColor, d.customBase))
                            )
                        )
                    }
                    if (layout != WIDGET_LAYOUT_SMALL) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view)
                    }
                    android.util.Log.d("InkWidget", "onUpdate push layout=$layout ids=${appWidgetIds.size} exp=${d.expense}")
                }
            } catch (e: Exception) {
                android.util.Log.e("InkWidget", "onUpdate layout=$layout failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

class SmallWidgetReceiver : BaseWidgetReceiver(WIDGET_LAYOUT_SMALL)
class MediumWidgetReceiver : BaseWidgetReceiver(WIDGET_LAYOUT_MEDIUM)
class LargeWidgetReceiver : BaseWidgetReceiver(WIDGET_LAYOUT_LARGE)

/** 兼容旧版已放置的小组件：渲染为标准布局 */
class MonthWidgetReceiver : BaseWidgetReceiver(WIDGET_LAYOUT_MEDIUM)

/** 方形视觉背景：在组件实际矩形中央画一个圆角正方形（渐变或自定义图），四周透明（MIUI 格子非正方形的解法） */
private fun squareBgBitmap(wPx: Int, hPx: Int, bg: BgSpec, density: Float): Bitmap {
    val out = Bitmap.createBitmap(wPx.coerceAtLeast(8), hPx.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val margin = (3 * density).toInt()
    val side = (minOf(wPx, hPx) - margin * 2).coerceAtLeast(8)
    val l = (wPx - side) / 2f
    val t = (hPx - side) / 2f
    val radius = 18 * density   // 与收支组件同款圆角
    val rr = android.graphics.RectF(l, t, l + side, t + side)
    if (bg.mode == 1 && bg.customBase != null) {
        // 自定义图片：中心裁成方形铺进圆角框（底图已在 resolveBg 压暗过，不再叠加）
        val path = android.graphics.Path().apply { addRoundRect(rr, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val src = bg.customBase
        val cs = maxOf(side / src.width.toFloat(), side / src.height.toFloat())
        val dw = src.width * cs
        val dh = src.height * cs
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, android.graphics.RectF(l + (side - dw) / 2, t + (side - dh) / 2, l + (side + dw) / 2, t + (side + dh) / 2), paint)
        canvas.restore()
    } else {
        // 渐变方向与收支组件同款（对角线）
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(l, t, l + side, t + side, bg.sc, bg.ec, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rr, radius, radius, paint)
    }
    return out
}

/** 组件实际 px 尺寸（按放置时的 options 估算） */
private fun widgetPx(mgr: AppWidgetManager, awid: Int, app: Context): Pair<Int, Int> {
    val opts = mgr.getAppWidgetOptions(awid)
    val wDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
    val hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
    val density = app.resources.displayMetrics.density
    return Pair((wDp * density).toInt().coerceAtLeast(8), (hDp * density).toInt().coerceAtLeast(8))
}

// ── 倒数日小组件（2×2）：显示置顶/最近（可在添加时配置选具体某条） ──

private fun countdownTarget(c: com.inkledger.app.data.db.CountDownEntity, today: LocalDate): LocalDate {
    var t = LocalDate.ofEpochDay(c.targetEpochDay)
    if (c.repeatYearly) {
        var guard = 0
        while (t.isBefore(today) && guard < 200) { t = t.plusYears(1); guard++ }
    }
    return t
}

private fun openAppPi(app: Context): PendingIntent = PendingIntent.getActivity(
    app, 1, Intent(app, MainActivity::class.java),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

class CountdownWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { pushCountdownWidgets(context, appWidgetManager, appWidgetIds) }
            catch (e: Exception) { android.util.Log.e("InkWidget", "countdown onUpdate failed", e) }
            finally { pending.finish() }
        }
    }
}

private suspend fun pushCountdownWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray?) {
    val app = context.applicationContext
    val myIds = ids ?: mgr.getAppWidgetIds(ComponentName(app, CountdownWidgetReceiver::class.java)) ?: IntArray(0)
    if (myIds.isEmpty()) return
    val s = SettingsRepository(app).flow.first()
    val today = LocalDate.now()
    val cds = AppDatabase.get(app).countDownDao().observeAll().first()
    val bg = resolveBg(app, s)
    myIds.forEach { awid ->
        try {
            val pick = SettingsRepository(app).widgetCountdownPick(awid)
            // 选择优先 → 置顶 → 未来最近 → 最近
            val c = cds.firstOrNull { it.id == pick }
                ?: cds.firstOrNull { it.pinned }
                ?: cds.filter { countdownTarget(it, today).toEpochDay() >= today.toEpochDay() }
                    .minByOrNull { countdownTarget(it, today).toEpochDay() }
                ?: cds.minByOrNull { countdownTarget(it, today).toEpochDay() }
            val views = RemoteViews(app.packageName, R.layout.widget_countdown)
            // 正方形视觉：背景中央画方形 + 内容等比缩放（MIUI 格子非正方）
            val (wPx, hPx) = widgetPx(mgr, awid, app)
            views.setImageViewBitmap(R.id.widget_bg, squareBgBitmap(wPx, hPx, bg, app.resources.displayMetrics.density))
            val sq = (minOf(wPx, hPx) - 6 * app.resources.displayMetrics.density) / maxOf(wPx, hPx).toFloat()
            views.setFloat(R.id.widget_cd_content, "setScaleX", sq)
            views.setFloat(R.id.widget_cd_content, "setScaleY", sq)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPi(app))
            if (c == null) {
                views.setTextViewText(R.id.widget_cd_name, "还没有倒数日")
                views.setTextViewText(R.id.widget_cd_days, "—")
                views.setTextViewText(R.id.widget_cd_unit, "")
                views.setTextViewText(R.id.widget_cd_date, "去应用里添加一个吧")
            } else {
                val target = countdownTarget(c, today)
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, target)
                views.setTextViewText(R.id.widget_cd_name, c.name)
                views.setTextViewText(
                    R.id.widget_cd_days,
                    when { days == 0L -> "今天"; else -> days.toString() }
                )
                views.setTextViewText(
                    R.id.widget_cd_unit,
                    when { days > 0L -> "天"; days < 0L -> "天前"; else -> "" }
                )
                views.setTextViewText(
                    R.id.widget_cd_date,
                    target.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"))
                )
            }
            mgr.updateAppWidget(intArrayOf(awid), views)
        } catch (e: Exception) {
            android.util.Log.e("InkWidget", "pushCountdown #$awid failed", e)
        }
    }
}

// ── 打卡小组件（2×2）：单个任务 + 大打卡钮（可在添加时配置选哪个；桌面直接打卡） ──

class HabitWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { pushHabitWidgets(context, appWidgetManager, appWidgetIds) }
            catch (e: Exception) { android.util.Log.e("InkWidget", "habit onUpdate failed", e) }
            finally { pending.finish() }
        }
    }
}

/** 桌面打卡钮：广播 → 切换当天打卡 → 刷新全部小组件 */
class HabitWidgetCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getLongExtra("gid", 0L)
        if (goalId <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(context)
                val today = LocalDate.now().toEpochDay()
                if (db.goalDao().countCheck(goalId, today) > 0) db.goalDao().deleteCheck(goalId, today)
                else db.goalDao().insertCheck(
                    com.inkledger.app.data.db.HabitCheckEntity(goalId = goalId, epochDay = today)
                )
                WidgetSync.refreshAsync(context)
            } catch (e: Exception) {
                android.util.Log.e("InkWidget", "habit check failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

private suspend fun pushHabitWidgets(context: Context, mgr: AppWidgetManager, ids: IntArray?) {
    val app = context.applicationContext
    val myIds = ids ?: mgr.getAppWidgetIds(ComponentName(app, HabitWidgetReceiver::class.java)) ?: IntArray(0)
    if (myIds.isEmpty()) return
    val s = SettingsRepository(app).flow.first()
    val todayEpoch = LocalDate.now().toEpochDay()
    val goals = AppDatabase.get(app).goalDao().observeAll().first().filter { it.kind == 1 }
    val checksByGoal = AppDatabase.get(app).goalDao().observeAllChecks().first()
        .groupBy { it.goalId }.mapValues { en -> en.value.map { it.epochDay }.toSet() }
    val bg = resolveBg(app, s)
    myIds.forEach { awid ->
        try {
            val pick = SettingsRepository(app).widgetHabitPick(awid)
            val g = goals.firstOrNull { it.id == pick } ?: goals.firstOrNull()
            val views = RemoteViews(app.packageName, R.layout.widget_habit)
            val (wPx, hPx) = widgetPx(mgr, awid, app)
            views.setImageViewBitmap(R.id.widget_bg, squareBgBitmap(wPx, hPx, bg, app.resources.displayMetrics.density))
            val sq = (minOf(wPx, hPx) - 6 * app.resources.displayMetrics.density) / maxOf(wPx, hPx).toFloat()
            views.setFloat(R.id.widget_hb_content, "setScaleX", sq)
            views.setFloat(R.id.widget_hb_content, "setScaleY", sq)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPi(app))
            if (g == null) {
                views.setTextViewText(R.id.widget_hb_name, "还没有打卡习惯")
                views.setTextViewText(R.id.widget_hb_prefix, "去应用里添加一个吧")
                views.setTextViewText(R.id.widget_hb_days, "")
                views.setTextViewText(R.id.widget_hb_suffix, "")
                views.setTextViewText(R.id.widget_hb_check, "＋")
                views.setTextColor(R.id.widget_hb_check, 0xFFFFFFFF.toInt())
                views.setOnClickPendingIntent(R.id.widget_hb_btn, openAppPi(app))
            } else {
                val checks = checksByGoal[g.id] ?: emptySet()
                val checkedToday = todayEpoch in checks
                var streak = 0
                var d = if (checkedToday) LocalDate.now() else LocalDate.now().minusDays(1)
                while (d.toEpochDay() in checks) { streak++; d = d.minusDays(1) }
                val monthCount = checks.count { it >= LocalDate.now().withDayOfMonth(1).toEpochDay() }
                views.setTextViewText(R.id.widget_hb_name, g.name)
                // 天数单独放大（倒数日同款结构）：小字前缀 + 大数字 + 小字后缀
                when {
                    checkedToday -> {
                        views.setTextViewText(R.id.widget_hb_prefix, "🔥 连续")
                        views.setTextViewText(R.id.widget_hb_days, "$streak")
                        views.setTextViewText(R.id.widget_hb_suffix, "天")
                    }
                    streak > 0 -> {
                        views.setTextViewText(R.id.widget_hb_prefix, "连续")
                        views.setTextViewText(R.id.widget_hb_days, "$streak")
                        views.setTextViewText(R.id.widget_hb_suffix, "天")
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_hb_prefix, "点圆打卡")
                        views.setTextViewText(R.id.widget_hb_days, "")
                        views.setTextViewText(R.id.widget_hb_suffix, "")
                    }
                }
                views.setTextColor(
                    R.id.widget_hb_check,
                    if (checkedToday) 0xFFFFFFFF.toInt() else 0x73FFFFFF.toInt()
                )
                views.setOnClickPendingIntent(
                    R.id.widget_hb_btn,
                    PendingIntent.getBroadcast(
                        app, g.id.toInt(),
                        Intent(app, HabitWidgetCheckReceiver::class.java).putExtra("gid", g.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            mgr.updateAppWidget(intArrayOf(awid), views)
        } catch (e: Exception) {
            android.util.Log.e("InkWidget", "pushHabit #$awid failed", e)
        }
    }
}

/** 数据变化后刷新全部小组件 */
object WidgetSync {

    /** 进程级作用域：调用方（含即将 finish 的透明记账页）不会被取消 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 异步刷新，fire-and-forget：任何线程/生命周期都可安全调用 */
    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                updateAll(app)
            } catch (e: Exception) {
                android.util.Log.e("InkWidget", "refreshAsync failed", e)
            }
        }
    }

    /**
     * 刷新全部小组件（含旧版「收支」组件）。
     * 数据读取一次，然后按接收器查真实 widgetId，AppWidgetManager.updateAppWidget
     * 直接写桌面——同步 binder 调用，不经过广播队列、不依赖主线程，毫秒级生效。
     */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val d = readWidgetData(app)
        push(mgr, app, SmallWidgetReceiver::class.java, WIDGET_LAYOUT_SMALL, d)
        push(mgr, app, MediumWidgetReceiver::class.java, WIDGET_LAYOUT_MEDIUM, d)
        push(mgr, app, LargeWidgetReceiver::class.java, WIDGET_LAYOUT_LARGE, d)
        push(mgr, app, MonthWidgetReceiver::class.java, WIDGET_LAYOUT_MEDIUM, d)
        pushCountdownWidgets(app, mgr, null)
        pushHabitWidgets(app, mgr, null)
        android.util.Log.d("InkWidget", "updateAll done exp=${d.expense} inc=${d.income}")
    }

    private fun push(
        mgr: AppWidgetManager, app: Context, rc: Class<*>, layout: Int, d: WidgetData
    ) {
        try {
            val ids = mgr.getAppWidgetIds(ComponentName(app, rc)) ?: IntArray(0)
            if (ids.isNotEmpty()) {
                ids.forEach { awid ->
                    mgr.updateAppWidget(
                        intArrayOf(awid),
                        buildViews(app, awid, layout, d, bgBitmapFor(mgr, awid, BgSpec(d.bgMode, d.startColor, d.endColor, d.customBase)))
                    )
                }
                if (layout != WIDGET_LAYOUT_SMALL) {
                    mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list_view)
                }
                android.util.Log.d(
                    "InkWidget",
                    "push ${rc.simpleName} ids=${ids.size} exp=${d.expense} inc=${d.income} " +
                        "bgMode=${d.bgMode} sc=%08X ec=%08X".format(d.startColor, d.endColor)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("InkWidget", "push ${rc.simpleName} failed", e)
        }
    }
}

/** 记录列表适配器：本期最近 50 条，ListView 原生滑动 */
class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        data class Row(val amount: String, val isExpense: Boolean, val note: String, val time: String)
        private var rows: List<Row> = emptyList()

        override fun onCreate() {}
        override fun onDataSetChanged() {
            rows = kotlinx.coroutines.runBlocking { readListRows(ctx, 50) }
        }
        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewAt(position: Int): RemoteViews {
            val r = rows[position]
            val rv = RemoteViews(ctx.packageName, R.layout.widget_row)
            rv.setTextViewText(R.id.widget_row_amount, r.amount)
            rv.setTextColor(
                R.id.widget_row_amount,
                if (r.isExpense) 0xFFFFC2BC.toInt() else 0xFFBDF5D0.toInt()
            )
            rv.setTextViewText(R.id.widget_row_note, r.note)
            rv.setTextViewText(R.id.widget_row_time, r.time)
            // 行点击 → 填充进列表的 PendingIntentTemplate（打开应用）
            rv.setOnClickFillInIntent(R.id.widget_row, Intent())
            return rv
        }
    }
}

private suspend fun readListRows(context: Context, limit: Int): List<WidgetListService.Factory.Row> {
    val app = context.applicationContext
    val s = SettingsRepository(app).flow.first()
    val all = AppDatabase.get(app).txDao().getAll()
    val cats = AppDatabase.get(app).categoryDao().observeAll()
        .first().associateBy { it.id }
    val periodStart = LocalDate.now().monthPeriodStart(s.monthStartDay)
    val hm = java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm")
    return all.filter { it.timestamp.toLocalDate() >= periodStart }
        .sortedByDescending { it.timestamp }.take(limit)
        .map {
            WidgetListService.Factory.Row(
                (if (it.type == 0) "-" else "+") + formatCompact(it.amountMinor, s.currency),
                it.type == 0,
                // 名称与应用内一致：有备注显示备注，否则显示分类名（不再统一叫「记录」）
                it.note.ifBlank { cats[it.categoryId]?.name ?: "记录" },
                it.timestamp.toLocalDateTime().format(hm)
            )
        }
}
