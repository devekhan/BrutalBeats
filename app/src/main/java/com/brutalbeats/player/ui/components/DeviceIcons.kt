package com.brutalbeats.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.Palette

/** Kinds of audio output shown in Now Playing, the mini player and the Output Devices sheet. */
enum class OutputKind(val label: String, val fill: Color) {
    HEADPHONES("Headphones", Palette.HotPink),
    EARBUDS("Earbuds", Palette.Mint),
    BT_SPEAKER("Bluetooth speaker", Palette.Orange),
    CAR("Car audio", Palette.ElectricBlue),
    WIRED_HEADSET("Wired headset", Palette.Yellow),
    PHONE_SPEAKER("Phone speaker", Palette.White),
    BLUETOOTH("Bluetooth device", Palette.ElectricBlue)
}

/**
 * Neo-brutalist device icon: thick outline, flat fill, hard shadow.
 * With [popIn] the icon springs in whenever [kind] changes (connect / disconnect).
 */
@Composable
fun DeviceIcon(
    kind: OutputKind,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    popIn: Boolean = true,
    contentDescription: String? = kind.label
) {
    val ink = Brutal.colors.ink
    val pop = remember { Animatable(if (popIn) 0.4f else 1f) }
    LaunchedEffect(kind) {
        if (popIn) {
            pop.snapTo(0.4f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    val sem = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .then(sem)
    ) {
        scale(this.size.minDimension / 48f, pivot = Offset.Zero) { drawDevice(kind, ink) }
    }
}

private fun pathOf(vararg xy: Float): Path = Path().apply {
    moveTo(xy[0], xy[1])
    var i = 2
    while (i < xy.size) {
        lineTo(xy[i], xy[i + 1])
        i += 2
    }
    close()
}

private fun DrawScope.drawDevice(kind: OutputKind, ink: Color) {
    val sw = 2.6f
    val sh = 2.6f

    fun solid(path: Path, fill: Color = kind.fill) {
        translate(sh, sh) { drawPath(path, ink) }
        drawPath(path, fill)
        drawPath(path, ink, style = Stroke(sw, join = StrokeJoin.Miter))
    }
    fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float = 3f): Path =
        Path().apply { addRoundRect(RoundRect(l, t, r, b, CornerRadius(rad))) }
    fun disc(cx: Float, cy: Float, r: Float): Path =
        Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    fun band(topLeft: Offset, sz: Size) {
        translate(sh, sh) { drawArc(ink, 180f, 180f, false, topLeft, sz, style = Stroke(8f)) }
        drawArc(ink, 180f, 180f, false, topLeft, sz, style = Stroke(8f))
        drawArc(kind.fill, 180f, 180f, false, topLeft, sz, style = Stroke(3f))
    }

    when (kind) {
        OutputKind.HEADPHONES -> {
            band(Offset(11f, 7f), Size(26f, 30f))
            solid(rr(6f, 22f, 16f, 40f))
            solid(rr(32f, 22f, 42f, 40f))
        }
        OutputKind.EARBUDS -> {
            solid(rr(12.5f, 20f, 17.5f, 40f, 2f))
            solid(rr(30.5f, 20f, 35.5f, 40f, 2f))
            solid(disc(15f, 17f, 7f))
            solid(disc(33f, 17f, 7f))
            drawCircle(ink, 1.8f, Offset(15f, 17f))
            drawCircle(ink, 1.8f, Offset(33f, 17f))
        }
        OutputKind.BT_SPEAKER -> {
            solid(rr(9f, 5f, 39f, 43f, 4f))
            solid(disc(24f, 29f, 9f), Palette.White)
            drawCircle(ink, 3f, Offset(24f, 29f))
            solid(disc(24f, 14f, 3.5f), Palette.White)
        }
        OutputKind.CAR -> {
            solid(pathOf(4f, 32f, 6f, 23f, 15f, 22f, 20f, 14f, 32f, 14f, 38f, 22f, 44f, 24f, 44f, 32f))
            solid(pathOf(21.5f, 17.5f, 30f, 17.5f, 34f, 22.5f, 17f, 22.5f), Palette.White)
            solid(disc(14f, 33f, 5.5f), Palette.White)
            solid(disc(35f, 33f, 5.5f), Palette.White)
            drawCircle(ink, 2f, Offset(14f, 33f))
            drawCircle(ink, 2f, Offset(35f, 33f))
        }
        OutputKind.WIRED_HEADSET -> {
            band(Offset(13f, 8f), Size(22f, 24f))
            solid(rr(8f, 20f, 17f, 34f))
            solid(rr(31f, 20f, 40f, 34f))
            drawLine(ink, Offset(35.5f, 34f), Offset(35.5f, 40f), strokeWidth = 3f)
            solid(rr(32f, 40f, 39f, 45f, 1.5f), Palette.White)
        }
        OutputKind.PHONE_SPEAKER -> {
            solid(rr(13f, 4f, 35f, 44f, 4f))
            solid(rr(16f, 10f, 32f, 34f, 1f), Palette.PaperLight)
            drawLine(ink, Offset(20f, 7f), Offset(28f, 7f), strokeWidth = 2.2f)
            drawCircle(ink, 2f, Offset(24f, 39f))
        }
        OutputKind.BLUETOOTH -> {
            solid(rr(5f, 5f, 43f, 43f, 6f))
            drawPath(
                pathOf(17f, 17f, 31f, 31f, 24f, 38f, 24f, 10f, 31f, 17f, 17f, 31f),
                Color.White,
                style = Stroke(3.2f, join = StrokeJoin.Miter)
            )
        }
    }
}
