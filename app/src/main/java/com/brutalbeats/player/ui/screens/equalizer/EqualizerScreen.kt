package com.brutalbeats.player.ui.screens.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.data.prefs.EqSettings
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalSlider
import com.brutalbeats.player.ui.components.BrutalSwitch
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.GlassPanel
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LocalGlassState
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.nav.NameInputRequest
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.playback.EqPresets
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val REVERB_NAMES = listOf(
    "None", "Small room", "Medium room", "Large room", "Medium hall", "Large hall", "Plate"
)

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else "$hz"

private fun dbLabel(mb: Int): String =
    if (mb == 0) "0" else String.format(Locale.US, "%+.1f", mb / 100f)

/** Equalizer with vertical band sliders, presets (built-in and your own), and sound effects. */
@Composable
fun EqualizerScreen(
    modifier: Modifier = Modifier,
    vm: EqualizerViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val sheets = LocalSheets.current
    val bottom = LocalBottomInset.current
    val eq by vm.eq.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    val custom by vm.customPresets.collectAsStateWithLifecycle()

    val nothingSupported = caps.bandCount == 0 && !caps.bassBoost && !caps.virtualizer &&
        !caps.loudness && !caps.reverb

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = bottom + 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrutalHeading("Equalizer", style = BrutalType.h1, modifier = Modifier.weight(1f))
            BrutalSwitch(checked = eq.enabled, onCheckedChange = { vm.commit(eq.copy(enabled = it)) })
        }

        if (nothingSupported) {
            EmptyState(
                title = "Equalizer unavailable",
                message = "This phone doesn't offer audio effects, or the player hasn't started yet. " +
                    "Play a song, then come back.",
                glyph = Glyph.Equalizer
            )
            return@Column
        }

        if (!eq.enabled) {
            Text(
                text = "The equalizer is off. Flip the switch to hear your changes.",
                style = BrutalType.body,
                color = c.textMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )
        }

        // ---- presets
        if (caps.equalizer) {
            Text(
                "PRESETS",
                style = BrutalType.label,
                color = c.textMuted,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EqPresets.names.forEach { name ->
                    BrutalChip(
                        text = name,
                        selected = eq.presetKey == name,
                        onClick = { vm.applyBuiltIn(name) }
                    )
                }
                custom.forEach { preset ->
                    BrutalChip(
                        text = preset.name,
                        selected = eq.presetKey == "custom:${preset.id}",
                        onClick = { vm.applyCustom(preset) },
                        selectedFill = c.accent3
                    )
                }
            }
            val selectedCustom = custom.firstOrNull { eq.presetKey == "custom:${it.id}" }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrutalButton(
                    text = "Save preset",
                    onClick = {
                        sheets.nameInput = NameInputRequest("Save preset", "My preset", "Save") { name ->
                            vm.saveCustom(name)
                        }
                    },
                    fill = c.accent4
                )
                if (selectedCustom != null) {
                    BrutalButton(
                        text = "Rename",
                        onClick = {
                            sheets.nameInput = NameInputRequest(
                                "Rename preset", selectedCustom.name, "Save"
                            ) { name -> vm.renameCustom(selectedCustom.id, name) }
                        },
                        fill = c.surface
                    )
                    BrutalButton(
                        text = "Delete",
                        onClick = { vm.deleteCustom(selectedCustom) },
                        fill = c.danger
                    )
                }
            }

            // ---- band sliders (tinted glass: the screen behind is flat, so no blur is needed here)
            val bands = caps.bandCount
            val levels = remember(eq.bandLevelsCsv, bands) {
                mutableStateListOf<Int>().apply { addAll(parseBandLevels(eq.bandLevelsCsv, bands)) }
            }
            CompositionLocalProvider(LocalGlassState provides null) {
                GlassPanel(Modifier.fillMaxWidth().padding(start = 16.dp, end = 22.dp, bottom = 6.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 0 until bands) {
                            val hz = caps.centerFreqsHz.getOrNull(i) ?: 0
                            Column(
                                Modifier.width(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    dbLabel(levels[i]),
                                    style = BrutalType.caption,
                                    color = c.text,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(6.dp))
                                VerticalBandSlider(
                                    level = levels[i],
                                    min = caps.minLevelMb,
                                    max = caps.maxLevelMb,
                                    enabled = eq.enabled,
                                    fill = c.accent,
                                    description = "${freqLabel(hz)} hertz band",
                                    onChange = { v ->
                                        levels[i] = v
                                        vm.preview(
                                            eq.copy(
                                                presetKey = "manual",
                                                bandLevelsCsv = levels.joinToString(",")
                                            )
                                        )
                                    },
                                    onFinished = {
                                        vm.commit(
                                            eq.copy(
                                                presetKey = "manual",
                                                bandLevelsCsv = levels.joinToString(",")
                                            )
                                        )
                                    }
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    freqLabel(hz),
                                    style = BrutalType.caption,
                                    color = c.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ---- effects
        Text(
            "SOUND EFFECTS",
            style = BrutalType.label,
            color = c.textMuted,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )
        Column(Modifier.padding(start = 20.dp, end = 26.dp)) {
            EffectSlider(
                label = "Bass boost",
                supported = caps.bassBoost,
                enabled = eq.enabled,
                saved = eq.bassBoost.toFloat(),
                range = 0f..1000f,
                format = { "${(it / 10f).roundToInt()}%" },
                fill = c.accent2,
                onPreview = { vm.preview(eq.copy(bassBoost = it.roundToInt())) },
                onCommit = { vm.commit(eq.copy(bassBoost = it.roundToInt())) }
            )
            EffectSlider(
                label = "Virtualizer (wider sound)",
                supported = caps.virtualizer,
                enabled = eq.enabled,
                saved = eq.virtualizer.toFloat(),
                range = 0f..1000f,
                format = { "${(it / 10f).roundToInt()}%" },
                fill = c.accent4,
                onPreview = { vm.preview(eq.copy(virtualizer = it.roundToInt())) },
                onCommit = { vm.commit(eq.copy(virtualizer = it.roundToInt())) }
            )
            EffectSlider(
                label = "Loudness gain",
                supported = caps.loudness,
                enabled = eq.enabled,
                saved = eq.loudnessGainMb.toFloat(),
                range = 0f..1500f,
                format = { String.format(Locale.US, "+%.1f dB", it / 100f) },
                fill = c.accent3,
                onPreview = { vm.preview(eq.copy(loudnessGainMb = it.roundToInt())) },
                onCommit = { vm.commit(eq.copy(loudnessGainMb = it.roundToInt())) }
            )
        }

        if (caps.reverb) {
            Spacer(Modifier.height(8.dp))
            Text(
                "REVERB",
                style = BrutalType.label,
                color = c.textMuted,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                REVERB_NAMES.forEachIndexed { index, name ->
                    BrutalChip(
                        text = name,
                        selected = eq.reverbPreset == index,
                        onClick = { vm.commit(eq.copy(reverbPreset = index)) }
                    )
                }
            }
        } else {
            Text(
                "Reverb isn't available on this phone.",
                style = BrutalType.body,
                color = c.textMuted,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(start = 16.dp, end = 22.dp)) {
            BrutalButton(text = "Reset everything", onClick = vm::reset, fill = c.surface)
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    supported: Boolean,
    enabled: Boolean,
    saved: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    fill: Color,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit
) {
    val c = Brutal.colors
    var local by remember(saved) { mutableFloatStateOf(saved) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = BrutalType.label,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (supported) format(local) else "NOT SUPPORTED",
                style = BrutalType.label,
                color = c.textMuted
            )
        }
        BrutalSlider(
            value = local,
            onValueChange = {
                local = it
                onPreview(it)
            },
            valueRange = range,
            onValueChangeFinished = { onCommit(local) },
            fill = fill,
            enabled = supported && enabled
        )
    }
}

/** Tall slider for one frequency band. Snaps to 0 dB near the middle. */
@Composable
private fun VerticalBandSlider(
    level: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    fill: Color,
    description: String,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit
) {
    val c = Brutal.colors
    val borderDp = Brutal.dims.border
    val onChangeNow by rememberUpdatedState(onChange)
    val onFinishedNow by rememberUpdatedState(onFinished)
    val span = (max - min).coerceAtLeast(1)
    val frac = ((level - min).toFloat() / span).coerceIn(0f, 1f)
    val trackColor = c.surface
    val ink = c.ink

    Canvas(
        Modifier
            .width(44.dp)
            .height(190.dp)
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(level.toFloat(), min.toFloat()..max.toFloat())
                setProgress { target ->
                    onChangeNow(target.roundToInt().coerceIn(min, max))
                    true
                }
            }
            .pointerInput(enabled, min, max) {
                if (!enabled) return@pointerInput
                val thumbH = 20.dp.toPx()
                fun toLevel(y: Float): Int {
                    val usable = (size.height - thumbH).coerceAtLeast(1f)
                    val f = 1f - ((y - thumbH / 2f) / usable).coerceIn(0f, 1f)
                    val v = (min + f * span).roundToInt()
                    return if (abs(v) < 40) 0 else v
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onChangeNow(toLevel(down.position.y))
                    drag(down.id) { change ->
                        change.consume()
                        onChangeNow(toLevel(change.position.y))
                    }
                    onFinishedNow()
                }
            }
    ) {
        val border = borderDp.toPx()
        val trackW = 16.dp.toPx()
        val x0 = (size.width - trackW) / 2f
        val thumbH = 20.dp.toPx()
        val shadow = 3.dp.toPx()
        val usable = size.height - thumbH
        val cy = thumbH / 2f + usable * (1f - frac)
        val zeroFrac = ((0 - min).toFloat() / span).coerceIn(0f, 1f)
        val zy = thumbH / 2f + usable * (1f - zeroFrac)

        drawRect(trackColor, Offset(x0, 0f), Size(trackW, size.height))
        drawRect(fill, Offset(x0, cy), Size(trackW, size.height - cy))
        drawRect(ink, Offset(x0, 0f), Size(trackW, size.height), style = Stroke(border))
        drawLine(ink, Offset(x0 - 4.dp.toPx(), zy), Offset(x0 + trackW + 4.dp.toPx(), zy), strokeWidth = 2.dp.toPx())

        val thumbW = size.width - shadow
        val top = cy - thumbH / 2f
        drawRect(ink, Offset(shadow, top + shadow), Size(thumbW, thumbH))
        drawRect(Color.White, Offset(0f, top), Size(thumbW, thumbH))
        drawRect(ink, Offset(0f, top), Size(thumbW, thumbH), style = Stroke(border))
    }
}
