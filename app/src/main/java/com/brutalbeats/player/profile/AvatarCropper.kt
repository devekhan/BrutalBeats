package com.brutalbeats.player.profile

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalSlider
import com.brutalbeats.player.ui.components.hardShadow
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import android.graphics.Canvas as AndroidCanvas

/**
 * Full-screen pan/zoom circular cropper. Drag to move, pinch (or use the slider) to zoom.
 * [onDone] receives a square 512x512 picture that is shown circular everywhere in the app.
 */
@Composable
fun AvatarCropper(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onDone: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Brutal.colors
    BackHandler(onBack = onCancel)

    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrutalHeading("Crop photo", style = BrutalType.h1, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            "Drag to move. Pinch or use the slider to zoom.",
            style = BrutalType.body,
            color = c.textMuted,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val side = minOf(maxWidth - 8.dp, 340.dp)
            val sidePx = with(density) { side.toPx() }
            val insetPx = with(density) { 8.dp.toPx() }
            val radius = sidePx / 2f - insetPx
            val base = sidePx / minOf(bitmap.width, bitmap.height).toFloat()

            fun clamp(off: Offset, z: Float): Offset {
                val total = base * z
                val mx = maxOf(0f, (bitmap.width * total - sidePx) / 2f)
                val my = maxOf(0f, (bitmap.height * total - sidePx) / 2f)
                return Offset(off.x.coerceIn(-mx, mx), off.y.coerceIn(-my, my))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(side)
                        .hardShadow(6.dp, c.ink)
                        .border(Brutal.dims.border, c.ink)
                        .clipToBounds()
                        .pointerInput(sidePx) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val z = (zoom * gestureZoom).coerceIn(1f, 5f)
                                zoom = z
                                offset = clamp(offset + pan, z)
                            }
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val total = base * zoom
                        withTransform({
                            translate(size.width / 2f + offset.x, size.height / 2f + offset.y)
                            scale(total, total, Offset.Zero)
                            translate(-bitmap.width / 2f, -bitmap.height / 2f)
                        }) {
                            drawImage(imageBitmap, filterQuality = FilterQuality.Medium)
                        }
                        val hole = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(Rect(center - Offset(radius, radius), Size(radius * 2, radius * 2)))
                        }
                        drawPath(hole, Color.Black.copy(alpha = 0.6f))
                        drawCircle(Color.White, radius, center, style = Stroke(4.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(24.dp))
                BrutalSlider(
                    value = zoom,
                    onValueChange = { z ->
                        zoom = z
                        offset = clamp(offset, z)
                    },
                    valueRange = 1f..5f,
                    modifier = Modifier.size(width = side, height = 48.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.size(width = side, height = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BrutalButton(
                        text = "Cancel",
                        onClick = onCancel,
                        fill = c.surface,
                        modifier = Modifier.weight(1f)
                    )
                    BrutalButton(
                        text = "Use photo",
                        onClick = {
                            onDone(cropToBitmap(bitmap, base * zoom, offset, radius))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Renders the circle area (diameter 2 x [radius] on screen) into a 512x512 picture. */
private fun cropToBitmap(src: Bitmap, totalScale: Float, offset: Offset, radius: Float): Bitmap {
    val outSize = 512
    val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
    val k = outSize / (radius * 2f)
    val m = Matrix().apply {
        postTranslate(-src.width / 2f, -src.height / 2f)
        postScale(totalScale, totalScale)
        postTranslate(radius + offset.x, radius + offset.y)
        postScale(k, k)
    }
    AndroidCanvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}
