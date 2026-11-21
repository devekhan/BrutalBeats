package com.brutalbeats.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Every UI glyph in the app. Custom-drawn: thick strokes, square caps, no stock Material icons. */
enum class Glyph {
    Play, Pause, Next, Previous, Shuffle, Repeat, RepeatOne, Heart, HeartFilled, Search,
    Settings, Queue, Equalizer, Timer, Plus, Close, Check, Back, ChevronDown, Music, Folder,
    Album, Artist, Playlist, Refresh, More, Volume, Trash, Edit, Home, Bluetooth, Sort
}

/**
 * Draws a [Glyph] in a 24x24 grid scaled to [size].
 * Pass [contentDescription] when the icon stands alone (not inside a labelled button).
 */
@Composable
fun BrutalIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    contentDescription: String? = null
) {
    val sem = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier
    Canvas(modifier.size(size).then(sem)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawGlyph(glyph, tint) }
    }
}

private const val SW = 2.6f

private fun poly(vararg xy: Float, closed: Boolean = false): Path = Path().apply {
    moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size) {
        lineTo(xy[i], xy[i + 1])
        i += 2
    }
    if (closed) close()
}

private fun heartPath(): Path = Path().apply {
    moveTo(12f, 20.5f)
    lineTo(4f, 12.5f)
    cubicTo(1.5f, 10f, 2f, 6f, 5.5f, 4.8f)
    cubicTo(8f, 4f, 10.5f, 5.2f, 12f, 8f)
    cubicTo(13.5f, 5.2f, 16f, 4f, 18.5f, 4.8f)
    cubicTo(22f, 6f, 22.5f, 10f, 20f, 12.5f)
    close()
}

