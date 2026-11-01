package com.inkledger.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 四套低饱和 curated 主题 + 跟随系统。
 * 视觉压力小：低对比背景、柔和强调色、少量高饱和元素。
 */

val MintDark = darkColorScheme(
    background = Color(0xFF14171A),
    onBackground = Color(0xFFE2E5E8),
    surface = Color(0xFF1B1F23),
    onSurface = Color(0xFFDEE2E5),
    surfaceVariant = Color(0xFF2B323A),
    onSurfaceVariant = Color(0xFFB4BBC2),
    primary = Color(0xFF8FD6B4),
    onPrimary = Color(0xFF10241A),
    primaryContainer = Color(0xFF1F3D33),
    onPrimaryContainer = Color(0xFFBDEBD6),
    secondary = Color(0xFF9FB3AA),
    onSecondary = Color(0xFF17251F),
    secondaryContainer = Color(0xFF27332E),
    onSecondaryContainer = Color(0xFFC3D4CC),
    error = Color(0xFFE58C8C),
    onError = Color(0xFF2B1212),
    outline = Color(0xFF4B545C)
)

val AmberDark = darkColorScheme(
    background = Color(0xFF1B1713),
    onBackground = Color(0xFFEAE3DC),
    surface = Color(0xFF221E19),
    onSurface = Color(0xFFE5DED6),
    surfaceVariant = Color(0xFF352D26),
    onSurfaceVariant = Color(0xFFBDB3A7),
    primary = Color(0xFFE2B382),
    onPrimary = Color(0xFF241708),
    primaryContainer = Color(0xFF46351F),
    onPrimaryContainer = Color(0xFFF2D4AE),
    secondary = Color(0xFFB0A48F),
    onSecondary = Color(0xFF211D14),
    secondaryContainer = Color(0xFF332E24),
    onSecondaryContainer = Color(0xFFD6CCB8),
    error = Color(0xFFE58C8C),
    onError = Color(0xFF2B1212),
    outline = Color(0xFF575046)
)

val LavenderLight = lightColorScheme(
    background = Color(0xFFF6F4F9),
    onBackground = Color(0xFF2D2A36),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2D2A36),
    surfaceVariant = Color(0xFFEFECEF),
    onSurfaceVariant = Color(0xFF6E6A7A),
    primary = Color(0xFF8F82BE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E0F5),
    onPrimaryContainer = Color(0xFF3D3260),
    secondary = Color(0xFF8D8A99),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E1E8),
    onSecondaryContainer = Color(0xFF3B3947),
    error = Color(0xFFC96A6A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD5D1DC)
)

val CreamLight = lightColorScheme(
    background = Color(0xFFFAF6F0),
    onBackground = Color(0xFF34312B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF34312B),
    surfaceVariant = Color(0xFFF1EAE0),
    onSurfaceVariant = Color(0xFF7A7566),
    primary = Color(0xFF6FA98C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFEEE5),
    onPrimaryContainer = Color(0xFF29503C),
    secondary = Color(0xFF97927F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAE6D9),
    onSecondaryContainer = Color(0xFF3B382D),
    error = Color(0xFFC96A6A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFDED7C9)
)

val BlueDark = darkColorScheme(
    background = Color(0xFF10151C),
    onBackground = Color(0xFFE0E7ED),
    surface = Color(0xFF182029),
    onSurface = Color(0xFFDCE5EC),
    surfaceVariant = Color(0xFF293543),
    onSurfaceVariant = Color(0xFFAFBECC),
    primary = Color(0xFF7FB5E8),
    onPrimary = Color(0xFF0C1A26),
    primaryContainer = Color(0xFF1D3A55),
    onPrimaryContainer = Color(0xFFB8D9F5),
    secondary = Color(0xFF9DB0C0),
    onSecondary = Color(0xFF14202B),
    secondaryContainer = Color(0xFF22303D),
    onSecondaryContainer = Color(0xFFC2D3E0),
    error = Color(0xFFE58C8C),
    onError = Color(0xFF2B1212),
    outline = Color(0xFF475360)
)

