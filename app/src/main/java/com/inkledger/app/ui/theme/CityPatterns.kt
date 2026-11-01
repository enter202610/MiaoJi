package com.inkledger.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.inkledger.app.R
import com.inkledger.app.data.prefs.ThemePreset

/**
 * 城市主题底图：真实地标照片提取的剪影轮廓（离线 PNG 素材）。
 * 以主题色低透明度铺在页面底部，像水印一样不碍眼，但一眼可辨是哪座城。
 */
@Composable
fun CityPattern(preset: ThemePreset, modifier: Modifier = Modifier) {
    val wash = CITY_WASHES[preset] ?: return
    val res = silhouetteRes(preset) ?: return
    Box(modifier) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            alignment = Alignment.BottomCenter,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(wash.skyline),
            alpha = 0.55f,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

/** 主题选择器 / 引导页里的迷你剪影预览。 */
@Composable
fun CitySilhouetteMini(
    preset: ThemePreset,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val res = silhouetteRes(preset) ?: return
    Image(
        painter = painterResource(res),
        contentDescription = null,
        alignment = Alignment.BottomCenter,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
    )
}

private fun silhouetteRes(preset: ThemePreset): Int? = when (preset) {
    ThemePreset.CITY_SHENZHEN -> R.drawable.sil_shenzhen
    ThemePreset.CITY_SHANGHAI -> R.drawable.sil_shanghai
    ThemePreset.CITY_BEIJING -> R.drawable.sil_beijing
    ThemePreset.CITY_GUANGZHOU -> R.drawable.sil_guangzhou
    ThemePreset.CITY_HK -> R.drawable.sil_hk
    ThemePreset.CITY_MACAU -> R.drawable.sil_macau
    else -> null
}
