package com.brutalbeats.player.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.data.prefs.ThemeMode
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSlider
import com.brutalbeats.player.ui.components.BrutalSwitch
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.sheets.ActionRow
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.Palette
import kotlin.math.roundToInt

private val ACCENT_NAMES = listOf("Yellow", "Hot pink", "Mint", "Electric blue", "Orange")
private val MIN_TRACK_CHOICES = listOf(0, 15, 30, 45, 60, 90, 120)

/** Settings: look, library, playback and About. */
@Composable
fun SettingsScreen(
    onOpenFolders: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val bottom = LocalBottomInset.current
    val s by vm.settings.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = bottom + 16.dp)
    ) {
        BrutalHeading(
            "Settings",
            style = BrutalType.h1,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
        )

        // ---------------- look
        SectionLabel("Look")
        Text(
            "THEME",
            style = BrutalType.label,
            color = c.text,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeMode.values().forEach { mode ->
                BrutalChip(
                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    selected = s.themeMode == mode,
                    onClick = { vm.setTheme(mode) }
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Text(
            "ACCENT COLOR",
            style = BrutalType.label,
            color = c.text,
            modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Palette.AccentPresets.forEachIndexed { index, color ->
                val selected = s.accentArgb == color.toArgb()
                BrutalIconButton(
                    onClick = { vm.setAccent(color.toArgb()) },
                    contentDescription = "${ACCENT_NAMES[index]} accent" + if (selected) ", selected" else "",
                    fill = color,
                    size = 52.dp
                ) {
                    if (selected) BrutalIcon(Glyph.Check, tint = Color.Black, size = 26.dp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        DeferredSlider(
            label = "Glass intensity",
            saved = s.glassIntensity,
            range = 0f..1f,
            format = { "${(it * 100).roundToInt()}%" },
            onCommit = { vm.setGlass(it) }
        )
        SwitchRow(
            title = "High contrast",
            subtitle = "Solid panels, thicker borders. Easier to read.",
            checked = s.highContrast,
            onChange = vm::setHighContrast
        )
        Spacer(Modifier.height(18.dp))

        // ---------------- library
        SectionLabel("Library")
        Text(
            "IGNORE SONGS SHORTER THAN",
            style = BrutalType.label,
            color = c.text,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MIN_TRACK_CHOICES.forEach { seconds ->
                BrutalChip(
                    text = if (seconds == 0) "Off" else "${seconds}s",
                    selected = s.minTrackSeconds == seconds,
                    onClick = { vm.setMinTrackSeconds(seconds) },
                    selectedFill = c.accent3
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(start = 20.dp, end = 26.dp)) {
            ActionRow(Glyph.Folder, "Folders: include / hide", onOpenFolders)
            ActionRow(Glyph.Refresh, "Rescan library now", vm::rescan)
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- playback
        SectionLabel("Playback")
        DeferredSlider(
            label = "Crossfade",
            saved = s.crossfadeSeconds.toFloat(),
            range = 0f..12f,
            format = { if (it.roundToInt() == 0) "Off" else "${it.roundToInt()}s" },
            onCommit = { vm.setCrossfade(it.roundToInt()) },
            hint = "Music fades out and the next song fades in. The dip lasts this long in total."
        )
        SwitchRow(
            title = "Fade on pause",
            subtitle = "Soft fade when you pause or resume.",
            checked = s.fadeOnPause,
            onChange = vm::setFadeOnPause
        )
        Spacer(Modifier.height(18.dp))

        // ---------------- about
        SectionLabel("About")
        Column(Modifier.padding(start = 20.dp, end = 26.dp)) {
            ActionRow(Glyph.Music, "About Brutal Beats", onOpenAbout, fill = c.accent)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BrutalHeading(
        text = text,
        style = BrutalType.h3,
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Brutal.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title.uppercase(), style = BrutalType.label, color = c.text)
            Text(subtitle, style = BrutalType.caption, color = c.textMuted)
        }
        BrutalSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** A slider that moves freely while you drag and saves when you let go. */
@Composable
private fun DeferredSlider(
    label: String,
    saved: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onCommit: (Float) -> Unit,
    hint: String? = null
) {
    val c = Brutal.colors
    var local by remember(saved) { mutableFloatStateOf(saved) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 26.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = BrutalType.label, color = c.text, modifier = Modifier.weight(1f))
            Text(format(local), style = BrutalType.label, color = c.textMuted)
        }
        BrutalSlider(
            value = local,
            onValueChange = { local = it },
            valueRange = range,
            onValueChangeFinished = { onCommit(local) }
        )
        if (hint != null) Text(hint, style = BrutalType.caption, color = c.textMuted)
    }
}