val SakuraDark = darkColorScheme(
    background = Color(0xFF1C151A),
    onBackground = Color(0xFFEDE2E8),
    surface = Color(0xFF241B21),
    onSurface = Color(0xFFE7DCE2),
    surfaceVariant = Color(0xFF372B34),
    onSurfaceVariant = Color(0xFFC2B0BC),
    primary = Color(0xFFE8A7C3),
    onPrimary = Color(0xFF2A1220),
    primaryContainer = Color(0xFF4E2E3E),
    onPrimaryContainer = Color(0xFFF5C9DC),
    secondary = Color(0xFFB8A3B1),
    onSecondary = Color(0xFF241722),
    secondaryContainer = Color(0xFF332531),
    onSecondaryContainer = Color(0xFFDAC7D3),
    error = Color(0xFFE58C8C),
    onError = Color(0xFF2B1212),
    outline = Color(0xFF534651)
)

val SkyLight = lightColorScheme(
    background = Color(0xFFF2F6FA),
    onBackground = Color(0xFF2B333C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B333C),
    surfaceVariant = Color(0xFFE4EDF5),
    onSurfaceVariant = Color(0xFF67747F),
    primary = Color(0xFF5B8DB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE9F5),
    onPrimaryContainer = Color(0xFF2A4A66),
    secondary = Color(0xFF8494A3),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E9F1),
    onSecondaryContainer = Color(0xFF39485A),
    error = Color(0xFFC96A6A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFD4E0EA)
)

val MatchaLight = lightColorScheme(
    background = Color(0xFFF5F8F2),
    onBackground = Color(0xFF303729),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF303729),
    surfaceVariant = Color(0xFFE8F0E2),
    onSurfaceVariant = Color(0xFF707965),
    primary = Color(0xFF7C9E6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EED9),
    onPrimaryContainer = Color(0xFF3D5230),
    secondary = Color(0xFF93987E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EDDD),
    onSecondaryContainer = Color(0xFF3A4030),
    error = Color(0xFFC96A6A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFDCE4D2)
)

/** 主题相关的扩展色：收入 / 支出 / 进度轨道 */
data class InkExtras(
    val income: Color,
    val expense: Color,
    val track: Color
)

private val MintDarkExtras = InkExtras(
    income = Color(0xFF8FD6B4),
    expense = Color(0xFFE39A9A),
    track = Color(0xFF363D44)
)
private val AmberDarkExtras = InkExtras(
    income = Color(0xFFE2B382),
    expense = Color(0xFFE39A9A),
    track = Color(0xFF3F372F)
)
private val LavenderExtras = InkExtras(
    income = Color(0xFF8F82BE),
    expense = Color(0xFFC96A6A),
    track = Color(0xFFE9E5EC)
)
private val CreamExtras = InkExtras(
    income = Color(0xFF6FA98C),
    expense = Color(0xFFC96A6A),
    track = Color(0xFFEDE5D8)
)

private val BlueDarkExtras = InkExtras(
    income = Color(0xFF7FB5E8),
    expense = Color(0xFFE39A9A),
    track = Color(0xFF2C3742)
)

private val SakuraDarkExtras = InkExtras(
    income = Color(0xFFE8A7C3),
    expense = Color(0xFFE39A9A),
    track = Color(0xFF382C34)
)

private val SkyExtras = InkExtras(
    income = Color(0xFF5B8DB8),
    expense = Color(0xFFC96A6A),
    track = Color(0xFFE3EBF2)
)

private val MatchaExtras = InkExtras(
    income = Color(0xFF7C9E6B),
    expense = Color(0xFFC96A6A),
    track = Color(0xFFE7EFE0)
)

val LocalInkExtras = staticCompositionLocalOf { MintDarkExtras }

