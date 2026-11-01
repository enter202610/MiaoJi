package com.inkledger.app.ui.theme

/**
 * 中国传统色：一种色相只选 1-2 个辨识度高的热门色，族与族之间色相差异明显。
 * 色板保留authentic原色（选色圆点展示原色）；应用为主题/组件背景时过亮色自动压深（readableSeed）。
 */
data class ChineseColor(val name: String, val seed: Long)

val CHINESE_COLORS = listOf(
    ChineseColor("胭脂", 0xFF9D2933),   // 深绯红
    ChineseColor("朱砂", 0xFFC8503C),   // 朱红
    ChineseColor("海棠红", 0xFFDB5A6B), // 粉
    ChineseColor("琥珀", 0xFFCA6924),   // 橙棕
    ChineseColor("秋香", 0xFFD9B611),   // 金黄（亮，应用时自动压深）
    ChineseColor("驼", 0xFFA0785A),     // 驼棕
    ChineseColor("竹青", 0xFF2D6A4F),   // 深绿
    ChineseColor("铜绿", 0xFF549688),   // 青绿
    ChineseColor("湖蓝", 0xFF2E7FA8),   // 湖蓝
    ChineseColor("靛青", 0xFF26558F),   // 靛蓝
    ChineseColor("紫棠", 0xFF5D3A9B),   // 紫
    ChineseColor("绛紫", 0xFF8C4356),   // 酒红紫
    ChineseColor("墨", 0xFF3A3A3A)      // 墨黑
)

/** 相对亮度（WCAG） */
private fun channelLum(argb: Long, shift: Int): Double {
    val s = ((argb shr shift) and 0xFF).toInt() / 255.0
    return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
}

/** 种子色过亮时按亮度比例压深到可读范围（白字可读、白底文字可读），深色原样返回 */
fun readableSeed(seed: Long): Long {
    val lum = 0.2126 * channelLum(seed, 16) + 0.7152 * channelLum(seed, 8) + 0.0722 * channelLum(seed, 0)
    if (lum <= 0.36) return seed
    val f = kotlin.math.sqrt(0.34 / lum).coerceAtMost(1.0)
    fun ch(shift: Int) = (((seed shr shift) and 0xFF).toInt() * f).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(255, ch(16), ch(8), ch(0)).toLong() and 0xFFFFFFFFL
}

/** 颜色加深（factor<1）或提亮（factor>1） */
fun shadeColor(c: Long, factor: Float): Int {
    fun ch(shift: Int): Int {
        val base = ((c shr shift) and 0xFF).toInt()
        val v = if (factor <= 1f) (base * factor).toInt()
        else base + ((255 - base) * (factor - 1f)).toInt()
        return v.coerceIn(0, 255)
    }
    return android.graphics.Color.argb(255, ch(16), ch(8), ch(0))
}
