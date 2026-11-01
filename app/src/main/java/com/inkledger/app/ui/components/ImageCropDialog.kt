package com.inkledger.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 壁纸式裁切：双指缩放/单指拖动确定取景（所见即所得），
 * 「应用」时按当前视口裁切原图（无损于源图分辨率）交给 saveTo 保存。
 *
 * aspect 非空（如小组件 2.1:1）时为「框视口」模式：
 * 照片按铺满横屏裁切框显示，在框内拖动/缩放，框外区域压暗，应用时只取框内画面——
 * 原图不会被先裁成竖条。
 */
@Composable
fun ImageCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    saveTo: (Bitmap) -> Boolean,
    aspect: Float? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bmp by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }

    val vw = context.resources.displayMetrics.widthPixels.toFloat()
    val vh = context.resources.displayMetrics.heightPixels.toFloat()

    // 横屏框几何（aspect 非空时用）
    val frameW: Float
    val frameH: Float
    val frameL: Float
    val frameT: Float
    if (aspect != null) {
        var fw = vw - 140f
        var fh = fw / aspect
        if (fh > vh * 0.55f) { fh = vh * 0.55f; fw = fh * aspect }
        frameW = fw; frameH = fh
        frameL = (vw - fw) / 2f
        frameT = (vh - fh) / 2f - 90f
    } else {
        frameW = vw; frameH = vh; frameL = 0f; frameT = 0f
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF5090B0E))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 8f)
                        ox += pan.x
                        oy += pan.y
                    }
                }
        ) {
            val b = bmp
            if (b != null) {
                if (aspect != null) {
                    // 框视口模式（小组件）：先完整展示原图（Fit），用户缩放/拖动构图，
                    // 框外压暗；应用时输出=框内 ∩ 图片内容
                    Image(
                        b.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(frameL.roundToInt(), frameT.roundToInt()) }
                            .size(with(density) { frameW.toDp() }, with(density) { frameH.toDp() })
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = ox
                                translationY = oy
                            },
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // 全屏模式（应用背景/倒数日壁纸）：所见即所得，铺满展示
                    Image(
                        b.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = ox
                                translationY = oy
                            },
                        contentScale = ContentScale.Crop
                    )
                    // 外围选取框：白描边标出取景范围
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 16f
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }
                }
            }
            if (aspect != null) {
                // 框外压暗：四块矩形拼出框外区域（不用 BlendMode.Clear——部分设备上会整层变黑）
                Canvas(Modifier.fillMaxSize()) {
                    val sc = Color.Black.copy(alpha = 0.5f)
                    drawRect(sc, size = androidx.compose.ui.geometry.Size(size.width, frameT))
                    drawRect(
                        sc,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, frameT + frameH),
                        size = androidx.compose.ui.geometry.Size(
                            size.width,
                            (size.height - frameT - frameH).coerceAtLeast(0f)
                        )
                    )
                    drawRect(
                        sc,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, frameT),
                        size = androidx.compose.ui.geometry.Size(frameL, frameH)
                    )
                    drawRect(
                        sc,
                        topLeft = androidx.compose.ui.geometry.Offset(frameL + frameW, frameT),
                        size = androidx.compose.ui.geometry.Size(
                            (size.width - frameL - frameW).coerceAtLeast(0f),
                            frameH
                        )
                    )
                    drawRect(
                        Color.White.copy(alpha = 0.85f),
                        topLeft = androidx.compose.ui.geometry.Offset(frameL, frameT),
                        size = androidx.compose.ui.geometry.Size(frameW, frameH),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }
            }
            Text(
                if (aspect != null) "双指缩放 · 单指拖动取景（横向比例）" else "双指缩放 · 单指拖动取景",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 46.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.clickable(onClick = onDismiss)
                ) {
                    Text(
                        "  取消  ",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .clickable {
                            val img = bmp ?: return@clickable
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    if (aspect != null) {
                                        // 框视口：照片以 Fit 展示，输出=框内 ∩ 图片内容（多余部分下游自动居中裁齐）
                                        val cs2 = minOf(frameW / img.width, frameH / img.height)
                                        val total = cs2 * scale
                                        val originX = frameL + (frameW - img.width * cs2) / 2f + ox
                                        val originY = frameT + (frameH - img.height * cs2) / 2f + oy
                                        fun sx(x: Float) = ((x - originX) / total).toInt()
                                        fun sy(y: Float) = ((y - originY) / total).toInt()
                                        val l = sx(frameL).coerceIn(0, img.width - 8)
                                        val t = sy(frameT).coerceIn(0, img.height - 8)
                                        val r = sx(frameL + frameW).coerceIn(l + 8, img.width)
                                        val bt = sy(frameT + frameH).coerceIn(t + 8, img.height)
                                        runCatching {
                                            Bitmap.createBitmap(img, l, t, r - l, bt - t)
                                        }.getOrNull()?.let { saveTo(it) } ?: false
                                    } else {
                                        val cs = maxOf(vw / img.width, vh / img.height)
                                        val total = cs * scale
                                        fun sx(x: Float) = ((x - (vw - img.width * total) / 2f - ox) / total).toInt()
                                        fun sy(y: Float) = ((y - (vh - img.height * total) / 2f - oy) / total).toInt()
                                        val l = sx(0f).coerceIn(0, img.width - 8)
                                        val t = sy(0f).coerceIn(0, img.height - 8)
                                        val r = sx(vw).coerceIn(l + 8, img.width)
                                        val bt = sy(vh).coerceIn(t + 8, img.height)
                                        runCatching {
                                            Bitmap.createBitmap(img, l, t, r - l, bt - t)
                                        }.getOrNull()?.let { saveTo(it) } ?: false
                                    }
                                }
                                if (ok) onDismiss()
                            }
                        }
                ) {
                    Text(
                        "  应用为背景  ",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
