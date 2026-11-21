package com.brutalbeats.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.output.OutputDevice
import com.brutalbeats.player.output.OutputState
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.Palette
import com.brutalbeats.player.ui.theme.readable

/** The "Output Devices" glass sheet: every connected output, the active one tagged CONNECTED. */
@Composable
fun BoxScope.OutputDevicesSheet(
    visible: Boolean,
    state: OutputState,
    onDismiss: () -> Unit,
    onSelect: (OutputDevice) -> Unit,
    onOpenSystemSwitcher: () -> Unit,
    onRequestBluetoothPermission: () -> Unit
) {
    val c = Brutal.colors
    BrutalSheet(visible = visible, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrutalHeading(
                text = "Output devices",
                style = BrutalType.h2,
                modifier = Modifier.weight(1f)
            )
            BrutalIconButton(
                onClick = onDismiss,
                contentDescription = "Close output devices",
                fill = c.surface
            ) { BrutalIcon(Glyph.Close) }
        }
        Spacer(Modifier.height(16.dp))

        if (state.bluetoothPermissionMissing) {
            BrutalSurface(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp), fill = c.accent2) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "WANT TO SEE YOUR HEADPHONES' NAMES?",
                        style = BrutalType.h3,
                        color = c.accent2.readable()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Android hides Bluetooth device names until you allow \"Nearby devices\". " +
                            "Brutal Beats only reads the name. It never connects to anything and has no internet access.",
                        style = BrutalType.body,
                        color = c.accent2.readable()
                    )
                    Spacer(Modifier.height(12.dp))
                    BrutalButton(
                        text = "Allow",
                        onClick = onRequestBluetoothPermission,
                        fill = c.surface
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.bluetoothOn == false) {
            Text(
                text = "Bluetooth is off. Turn it on in Android to connect headphones or speakers.",
                style = BrutalType.body,
                color = c.text
            )
            Spacer(Modifier.height(12.dp))
        }

        state.devices.forEach { device ->
            DeviceRow(device = device, onClick = { onSelect(device) })
            Spacer(Modifier.height(6.dp))
        }

        if (state.devices.count { it.kind != OutputKind.PHONE_SPEAKER } == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "No headphones or speakers connected. Connect one and it pops in here.",
                style = BrutalType.body,
                color = c.textMuted
            )
        }

        Spacer(Modifier.height(18.dp))
        BrutalButton(
            text = "System output switcher",
            onClick = onOpenSystemSwitcher,
            fill = c.accent4,
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
        )
    }
}

@Composable
private fun DeviceRow(device: OutputDevice, onClick: () -> Unit) {
    val c = Brutal.colors
    val shape = RoundedCornerShape(Brutal.dims.corner)
    val bg = if (device.isActive) c.accent else c.surface
    val fg = bg.readable()
    Box(Modifier.padding(end = 6.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics {
                    this.contentDescription =
                        "${device.name}, ${device.kind.label}" + if (device.isActive) ", connected" else ""
                }
                .brutalPress(onClick, shape, 4.dp, c.ink)
                .clip(shape)
                .background(bg)
                .border(Brutal.dims.border, c.ink, shape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DeviceIcon(kind = device.kind, size = 44.dp, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = BrutalType.bodyBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = device.kind.label.uppercase(),
                    style = BrutalType.caption,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
            if (device.batteryPercent != null) {
                Sticker(text = "${device.batteryPercent}%", fill = Palette.White, rotation = 2f)
            }
            if (device.isActive) {
                Sticker(text = "Connected", fill = c.accent3, rotation = -3f)
            }
        }
    }
}

/**
 * Small tappable chip showing the active output (icon + name). Used in Now Playing and the mini player.
 * The icon springs in whenever the device type changes.
 */
@Composable
fun ActiveOutputChip(
    active: OutputDevice?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val c = Brutal.colors
    val kind = active?.kind ?: OutputKind.PHONE_SPEAKER
    val name = active?.name ?: "Phone speaker"
    val shape = RoundedCornerShape(Brutal.dims.corner)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics { this.contentDescription = "Audio output: $name. Tap to change." }
            .brutalPress(onClick, shape, 3.dp, c.ink)
            .clip(shape)
            .background(c.surface)
            .border(Brutal.dims.border, c.ink, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeviceIcon(kind = kind, size = if (compact) 32.dp else 36.dp, contentDescription = null)
        if (!compact) {
            Text(
                text = name,
                style = BrutalType.label,
                color = c.surface.readable(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
        }
    }
}