// ---------- 城市浅色主题 ----------
val ShenzhenLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFF3FAF7), onBackground = Color(0xFF26332E), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF26332E), surfaceVariant = Color(0xFFE3F1EA), onSurfaceVariant = Color(0xFF64786F), primary = Color(0xFF2E9C86), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD6F0E7), onPrimaryContainer = Color(0xFF0F4A3D), secondary = Color(0xFF7FA79B), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE2EFE9), onSecondaryContainer = Color(0xFF2C4038), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFD5E6DD))
val ShanghaiLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFF3F6FB), onBackground = Color(0xFF282E3B), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF282E3B), surfaceVariant = Color(0xFFE4EAF4), onSurfaceVariant = Color(0xFF687183), primary = Color(0xFF4A72B8), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFD9E4F6), onPrimaryContainer = Color(0xFF1F3D6E), secondary = Color(0xFF8390AE), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE3E8F2), onSecondaryContainer = Color(0xFF333E55), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFD6DFEE))
val BeijingLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFFAF5EF), onBackground = Color(0xFF362F27), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF362F27), surfaceVariant = Color(0xFFF1E7D9), onSurfaceVariant = Color(0xFF79695A), primary = Color(0xFFB4763C), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFF5E3CE), onPrimaryContainer = Color(0xFF5C3A14), secondary = Color(0xFFA98F74), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFF0E6D6), onSecondaryContainer = Color(0xFF443A2C), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFEADCC7))
val GzLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFFBF5F1), onBackground = Color(0xFF352C29), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF352C29), surfaceVariant = Color(0xFFF3E5DC), onSurfaceVariant = Color(0xFF786A61), primary = Color(0xFFD07A50), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFF8DFCF), onPrimaryContainer = Color(0xFF66301A), secondary = Color(0xFFAE8E7C), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFF2E3D8), onSecondaryContainer = Color(0xFF453730), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFEDD9CB))
val HkLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFF3F2FB), onBackground = Color(0xFF2B2A3D), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF2B2A3D), surfaceVariant = Color(0xFFE7E4F4), onSurfaceVariant = Color(0xFF6B6883), primary = Color(0xFF6A63C9), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFDFDCF7), onPrimaryContainer = Color(0xFF37307E), secondary = Color(0xFF8E8AA8), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE6E3F1), onSecondaryContainer = Color(0xFF3B3850), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFDAD6EC))
val MacauLight = androidx.compose.material3.lightColorScheme(background = Color(0xFFFAF8F1), onBackground = Color(0xFF333226), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF333226), surfaceVariant = Color(0xFFF0EBD8), onSurfaceVariant = Color(0xFF75725C), primary = Color(0xFF95854C), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFEEE7CB), onPrimaryContainer = Color(0xFF4B4322), secondary = Color(0xFFA29B7E), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFEDE9D6), onSecondaryContainer = Color(0xFF3E3B2B), error = Color(0xFFC96A6A), onError = Color(0xFFFFFFFF), outline = Color(0xFFE6E0C8))

private val ShenzhenExtras = InkExtras(income = Color(0xFF2E9C86), expense = Color(0xFFC96A6A), track = Color(0xFFE0EEE6))
private val ShanghaiExtras = InkExtras(income = Color(0xFF4A72B8), expense = Color(0xFFC96A6A), track = Color(0xFFE1E8F3))
private val BeijingExtras = InkExtras(income = Color(0xFFB4763C), expense = Color(0xFFC96A6A), track = Color(0xFFEEE3D2))
private val GzExtras = InkExtras(income = Color(0xFFD07A50), expense = Color(0xFFC96A6A), track = Color(0xFFF0E2D8))
private val HkExtras = InkExtras(income = Color(0xFF6A63C9), expense = Color(0xFFC96A6A), track = Color(0xFFE4E1F1))
private val MacauExtras = InkExtras(income = Color(0xFF95854C), expense = Color(0xFFC96A6A), track = Color(0xFFEBE7D2))

/** 城市主题的淡雅元素：顶部两团极淡色斑 + 底部天际线剪影色（不打扰、仅氛围） */
data class CityWash(val a: Color, val b: Color, val skyline: Color)

