package com.brutalbeats.player.ui.nav

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Extra bottom space every screen must leave for the mini player and bottom bar. Provided by the root layout. */
val LocalBottomInset = compositionLocalOf<Dp> { 0.dp }

/** A page that lists songs, opened from the library. */
sealed interface Detail {
    val title: String

    data class Album(val id: Long, val name: String) : Detail {
        override val title: String get() = name
    }

    data class Artist(val name: String) : Detail {
        override val title: String get() = name
    }

    data class Folder(val path: String, val name: String) : Detail {
        override val title: String get() = name
    }

    data class Playlist(val id: Long, val name: String) : Detail {
        override val title: String get() = name
    }
}
