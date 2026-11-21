package com.brutalbeats.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.brutalbeats.player.data.db.Song

/**
 * Converts a library song into a Media3 item. The media id is the MediaStore id as text,
 * and the artwork "uri" is the song's own uri: ArtBitmapLoader turns it into album art
 * for the notification and lock screen.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(Uri.parse(uri))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