private fun DrawScope.drawGlyph(glyph: Glyph, c: Color) {
    val line = Stroke(width = SW, cap = StrokeCap.Square, join = StrokeJoin.Miter)
    fun stroke(p: Path) = drawPath(p, c, style = line)
    fun fill(p: Path) {
        drawPath(p, c)
        drawPath(p, c, style = Stroke(1.6f, join = StrokeJoin.Miter))
    }
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = SW, cap = StrokeCap.Square)
    fun box(l: Float, t: Float, r: Float, b: Float) =
        drawRect(c, Offset(l, t), Size(r - l, b - t))
    fun repeatBase() {
        stroke(poly(4f, 12f, 4f, 7f, 17f, 7f))
        fill(poly(16f, 3.5f, 21f, 7f, 16f, 10.5f, closed = true))
        stroke(poly(20f, 12f, 20f, 17f, 7f, 17f))
        fill(poly(8f, 13.5f, 3f, 17f, 8f, 20.5f, closed = true))
    }

    when (glyph) {
        Glyph.Play -> fill(poly(7f, 4f, 20f, 12f, 7f, 20f, closed = true))
        Glyph.Pause -> {
            box(6f, 4f, 10.5f, 20f)
            box(13.5f, 4f, 18f, 20f)
        }
        Glyph.Next -> {
            fill(poly(5f, 5f, 15f, 12f, 5f, 19f, closed = true))
            box(17f, 5f, 20f, 19f)
        }
        Glyph.Previous -> {
            fill(poly(19f, 5f, 9f, 12f, 19f, 19f, closed = true))
            box(4f, 5f, 7f, 19f)
        }
        Glyph.Shuffle -> {
            stroke(poly(3f, 7f, 8f, 7f, 16f, 17f, 19f, 17f))
            stroke(poly(3f, 17f, 8f, 17f, 10f, 14.5f))
            stroke(poly(14f, 9.5f, 16f, 7f, 19f, 7f))
            fill(poly(18f, 3.5f, 22f, 7f, 18f, 10.5f, closed = true))
            fill(poly(18f, 13.5f, 22f, 17f, 18f, 20.5f, closed = true))
        }
        Glyph.Repeat -> repeatBase()
        Glyph.RepeatOne -> {
            repeatBase()
            stroke(poly(10.5f, 11.5f, 12.5f, 10f, 12.5f, 14.5f))
        }
        Glyph.Heart -> stroke(heartPath())
        Glyph.HeartFilled -> fill(heartPath())
        Glyph.Search -> {
            drawCircle(c, 6f, Offset(10f, 10f), style = line)
            seg(14.5f, 14.5f, 20.5f, 20.5f)
        }
        Glyph.Settings -> {
            drawCircle(c, 5.5f, Offset(12f, 12f), style = line)
            drawCircle(c, 1.8f, Offset(12f, 12f))
            for (i in 0 until 8) {
                val a = (i * PI / 4).toFloat()
                val cx = cos(a)
                val sy = sin(a)
                seg(12f + cx * 8f, 12f + sy * 8f, 12f + cx * 10.5f, 12f + sy * 10.5f)
            }
        }
        Glyph.Queue -> {
            seg(3f, 6f, 15f, 6f)
            seg(3f, 12f, 15f, 12f)
            seg(3f, 18f, 10f, 18f)
            fill(poly(15f, 13f, 21.5f, 17.5f, 15f, 22f, closed = true))
        }
        Glyph.Equalizer -> {
            seg(6f, 4f, 6f, 20f)
            seg(12f, 4f, 12f, 20f)
            seg(18f, 4f, 18f, 20f)
            box(3.5f, 13f, 8.5f, 17f)
            box(9.5f, 6f, 14.5f, 10f)
            box(15.5f, 11f, 20.5f, 15f)
        }
        Glyph.Timer -> {
            drawCircle(c, 8f, Offset(12f, 13.5f), style = line)
            seg(12f, 13.5f, 12f, 9f)
            seg(9.5f, 2.5f, 14.5f, 2.5f)
        }
        Glyph.Plus -> {
            seg(12f, 4f, 12f, 20f)
            seg(4f, 12f, 20f, 12f)
        }
        Glyph.Close -> {
            seg(5f, 5f, 19f, 19f)
            seg(19f, 5f, 5f, 19f)
        }
        Glyph.Check -> stroke(poly(4f, 12.5f, 9.5f, 18f, 20f, 6.5f))
        Glyph.Back -> {
            seg(20f, 12f, 5f, 12f)
            stroke(poly(11f, 6f, 5f, 12f, 11f, 18f))
        }
        Glyph.ChevronDown -> stroke(poly(5f, 9f, 12f, 16f, 19f, 9f))
        Glyph.Music -> fill(poly(13f, 3f, 19f, 3f, 19f, 8f, 15f, 8f, 15f, 20f, 7f, 20f, 7f, 14f, 13f, 14f, closed = true))
        Glyph.Folder -> stroke(poly(3f, 6f, 10f, 6f, 12f, 8.5f, 21f, 8.5f, 21f, 19f, 3f, 19f, closed = true))
        Glyph.Album -> {
            drawCircle(c, 9f, Offset(12f, 12f), style = line)
            drawCircle(c, 2.6f, Offset(12f, 12f))
        }
        Glyph.Artist -> {
            drawCircle(c, 4f, Offset(12f, 8f), style = line)
            stroke(poly(4f, 21f, 4f, 18f, 8f, 14.5f, 16f, 14.5f, 20f, 18f, 20f, 21f))
        }
        Glyph.Playlist -> {
            seg(9f, 6f, 21f, 6f)
            seg(9f, 12f, 21f, 12f)
            seg(9f, 18f, 21f, 18f)
            box(3f, 4.5f, 6f, 7.5f)
            box(3f, 10.5f, 6f, 13.5f)
            box(3f, 16.5f, 6f, 19.5f)
        }
        Glyph.Refresh -> {
            drawArc(c, 30f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = line)
            fill(poly(20.4f, 9.5f, 20.2f, 3.85f, 14.8f, 8.35f, closed = true))
        }
        Glyph.More -> {
            box(10.5f, 3f, 13.5f, 6f)
            box(10.5f, 10.5f, 13.5f, 13.5f)
            box(10.5f, 18f, 13.5f, 21f)
        }
        Glyph.Volume -> {
            fill(poly(3f, 9f, 7f, 9f, 12f, 5f, 12f, 19f, 7f, 15f, 3f, 15f, closed = true))
            stroke(poly(15f, 9f, 17f, 12f, 15f, 15f))
            stroke(poly(18.5f, 6f, 21f, 12f, 18.5f, 18f))
        }
        Glyph.Trash -> {
            seg(4f, 7f, 20f, 7f)
            stroke(poly(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f))
            stroke(poly(6f, 7f, 6f, 21f, 18f, 21f, 18f, 7f))
            seg(10f, 11f, 10f, 17f)
            seg(14f, 11f, 14f, 17f)
        }
        Glyph.Edit -> stroke(poly(4f, 20f, 5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f, closed = true))
        Glyph.Home -> {
            stroke(poly(3f, 11f, 12f, 3f, 21f, 11f, 21f, 21f, 3f, 21f, closed = true))
            box(10f, 14f, 14f, 21f)
        }
        Glyph.Bluetooth -> stroke(poly(7f, 7f, 17f, 17f, 12f, 22f, 12f, 2f, 17f, 7f, 7f, 17f))
        Glyph.Sort -> {
            seg(3f, 6f, 21f, 6f)
            seg(3f, 12f, 15f, 12f)
            seg(3f, 18f, 9f, 18f)
        }
    }
}
