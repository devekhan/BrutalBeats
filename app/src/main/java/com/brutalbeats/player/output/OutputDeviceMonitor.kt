package com.brutalbeats.player.output

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.brutalbeats.player.ui.components.OutputKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OutputDevice(
    val id: Int,
    val name: String,
    val kind: OutputKind,
    val isActive: Boolean,
    val isBluetooth: Boolean,
    /** 0-100 when Android exposes it for this device, otherwise null. */
    val batteryPercent: Int?
)

data class OutputState(
    val devices: List<OutputDevice> = emptyList(),
    val active: OutputDevice? = null,
    /** null = unknown / no Bluetooth hardware. */
    val bluetoothOn: Boolean? = null,
    /** True when a Bluetooth device is connected but Android hides its name without the permission. */
    val bluetoothPermissionMissing: Boolean = false
)

private val BT_TYPES = setOf(
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID
)

private val WIRED_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_AUX_LINE,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL
)

private val CAR_REGEX = Regex("\\bcar\\b")
private val CAR_WORDS = listOf(
    "carplay", "car audio", "uconnect", "mazda", "bmw", "audi", "toyota", "honda", "ford", "hyundai",
    "kia", "tesla", "volvo", "mercedes", "skoda", "tata", "mahindra", "maruti", "suzuki", "nissan", "renault"
)
private val EARBUD_WORDS = listOf(
    "buds", "pods", "earbud", "airdopes", "tws", "ear (", "ear(", "nothing ear", "freebuds",
    "jabra elite", "wf-", "liberty"
)
private val HEADPHONE_WORDS = listOf(
    "headphone", "headset", "wh-", "wh1000", "quietcomfort", "momentum", "arctis", "studio", "over-ear", "neckband"
)
private val SPEAKER_WORDS = listOf(
    "speaker", "boom", "jbl", "soundlink", "sonos", "wonderboom", "partybox", "soundbar", "echo", "flip", "xtreme"
)

/** Picks the icon for an output: by Android's device type first, then by name hints. */
fun classifyOutput(type: Int, name: String): OutputKind {
    val n = name.lowercase()
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.PHONE_SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.HEADPHONES
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> OutputKind.WIRED_HEADSET
        else -> when {
            CAR_REGEX.containsMatchIn(n) || CAR_WORDS.any { n.contains(it) } -> OutputKind.CAR
            EARBUD_WORDS.any { n.contains(it) } -> OutputKind.EARBUDS
            HEADPHONE_WORDS.any { n.contains(it) } -> OutputKind.HEADPHONES
            SPEAKER_WORDS.any { n.contains(it) } || type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> OutputKind.BT_SPEAKER
            else -> OutputKind.BLUETOOTH
        }
    }
}

/**
 * Watches which audio outputs are connected (speaker, wired, Bluetooth, USB) and which one is playing.
 * Updates instantly on connect/disconnect. Can route the app's audio to a chosen device, and falls back
 * to the system output switcher where Android doesn't allow that.
 */
@OptIn(UnstableApi::class)
@Singleton
class OutputDeviceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(OutputState())
    val state: StateFlow<OutputState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var preferredId: Int? = null
    private var started = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val gone = removedDevices?.any { it.id == preferredId } == true
            if (gone) {
                preferredId = null
                try { player?.setPreferredAudioDevice(null) } catch (e: Exception) { }
            }
            refresh()
        }
    }

    /** Safe to call many times. */
    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun attachPlayer(p: ExoPlayer?) {
        player = p
        if (p == null) preferredId = null
    }

    fun refresh() {
        val permission = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        val shown = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type in BT_TYPES || it.type in WIRED_TYPES }
        val activeId = resolveActiveId(shown)

        val devices = shown
            .map { toDevice(it, it.id == activeId, permission) }
            .distinctBy { it.isBluetooth to it.name }
            .sortedWith(compareBy({ !it.isActive }, { rank(it) }))

        val btOn: Boolean? = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        } catch (e: Exception) {
            null
        }

        _state.value = OutputState(
            devices = devices,
            active = devices.firstOrNull { it.isActive },
            bluetoothOn = btOn,
            bluetoothPermissionMissing = !permission && devices.any { it.isBluetooth }
        )
    }

    /** Routes this app's audio to [device]. Returns false when that isn't possible (then use the system switcher). */
    fun select(device: OutputDevice): Boolean {
        val p = player ?: return false
        val info = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == device.id } ?: return false
        return try {
            val automatic = bestDefault(
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type in BT_TYPES || it.type in WIRED_TYPES
                }
            )
            if (automatic != null && automatic.id == info.id) {
                p.setPreferredAudioDevice(null) // back to Android's automatic choice
                preferredId = null
            } else {
                p.setPreferredAudioDevice(info)
                preferredId = info.id
            }
            refresh()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Opens Android's own output picker (falls back to Bluetooth settings). */
    fun openSystemSwitcher() {
        val tries = listOf(
            Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName),
            Intent("android.settings.panel.action.VOLUME"),
            Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
        )
        for (intent in tries) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun rank(d: OutputDevice): Int = when {
        d.isBluetooth -> 0
        d.kind == OutputKind.PHONE_SPEAKER -> 2
        else -> 1
    }

    private fun rankType(type: Int): Int = when {
        type in BT_TYPES -> 0
        type in WIRED_TYPES -> 1
        else -> 2
    }

    private fun bestDefault(shown: List<AudioDeviceInfo>): AudioDeviceInfo? =
        shown.minByOrNull { rankType(it.type) }

    private fun resolveActiveId(shown: List<AudioDeviceInfo>): Int? {
        val pref = preferredId
        if (pref != null && shown.any { it.id == pref }) return pref
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val routed = audioManager.getAudioDevicesForAttributes(attrs)
                val match = shown.firstOrNull { s -> routed.any { it.id == s.id } }
                if (match != null) return match.id
            } catch (e: Exception) {
                // fall through to the priority guess
            }
        }
        return bestDefault(shown)?.id
    }

    private fun toDevice(info: AudioDeviceInfo, active: Boolean, permission: Boolean): OutputDevice {
        val raw = info.productName?.toString()?.trim().orEmpty()
        val bluetooth = info.type in BT_TYPES
        val name = when (info.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI output"
            else -> raw.ifEmpty { if (bluetooth) "Bluetooth device" else "Audio device" }
        }
        val battery = if (bluetooth && permission && Build.VERSION.SDK_INT >= 28) {
            readBattery(info.address)
        } else null
        return OutputDevice(info.id, name, classifyOutput(info.type, name), active, bluetooth, battery)
    }

    /** Android has no public battery API for audio devices, so this asks politely and shrugs if refused. */
    private fun readBattery(address: String?): Int? {
        if (address.isNullOrEmpty()) return null
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
            val device = adapter.getRemoteDevice(address)
            val level = device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
            if (level in 0..100) level else null
        } catch (e: Throwable) {
            null
        }
    }
}
