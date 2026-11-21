package com.brutalbeats.player.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

data class Profile(val name: String = "", val avatarPath: String? = null)

/** Display name and profile picture. The picture is kept in app-private storage (no permission needed). */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SettingsStore
) : ViewModel() {

    val profile: StateFlow<Profile> = store.settings
        .map { Profile(it.userName, it.avatarPath) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile())

    fun setName(name: String) {
        viewModelScope.launch {
            store.edit { it[SettingsStore.Keys.USER_NAME] = name.trim().take(24) }
        }
    }

    /** Saves the cropped picture as a new file, then deletes the previous one. */
    fun saveAvatar(bitmap: Bitmap) {
        viewModelScope.launch {
            val oldPath = store.settings.first().avatarPath
            val newPath = withContext(Dispatchers.IO) { writeAvatar(bitmap) } ?: return@launch
            store.edit { it[SettingsStore.Keys.AVATAR] = newPath }
            if (oldPath != null && oldPath != newPath) {
                withContext(Dispatchers.IO) { File(oldPath).delete() }
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch {
            val oldPath = store.settings.first().avatarPath
            store.edit { it.remove(SettingsStore.Keys.AVATAR) }
            if (oldPath != null) withContext(Dispatchers.IO) { File(oldPath).delete() }
        }
    }

    /** Reads a picked photo (scaled down, rotated upright) so the cropper can show it. */
    suspend fun loadForCrop(uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                decodeForCrop(context, uri, 2048)
            } catch (e: Exception) {
                null
            }
        }

    private fun writeAvatar(bitmap: Bitmap): String? =
        try {
            val dir = File(context.filesDir, "profile").apply { mkdirs() }
            val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
}

private fun decodeForCrop(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        ?: return null

    val rawOrientation: Int? = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (e: Exception) {
        null
    }
    val degrees = when (rawOrientation ?: ExifInterface.ORIENTATION_NORMAL) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bmp
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}
