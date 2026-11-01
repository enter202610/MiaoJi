package com.inkledger.app.ui.goals

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.inkledger.app.data.media.BackgroundManager
import com.inkledger.app.data.media.NotesMedia
import com.inkledger.app.ui.theme.APP_GRADIENTS
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.graphics.toArgb

/**
 * 倒数日纪念卡导出：Canvas 绘制 1080x1920 图片存入相册。
 * 版式与应用内页面完全同构：以 400dp 设计宽为基准等比放大 2.7 倍，
 * StaticLayout 精确堆叠、整块垂直居中，天数与「天」底对齐（含 14dp 上移）。
 */
object CountdownCardShot {

    private const val W = 1080
    private const val H = 1920

    fun save(
        context: Context,
        id: Long,
        emoji: String,
        name: String,
        targetDate: LocalDate,
        repeatYearly: Boolean,
        bgId: Int,
        bgBlur: Int
    ): Boolean {
        val bmp = render(context, id, emoji, name, targetDate, repeatYearly, bgId, bgBlur)
        val ok = NotesMedia.saveBitmapToGallery(
            context, bmp,
            "inkledger_card_" + System.currentTimeMillis() + ".jpg"
        )
        bmp.recycle()
        return ok
    }

    /** 设计基准：应用页按 400dp 宽布局，这里 1080px / 400dp = 2.7 倍等比 */
    private fun d(v: Float): Float = v * W / 400f

    private fun static(text: String, sizePx: Float, bold: Boolean = false, alpha: Int = 255): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            color = Color.WHITE
            this.alpha = alpha
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, tp, W)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()
    }

    private fun render(
        context: Context,
        id: Long,
        emoji: String,
        name: String,
        targetDate: LocalDate,
        repeatYearly: Boolean,
        bgId: Int,
        bgBlur: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawBackground(context, canvas, id, bgId, bgBlur)

        // 与页面一致的日期推进逻辑
        var target = targetDate
        val today = LocalDate.now()
        if (repeatYearly) {
            var guard = 0
            while (target.isBefore(today) && guard < 200) { target = target.plusYears(1); guard++ }
        }
        val days = ChronoUnit.DAYS.between(today, target)

        // 各文本块（与应用字号 64/24/16/88/24/14sp 等比）
        val emojiL = static(emoji, d(64f))
        val nameL = static(name, d(24f), bold = true)
        val labelL = static(if (days >= 0) "还有" else "已过", d(16f), alpha = 178)
        val dateText = target.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)) +
            if (repeatYearly) " · 每年重复" else ""
        val dateL = static(dateText, d(14f), alpha = 166)
        val todayL = if (days == 0L) static("🎉 就是今天", d(18f)) else null

        // 天数行：数字 88sp + 「天」24sp（底对齐，天 上移 14dp）
        val numTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = d(88f); color = Color.WHITE }
        val unitTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = d(24f); color = Color.WHITE }
        val numText = kotlin.math.abs(days).toString()
        val numW = numTp.measureText(numText)
        val unitW = unitTp.measureText("天")
        val numFm = numTp.fontMetrics
        val unitFm = unitTp.fontMetrics
        val numH = numFm.descent - numFm.ascent
        val unitH = unitFm.descent - unitFm.ascent
        val rowH = maxOf(numH, unitH + d(14f))

        // 整块垂直居中（对应页面 Column 居中 + 各 padding 10/26/10dp）
        var total = emojiL.height + d(10f) + nameL.height + d(26f) + labelL.height +
            d(6f) + rowH + d(10f) + dateL.height
        todayL?.let { total += d(8f) + it.height }
        var y = (H - total) / 2f

        fun drawL(l: StaticLayout, top: Float) {
            canvas.save()
            canvas.translate(0f, top)
            l.draw(canvas)
            canvas.restore()
        }

        drawL(emojiL, y); y += emojiL.height + d(10f)
        drawL(nameL, y); y += nameL.height + d(26f)
        drawL(labelL, y); y += labelL.height + d(6f)
        // 数字行
        val rowBottom = y + rowH
        val rowW = numW + d(8f) + unitW
        val x0 = (W - rowW) / 2f
        canvas.drawText(numText, x0, rowBottom - numFm.descent, numTp)
        canvas.drawText("天", x0 + numW + d(8f), rowBottom - d(14f) - unitFm.descent, unitTp)
        y += rowH + d(10f)
        drawL(dateL, y); y += dateL.height
        todayL?.let { y += d(8f); drawL(it, y) }

        // 底部落款
        val markTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = d(13f); color = Color.WHITE; alpha = 128; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("妙计 · 倒数日", W / 2f, H - d(30f), markTp)
        return bmp
    }

    private fun drawBackground(context: Context, canvas: Canvas, id: Long, bgId: Int, bgBlur: Int) {
        // 自定义照片：中心裁切 + 降采样模糊近似 + 压暗（与实时页一致的遮罩强度）
        if (bgId == -1) {
            val f: File? = BackgroundManager.customFile(context, "cd_${id}.jpg")
            if (f != null) {
                val src = NotesMedia.decode(f, 1080) ?: run { drawGradient(canvas, 0); return }
                val scale = maxOf(W.toFloat() / src.width, H.toFloat() / src.height)
                val dw = (src.width * scale).toInt().coerceAtLeast(1)
                val dh = (src.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(src, dw, dh, true)
                val xOff = (dw - W) / 2
                val yOff = (dh - H) / 2
                val shown = if (bgBlur > 0) cheapBlur(scaled) else scaled
                canvas.save()
                canvas.translate(-xOff.toFloat(), -yOff.toFloat())
                canvas.drawBitmap(shown, 0f, 0f, null)
                canvas.restore()
                canvas.drawColor(Color.argb(((0.2f + 0.35f * (bgBlur / 100f)) * 255).toInt(), 0, 0, 0))
                if (shown !== scaled) shown.recycle()
                if (scaled !== src) scaled.recycle()
                src.recycle()
                return
            }
        }
        drawGradient(canvas, bgId.coerceIn(0, APP_GRADIENTS.size - 1))
    }

    private fun drawGradient(canvas: Canvas, idx: Int) {
        val colors = APP_GRADIENTS[idx].map { it.toArgb() }.toIntArray()
        val shader = LinearGradient(0f, 0f, 0f, H.toFloat(), colors, null, Shader.TileMode.CLAMP)
        canvas.drawPaint(Paint().apply { this.shader = shader })
    }

    /** 缩小再放大的廉价高斯近似，纪念卡静态图足够 */
    private fun cheapBlur(src: Bitmap): Bitmap {
        val ratio = 14f
        val small = Bitmap.createScaledBitmap(
            src, (src.width / ratio).toInt().coerceAtLeast(1), (src.height / ratio).toInt().coerceAtLeast(1), true
        )
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }
}
