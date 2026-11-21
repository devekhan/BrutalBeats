package com.brutalbeats.player.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.playback.SleepState
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSheet
import com.brutalbeats.player.ui.components.BrutalTextField
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import kotlinx.coroutines.delay

private fun formatCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

/** Sleep timer: presets, custom minutes, "finish current song", extend and cancel. */
@Composable
fun BoxScope.SleepTimerSheet(
    visible: Boolean,
    state: SleepState,
    remainingMs: () -> Long,
    onStart: (Int) -> Unit,
    onFinishSong: () -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = Brutal.colors
    var remaining by remember { mutableLongStateOf(0L) }
    var custom by remember { mutableStateOf("") }

    LaunchedEffect(visible, state) {
        while (visible && state is SleepState.Countdown) {
            remaining = remainingMs()
            delay(500)
        }
    }

    BrutalSheet(visible = visible, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrutalHeading("Sleep timer", style = BrutalType.h2, modifier = Modifier.weight(1f))
            BrutalIconButton(onClick = onDismiss, contentDescription = "Close sleep timer", fill = c.surface) {
                BrutalIcon(Glyph.Close)
            }
        }
        Spacer(Modifier.height(16.dp))

        when (state) {
            is SleepState.Countdown -> {
                Text(
                    text = formatCountdown(remaining),
                    style = BrutalType.display,
                    color = c.text
                )
                Text(
                    text = "Music fades out over the last 15 seconds.",
                    style = BrutalType.body,
                    color = c.textMuted
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BrutalButton("+5 min", { onExtend(5) }, fill = c.accent3, modifier = Modifier.weight(1f))
                    BrutalButton("Cancel", onCancel, fill = c.danger, modifier = Modifier.weight(1f))
                }
            }
            is SleepState.FinishSong -> {
                Text(
                    text = "Music will stop when this song ends.",
                    style = BrutalType.body,
                    color = c.text
                )
                Spacer(Modifier.height(16.dp))
                BrutalButton(
                    "Cancel",
                    onCancel,
                    fill = c.danger,
                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
                )
            }
            is SleepState.Off -> {
                Text("STOP MUSIC IN", style = BrutalType.label, color = c.textMuted)
                Spacer(Modifier.height(10.dp))
                listOf(listOf(5, 10, 15), listOf(30, 45, 60)).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { minutes ->
                            BrutalChip(
                                text = "$minutes min",
                                selected = false,
                                onClick = { onStart(minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    BrutalTextField(
                        value = custom,
                        onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) custom = v },
                        placeholder = "Custom minutes",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    BrutalButton(
                        text = "Start",
                        onClick = {
                            val minutes = custom.toIntOrNull()
                            if (minutes != null && minutes > 0) {
                                onStart(minutes)
                                custom = ""
                            }
                        },
                        enabled = (custom.toIntOrNull() ?: 0) > 0
                    )
                }
                Spacer(Modifier.height(12.dp))
                BrutalButton(
                    text = "Finish current song",
                    onClick = onFinishSong,
                    fill = c.accent4,
                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
                )
            }
        }
    }
}
