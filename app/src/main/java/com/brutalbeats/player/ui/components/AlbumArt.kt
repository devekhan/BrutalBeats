package com.brutalbeats.player.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.brutalbeats.player.ui.theme.Brutal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.brutalbeats.player.ui.theme.Palette as BB

/**
 * Loads embedded album art (or the MediaStore fallback) with a small memory cache.
 * Missing art is remembered so scrolling never retries the same file.
 */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun load(context: Context, uri: String, albumId: Long, px: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            val key = if (albumId > 0) "a$albumId:$px" else "u$uri:$px"
            cache.get(key)?.let { return@withContext it }
            if (key in misses) return@withContext null
            val bmp = readArt(context, Uri.parse(uri), albumId, px)
            if (bmp != null) cache.put(key, bmp) else misses.add(key)
            bmp
        }

    /** Picks a vivid accent from the art (used to tint the Now Playing background and notification). */
    suspend fun accentOf(bitmap: Bitmap, fallback: Color): Color = withContext(Dispatchers.Default) {
        val p = Palette.from(bitmap).maximumColorCount(12).generate()
        val argb = p.getVibrantColor(p.getMutedColor(p.getDominantColor(0)))
        if (argb == 0) fallback else Color(argb)
    }

    private fun readArt(context: Context, uri: Uri, albumId: Long, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } catch (_: Exception) {
            }
        }
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val bytes = r.embeddedPicture
                if (bytes != null) {
                    val bmp = decodeSampled(bytes, px)
                    if (bmp != null) return bmp
                }
            } finally {
                r.release()
            }
        } catch (_: Exception) {
        }
        if (albumId > 0) {
            try {
                val art = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )
                context.contentResolver.openInputStream(art)?.use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun decodeSampled(bytes: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

/**
 * Album art with a flat colored placeholder (music note) when a song has no art.
 * [framed] adds the thick border + hard shadow. [onLoaded] reports the bitmap (or null) for Palette use.
 */
@Composable
fun AlbumArt(
    uri: String?,
    albumId: Long,
    modifier: Modifier = Modifier,
    sizePx: Int = 256,
    shape: Shape = RoundedCornerShape(Brutal.dims.corner),
    framed: Boolean = true,
    shadow: Dp = 4.dp,
    contentDescription: String? = null,
    onLoaded: ((Bitmap?) -> Unit)? = null
) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(null, uri, albumId, sizePx) {
        value = if (uri == null) null else ArtLoader.load(context, uri, albumId, sizePx)
    }
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    LaunchedEffect(bitmap, uri) { currentOnLoaded?.invoke(bitmap) }

    val seed = if (albumId != 0L) albumId else (uri?.hashCode()?.toLong() ?: 0L)
    val placeholder = BB.AccentPresets[(((seed % 5) + 5) % 5).toInt()]
    val sem = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else Modifier
    val base = if (framed) {
        modifier.brutalBox(placeholder, shape, Brutal.dims.border, Brutal.colors.ink, shadow)
    } else {
        modifier.clip(shape)
    }
    Box(base.then(sem), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            BrutalIcon(Glyph.Music, tint = Color.Black, size = 40.dp)
        }
    }
}