val CITY_WASHES: Map<com.inkledger.app.data.prefs.ThemePreset, CityWash> = mapOf(
    com.inkledger.app.data.prefs.ThemePreset.CITY_SHENZHEN to CityWash(Color(0x1E26A69A), Color(0x161E6B8A), Color(0x3C2E9C86)),
    com.inkledger.app.data.prefs.ThemePreset.CITY_SHANGHAI to CityWash(Color(0x1E5B8DB8), Color(0x168F82BE), Color(0x3C4A72B8)),
    com.inkledger.app.data.prefs.ThemePreset.CITY_BEIJING to CityWash(Color(0x1EC98A5A), Color(0x16A8654F), Color(0x3CB4763C)),
    com.inkledger.app.data.prefs.ThemePreset.CITY_GUANGZHOU to CityWash(Color(0x1EE89A6B), Color(0x16D66E8A), Color(0x3CD07A50)),
    com.inkledger.app.data.prefs.ThemePreset.CITY_HK to CityWash(Color(0x1E6B7FD4), Color(0x16A96BC4), Color(0x3C6A63C9)),
    com.inkledger.app.data.prefs.ThemePreset.CITY_MACAU to CityWash(Color(0x1ED8B26A), Color(0x166BA88A), Color(0x3C95854C))
)

// 全屏渐变背景预置（12 款，2025-2026 流行配色）
val APP_GRADIENTS = listOf(
    listOf(Color(0xFF667eea), Color(0xFF764ba2)),   // 极光紫
    listOf(Color(0xFFf093fb), Color(0xFFf5576c)),   // 蜜桃粉
    listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),   // 天空青
    listOf(Color(0xFF43e97b), Color(0xFF38f9d7)),   // 薄荷绿
    listOf(Color(0xFFfa709a), Color(0xFFfee140)),   // 日落金粉
    listOf(Color(0xFFa8edea), Color(0xFFfed6e3)),   // 樱花雾
    listOf(Color(0xFF30cfd0), Color(0xFF330867)),   // 深海极光
    listOf(Color(0xFFfdfcfb), Color(0xFFe2d1c3)),   // 暖沙白
    listOf(Color(0xFFff9a9e), Color(0xFFfecfef)),   // 珊瑚粉
    listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb)),   // 薰衣草
    listOf(Color(0xFFffecd2), Color(0xFFfcb69f)),   // 落日橙
    listOf(Color(0xFF84fab0), Color(0xFF8fd3f4))    // 翡翠蓝
)

fun extrasFor(preset: com.inkledger.app.data.prefs.ThemePreset, dark: Boolean): InkExtras {
    val p = if (preset == com.inkledger.app.data.prefs.ThemePreset.LIGHT_SKY ||
        preset == com.inkledger.app.data.prefs.ThemePreset.LIGHT_LAVENDER ||
        preset == com.inkledger.app.data.prefs.ThemePreset.LIGHT_CREAM ||
        preset == com.inkledger.app.data.prefs.ThemePreset.LIGHT_MATCHA ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_SHENZHEN ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_SHANGHAI ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_BEIJING ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_GUANGZHOU ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_HK ||
        preset == com.inkledger.app.data.prefs.ThemePreset.CITY_MACAU
    ) preset else com.inkledger.app.data.prefs.ThemePreset.LIGHT_SKY
    return when (p) {
        com.inkledger.app.data.prefs.ThemePreset.LIGHT_LAVENDER -> LavenderExtras
        com.inkledger.app.data.prefs.ThemePreset.LIGHT_CREAM -> CreamExtras
        com.inkledger.app.data.prefs.ThemePreset.LIGHT_MATCHA -> MatchaExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_SHENZHEN -> ShenzhenExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_SHANGHAI -> ShanghaiExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_BEIJING -> BeijingExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_GUANGZHOU -> GzExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_HK -> HkExtras
        com.inkledger.app.data.prefs.ThemePreset.CITY_MACAU -> MacauExtras
        else -> SkyExtras
    }
}
