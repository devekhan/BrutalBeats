package com.brutalbeats.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.brutalbeats.player.data.db.Song
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the screens need to know about playback. */
data class PlayerUiState(
    val connected: Boolean = false,
    val currentId: Long = -1L,
    val index: Int = 0,
    val queueIds: List<Long> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val error: String? = null
)

/**
 * The screens' remote control for the playback service. All calls must be made on the main thread
 * (view models and Compose already are). Call [connect] once when the app starts.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LibraryRepository,
    private val settingsStore: SettingsStore,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var controller: MediaController? = null
    private var connecting = false

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** The song that is loaded now (playing or paused), or null. */
    val currentSong: StateFlow<Song?> = _state
        .map { it.currentId }
        .distinctUntilChanged()
        .map { id -> if (id < 0) null else repo.songById(id) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The queue as full songs, in queue order. */
    val queueSongs: StateFlow<List<Song>> = _state
        .map { it.queueIds }
        .distinctUntilChanged()
        .map { ids -> repo.songsByIds(ids) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(player, queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(error = "A file couldn't be played, so it was skipped.") }
        }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                refresh(c, queueChanged = true)
            } catch (e: Exception) {
                _state.update { it.copy(error = "Could not start the music player.") }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh(p: Player, queueChanged: Boolean) {
        val ids = if (queueChanged) {
            List(p.mediaItemCount) { p.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
        } else {
            _state.value.queueIds
        }
        val duration = p.duration
        _state.update { old ->
            old.copy(
                connected = true,
                currentId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L,
                index = p.currentMediaItemIndex,
                queueIds = ids,
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                durationMs = if (duration == C.TIME_UNSET) 0L else duration,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                speed = p.playbackParameters.speed
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Current position, refreshed 4x a second while playing. Only collect it on screens that show it. */
    fun positionFlow(): Flow<Long> = flow {
        while (true) {
            val c = controller
            emit(c?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            delay(if (c?.isPlaying == true) 250L else 1000L)
        }
    }.flowOn(Dispatchers.Main)

    // ---------------------------------------------------------------- start playback

    /** Plays [songs] starting at [startIndex]. Very long lists are trimmed to a 2000-song window. */
    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val (window, index) = windowOf(songs, startIndex.coerceIn(0, songs.lastIndex))
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(window.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    /** Shuffle-plays a list from a random song. */
    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.indices.random(), shuffle = true)
    }

    private fun windowOf(songs: List<Song>, index: Int): Pair<List<Song>, Int> {
        if (songs.size <= MAX_QUEUE) return songs to index
        val start = (index - 500).coerceAtLeast(0).coerceAtMost(songs.size - MAX_QUEUE)
        return songs.subList(start, start + MAX_QUEUE) to (index - start)
    }

    // ---------------------------------------------------------------- queue

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playSongs(listOf(song), 0)
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        }
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) playSongs(listOf(song), 0) else c.addMediaItem(song.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        val last = c.mediaItemCount - 1
        if (from in 0..last && to in 0..last && from != to) c.moveMediaItem(from, to)
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    // ---------------------------------------------------------------- transport

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition(0)
                else -> Unit
            }
            c.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    /** Restarts the song if it has played for a few seconds, otherwise goes to the previous song. */
    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Playback speed 0.5x-2x. Saved in settings; the service applies it. */
    fun setSpeed(speed: Float) {
        val clean = speed.coerceIn(0.5f, 2f)
        scope.launch { settingsStore.edit { it[SettingsStore.Keys.SPEED] = clean } }
    }

    private companion object {
        const val MAX_QUEUE = 2000
    }
}
