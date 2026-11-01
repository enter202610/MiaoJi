package com.inkledger.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * 自定义应用背景：用户选图 → 降采样存私有目录；
 * 展示时"缩到 40px 再放大"= 双线性自然高斯模糊（零运行时成本，全版本可用），叠加压暗遮罩。
 */
object BackgroundManager {

    private const val FILE_NAME = "app_bg.jpg"

    fun bgFile(context: Context): File? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }

    fun customFile(context: Context, name: String): File? =
        File(context.filesDir, name).takeIf { it.exists() }

    fun setFromUri(context: Context, uri: Uri, fileName: String = FILE_NAME): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while ((bounds.outWidth / sample) > 1080) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        File(context.filesDir, fileName).outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
        bmp.recycle()
        true
    } catch (e: Exception) {
        false
    }

    fun clear(context: Context) {
        bgFile(context)?.delete()
    }

    /** 保存裁切后的位图（JPEG 92，保留质量） */
    fun saveBitmap(context: Context, bmp: Bitmap, fileName: String = FILE_NAME): Boolean = try {
        java.io.File(context.filesDir, fileName).outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        true
    } catch (e: Exception) {
        false
    }

    /** 无级模糊：blurPct 0-100。0=原图清晰（1080 解码），100=深模糊；中间连续可调 */
    fun loadProcessed(context: Context, screenW: Int, blurPct: Int, fileName: String = FILE_NAME): Bitmap? {
        val f = customFile(context, fileName) ?: return null
        return blurFile(f, screenW, blurPct)
    }

    fun blurFile(f: File, screenW: Int, blurPct: Int = 100): Bitmap? {
        if (blurPct <= 2) return NotesMedia.decode(f, 1080)
        val tinyDim = (420 - 4.0 * blurPct).toInt().coerceIn(14, 420)
        val tiny = NotesMedia.decode(f, tinyDim) ?: return null
        val w = screenW.coerceAtLeast(360)
        val h = (w.toLong() * tiny.height / tiny.width.coerceAtLeast(1)).toInt().coerceAtLeast(360)
        return Bitmap.createScaledBitmap(tiny, w, h, true)
    }
}
