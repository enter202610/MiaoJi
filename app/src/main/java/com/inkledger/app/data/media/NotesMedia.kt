package com.inkledger.app.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** 记事图片的本地存储：全部保存在应用私有目录 notes_media/ 下，卸载即清理 */
object NotesMedia {

    fun dir(context: Context): File =
        File(context.filesDir, "notes_media").apply { mkdirs() }

    fun newCameraFile(context: Context): File =
        File(dir(context), "img_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** 从系统相册选择后，把内容拷贝进应用私有目录，返回文件名 */
    fun copyFromUri(context: Context, uri: Uri): String? = try {
        val ext = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val name = "img_${System.currentTimeMillis()}.$ext"
        val out = File(dir(context), name)
        val ok = context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { input.copyTo(it); true }
        } ?: false
        if (ok) name else null
    } catch (e: Exception) {
        null
    }

    fun file(context: Context, name: String): File? {
        if (name.isBlank() || name.contains("..") || name.contains('/') || name.contains('\\')) return null
        val f = File(dir(context), name)
        return if (f.exists()) f else null
    }

    /** 降采样解码，避免大图撑爆内存 */
    fun decode(file: File, maxDim: Int = 1024): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (e: Exception) {
        null
    }

    fun delete(context: Context, name: String) {
        file(context, name)?.delete()
    }

    /** 保存到系统相册（Android 10+，免权限） */
    fun saveToGallery(context: Context, name: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val f = file(context, name) ?: return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, f.name)
                put(
                    MediaStore.Images.Media.MIME_TYPE,
                    if (f.extension == "png") "image/png" else "image/jpeg"
                )
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/妙计")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { input -> input.copyTo(out) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 把内存位图直接存入系统相册（Android 10+ 免权限；老系统落到应用私有目录） */
    fun saveBitmapToGallery(context: Context, bmp: Bitmap, name: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/妙计")
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                } != null
            } else {
                val f = File(dir(context), name)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
