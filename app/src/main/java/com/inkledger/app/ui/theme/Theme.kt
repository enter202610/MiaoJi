package com.inkledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.inkledger.app.data.prefs.ThemePreset

val InkTypography = Typography()

/** 金额大字：等宽数字、中粗，避免视觉压迫 */
val AmountLargeStyle = TextStyle(
    fontSize = 38.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum"
)

val AmountMediumStyle = TextStyle(
    fontSize = 22.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum"
)

val AmountBodyStyle = TextStyle(
    fontSize = 15.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum"
)

val DaysBigStyle = TextStyle(
    fontSize = 30.sp,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum"
)

@Composable
fun InkTheme(preset: ThemePreset, chineseSeed: Int = 0, content: @Composable () -> Unit) {
    val dark = when (preset) {
        ThemePreset.SYSTEM -> isSystemInDarkTheme()
        else -> false   // 全浅色体系
    }
    val p = if (preset == ThemePreset.SYSTEM) ThemePreset.LIGHT_SKY else preset
    val context = androidx.compose.ui.platform.LocalContext.current

    // 动态取色（Android 12+）：主题色跟随系统壁纸自动生成
    val dynamicScheme = if (p == ThemePreset.THEME_DYNAMIC &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    ) {
        androidx.compose.material3.dynamicLightColorScheme(context)
    } else null

    // 中国传统色主题：整皮换色——背景/文字/容器全部由种子色派生（与晴空蓝等预设同级）
    // 过亮的种子（金/粉）先压深到可读亮度，保证白字与白底文字对比度
    val chineseScheme = if (p == ThemePreset.CHINESE) {
        val seed = Color(readableSeed(CHINESE_COLORS[chineseSeed.coerceIn(0, CHINESE_COLORS.lastIndex)].seed))
        androidx.compose.material3.lightColorScheme(
            primary = seed,
            onPrimary = Color.White,
            primaryContainer = seed.copy(alpha = 0.22f).compositeOver(Color.White),
            onPrimaryContainer = seed.darken(0.55f),
            secondary = seed.darken(0.75f),
            onSecondary = Color.White,
            secondaryContainer = seed.copy(alpha = 0.14f).compositeOver(Color.White),
            onSecondaryContainer = seed.darken(0.55f),
            background = seed.copy(alpha = 0.08f).compositeOver(Color(0xFFFEFEFD)),
            onBackground = seed.darken(0.30f),
            surface = Color.White,
            onSurface = seed.darken(0.30f),
            surfaceVariant = seed.copy(alpha = 0.14f).compositeOver(Color.White),
            onSurfaceVariant = seed.darken(0.50f),
            outline = seed.copy(alpha = 0.45f).compositeOver(Color.White)
        )
    } else null

    val scheme = dynamicScheme ?: chineseScheme ?: when (p) {
        ThemePreset.LIGHT_LAVENDER -> LavenderLight
        ThemePreset.LIGHT_CREAM -> CreamLight
        ThemePreset.LIGHT_SKY -> SkyLight
        ThemePreset.LIGHT_MATCHA -> MatchaLight
        ThemePreset.CITY_SHENZHEN -> ShenzhenLight
        ThemePreset.CITY_SHANGHAI -> ShanghaiLight
        ThemePreset.CITY_BEIJING -> BeijingLight
        ThemePreset.CITY_GUANGZHOU -> GzLight
        ThemePreset.CITY_HK -> HkLight
        ThemePreset.CITY_MACAU -> MacauLight
        else -> SkyLight
    }
    // 语义色：动态模式跟随壁纸；中国色模式进度条用种子色浅纱；其余走预设
    val extras = when {
        dynamicScheme != null -> InkExtras(
            income = dynamicScheme.primary,
            expense = dynamicScheme.error,
            track = dynamicScheme.primaryContainer
        )
        p == ThemePreset.CHINESE -> {
            val seed = Color(readableSeed(CHINESE_COLORS[chineseSeed.coerceIn(0, CHINESE_COLORS.lastIndex)].seed))
            InkExtras(
                income = Color(0xFF2E9C86),
                expense = Color(0xFFC96A6A),
                track = seed.copy(alpha = 0.18f).compositeOver(Color.White)
            )
        }
        else -> extrasFor(p, dark)
    }

    CompositionLocalProvider(LocalInkExtras provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = InkTypography,
            content = content
        )
    }
}

/** 颜色加深（factor<1） */
private fun Color.darken(factor: Float): Color = copy(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f)
)
