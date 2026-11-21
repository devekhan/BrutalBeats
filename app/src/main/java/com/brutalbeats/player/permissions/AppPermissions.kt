package com.brutalbeats.player.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.Glyph

object Perms {
    /** Audio files: READ_MEDIA_AUDIO on Android 13+, READ_EXTERNAL_STORAGE before that. */
    val audio: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasAudio(context: Context) = has(context, audio)

    fun hasNotifications(context: Context) =
        Build.VERSION.SDK_INT < 33 || has(context, Manifest.permission.POST_NOTIFICATIONS)

    fun hasBluetooth(context: Context) =
        Build.VERSION.SDK_INT < 31 || has(context, Manifest.permission.BLUETOOTH_CONNECT)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Current permission state plus the actions to ask for them. Refreshes when you return from Settings. */
class AppPermissions(
    val audio: Boolean,
    val notifications: Boolean,
    val bluetooth: Boolean,
    /** True after the audio permission was refused for good: only Android Settings can grant it now. */
    val audioBlocked: Boolean,
    private val onRequestStartup: () -> Unit,
    private val onRequestBluetooth: () -> Unit,
    private val onOpenSettings: () -> Unit
) {
    /** Asks for audio access and notification permission (Android 13+) in one go. */
    fun requestStartup() = onRequestStartup()

    /** Asks for "Nearby devices" (Android 12+), used only to read Bluetooth device names. */
    fun requestBluetooth() = onRequestBluetooth()

    fun openSettings() = onOpenSettings()
}

@Composable
fun rememberAppPermissions(): AppPermissions {
    val context = LocalContext.current
    var audio by remember { mutableStateOf(Perms.hasAudio(context)) }
    var notifications by remember { mutableStateOf(Perms.hasNotifications(context)) }
    var bluetooth by remember { mutableStateOf(Perms.hasBluetooth(context)) }
    var audioAsked by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        audio = Perms.hasAudio(context)
        notifications = Perms.hasNotifications(context)
        bluetooth = Perms.hasBluetooth(context)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    val activity = context.findActivity()
    val blocked = audioAsked && !audio && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Perms.audio)

    return AppPermissions(
        audio = audio,
        notifications = notifications,
        bluetooth = bluetooth,
        audioBlocked = blocked,
        onRequestStartup = {
            audioAsked = true
            val wanted = buildList {
                if (!audio) add(Perms.audio)
                if (!notifications && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
        },
        onRequestBluetooth = {
            if (!bluetooth && Build.VERSION.SDK_INT >= 31) {
                launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
        },
        onOpenSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    )
}

/** Shown instead of the library until the audio permission is granted. */
@Composable
fun AudioPermissionScreen(
    blocked: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    EmptyState(
        title = "Let me hear your music",
        message = if (blocked) {
            "Android won't ask again. Open Settings, then Permissions, and allow \"Music and audio\"."
        } else {
            "Brutal Beats needs to read the audio files on your phone. " +
                "Nothing leaves your device: this app has no internet access at all."
        },
        modifier = modifier,
        glyph = Glyph.Music,
        actionLabel = if (blocked) "Open settings" else "Give access",
        onAction = if (blocked) onOpenSettings else onGrant
    )
}
