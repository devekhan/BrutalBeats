package com.brutalbeats.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.brutalbeats.player.ui.components.ArtLoader
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.io.IOException

/**
 * Gives the notification and lock screen their album art.
 * Media3 hands us the song uri; we read the embedded art (cached) or draw a flat yellow block.
 */
@OptIn(UnstableApi::class)
class ArtBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw IOException("Could not decode image")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            ArtLoader.load(context, uri.toString(), 0L, 512) ?: placeholder()
        }

    private fun placeholder(): Bitmap =
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(0xFFFFD93D.toInt())
        }
}
