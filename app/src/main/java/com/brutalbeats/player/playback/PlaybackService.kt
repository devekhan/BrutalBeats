package com.brutalbeats.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.brutalbeats.player.MainActivity
import com.brutalbeats.player.R
import com.brutalbeats.player.data.db.QueueDao
import com.brutalbeats.player.data.prefs.SessionState
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.data.repo.LibraryRepository
import com.brutalbeats.player.di.ApplicationScope
import com.brutalbeats.player.output.OutputDeviceMonitor
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.min

/**
 * The music engine. Runs as a foreground media service so playback continues with the app closed
 * and the screen off, and drives the notification, lock screen, headset and car controls.
 *
 * Owns: ExoPlayer, the MediaSession, audio effects, fade-through crossfade, fade on pause,
 * sleep timer hookup, play counting, and saving/restoring the queue.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var repo: LibraryRepository
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var effects: AudioEffectsManager
    @Inject lateinit var sleepTimer: SleepTimer
    @Inject lateinit var outputMonitor: OutputDeviceMonitor
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    private lateinit var exo: ExoPlayer
    private lateinit var fadingPlayer: FadingPlayer
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Volume = crossfade factor x sleep-timer factor x pause-fade factor.
    private var crossfadeMs = 0L
    private var fadeOnPause = true
    private var crossFactor = 1f
    private var pauseFactor = 1f
    private var fadeJob: Job? = null
    private var pauseJob: Job? = null
    private var persistJob: Job? = null
    private var positionJob: Job? = null

    // Play counting: a listen counts after 30s or half the song, whichever comes first.
    private var trackId = -1L
    private var trackListenedMs = 0L
    private var trackPlayingSince = 0L
    private var trackDurationMs = 0L

    override fun onCreate() {
        super.onCreate()

        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        fadingPlayer = FadingPlayer(exo)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, fadingPlayer)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setBitmapLoader(ArtBitmapLoader(this, scope))
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_stat_music) }
        )

        exo.addListener(playerListener)
        effects.attach(exo.audioSessionId)
        sleepTimer.attach(exo)
        outputMonitor.attachPlayer(exo)
        outputMonitor.start()

        scope.launch {
            settingsStore.settings.collect { s ->
                crossfadeMs = s.crossfadeSeconds * 1000L
                fadeOnPause = s.fadeOnPause
                if (exo.playbackParameters.speed != s.playbackSpeed) exo.setPlaybackSpeed(s.playbackSpeed)
            }
        }
        scope.launch { settingsStore.eq.collect { effects.apply(it) } }
        scope.launch { repo.favoriteIds.collect { updateCustomLayout() } }

        restoreSession()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Keep playing if music is playing; otherwise shut the service down. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        persistPosition()
        val p = exo
        if (!p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        finalizeTrack()
        persistPosition()
        outputMonitor.attachPlayer(null)
        sleepTimer.detach()
        effects.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ player events

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION
                )
            ) updateCustomLayout()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) schedulePersistQueue()
            if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                persistPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            finalizeTrack()
            beginTrack()
            persistPosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (isPlaying) {
                if (trackId < 0) beginTrack()
                else if (trackPlayingSince == 0L) trackPlayingSince = now
                if (trackDurationMs <= 0L) trackDurationMs = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            } else if (trackPlayingSince != 0L) {
                trackListenedMs += now - trackPlayingSince
                trackPlayingSince = 0L
            }
            restartFadeLoop()
            restartPositionSaver()
            if (!isPlaying) persistPosition()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            effects.attach(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            // Corrupt, unsupported or deleted file: skip it instead of getting stuck.
            if (exo.hasNextMediaItem()) {
                exo.seekToNextMediaItem()
                exo.prepare()
                exo.play()
            }
        }
    }

    // ------------------------------------------------------------------ fades

    private fun applyVolume() {
        exo.volume = (crossFactor * sleepTimer.volumeFactor * pauseFactor).coerceIn(0f, 1f)
    }

    /**
     * Runs only while playing. Fades out near the end of a song and in at the start of the next
     * (a "fade-through" crossfade: the dip lasts the chosen number of seconds in total).
     */
    private fun restartFadeLoop() {
        fadeJob?.cancel()
        if (!exo.isPlaying) return
        fadeJob = scope.launch {
            while (isActive) {
                val half = crossfadeMs / 2
                var factor = 1f
                val duration = exo.duration
                if (half > 0 && duration != C.TIME_UNSET && duration > 0) {
                    val pos = exo.currentPosition
                    val remaining = duration - pos
                    val hasNext = exo.hasNextMediaItem() || exo.repeatMode != Player.REPEAT_MODE_OFF
                    if (hasNext && remaining < half) {
                        factor = min(factor, (remaining.toFloat() / half).coerceIn(0f, 1f))
                    }
                    if (pos < half) {
                        factor = min(factor, (pos.toFloat() / half).coerceIn(0f, 1f))
                    }
                }
                crossFactor = factor
                applyVolume()
                val busy = factor < 1f || sleepTimer.volumeFactor < 1f
                delay(
                    when {
                        busy -> 80L
                        half > 0 -> 400L
                        else -> 1000L
                    }
                )
            }
        }
    }

    private suspend fun ramp(from: Float, to: Float, ms: Long) {
        val steps = 12
        for (i in 1..steps) {
            pauseFactor = from + (to - from) * i / steps
            applyVolume()
            delay(ms / steps)
        }
    }

    /** Wraps the player so pause/play can fade smoothly (setting "Fade on pause"). */
    private inner class FadingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun play() {
            if (!fadeOnPause) {
                super.play()
                return
            }
            pauseJob?.cancel()
            pauseFactor = 0f
            applyVolume()
            super.play()
            pauseJob = scope.launch { ramp(0f, 1f, 300L) }
        }

        override fun pause() {
            if (!fadeOnPause || !isPlaying) {
                super.pause()
                return
            }
            pauseJob?.cancel()
            pauseJob = scope.launch {
                ramp(pauseFactor, 0f, 300L)
                super@FadingPlayer.pause()
                pauseFactor = 1f
                applyVolume()
            }
        }
    }

    // ------------------------------------------------------------------ play counting

    private fun beginTrack() {
        trackId = exo.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        trackDurationMs = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        trackListenedMs = 0L
        trackPlayingSince = if (exo.isPlaying) SystemClock.elapsedRealtime() else 0L
    }

    private fun finalizeTrack() {
        if (trackPlayingSince != 0L) {
            trackListenedMs += SystemClock.elapsedRealtime() - trackPlayingSince
            trackPlayingSince = 0L
        }
        val id = trackId
        val threshold = min(30_000L, if (trackDurationMs > 0) trackDurationMs / 2 else 30_000L)
        if (id >= 0 && trackListenedMs >= threshold) {
            val listened = trackListenedMs
            appScope.launch(Dispatchers.IO) { repo.recordPlay(id, listened) }
        }
        trackId = -1L
        trackListenedMs = 0L
    }

    // ------------------------------------------------------------------ save / restore

    private fun schedulePersistQueue() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(800)
            if (exo.mediaItemCount == 0) return@launch // never overwrite a saved queue with nothing
            val ids = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1L }
                .filter { it >= 0 }
            withContext(Dispatchers.IO) { queueDao.replace(ids) }
        }
    }

    private fun persistPosition() {
        if (exo.mediaItemCount == 0) return
        val id = exo.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val state = SessionState(
            songId = id,
            positionMs = exo.currentPosition.coerceAtLeast(0L),
            index = exo.currentMediaItemIndex,
            shuffle = exo.shuffleModeEnabled,
            repeatMode = exo.repeatMode
        )
        appScope.launch(Dispatchers.IO) { settingsStore.saveSession(state) }
    }

    /** While playing, save the position every 15 seconds so a crash loses almost nothing. */
    private fun restartPositionSaver() {
        positionJob?.cancel()
        if (!exo.isPlaying) return
        positionJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                persistPosition()
            }
        }
    }

    /** Puts the last queue back (paused) so the mini player shows what you were playing. */
    private fun restoreSession() {
        scope.launch {
            val saved = settingsStore.session.first()
            val ids = withContext(Dispatchers.IO) { queueDao.getAll() }
            if (ids.isEmpty() || exo.mediaItemCount > 0) return@launch
            val songs = withContext(Dispatchers.IO) { repo.songsByIds(ids) }
            if (songs.isEmpty() || exo.mediaItemCount > 0) return@launch
            var index = songs.indexOfFirst { it.id == saved.songId }
            val position = if (index >= 0) saved.positionMs else 0L
            if (index < 0) index = 0
            exo.setMediaItems(songs.map { it.toMediaItem() }, index, position)
            exo.shuffleModeEnabled = saved.shuffle
            exo.repeatMode = saved.repeatMode
            exo.prepare() // playWhenReady stays false, so it waits for you to press play
        }
    }

    // ------------------------------------------------------------------ notification buttons

    private fun buildLayout(): List<CommandButton> {
        val shuffleOn = exo.shuffleModeEnabled
        val repeat = exo.repeatMode
        val currentId = exo.currentMediaItem?.mediaId?.toLongOrNull()
        val isFavorite = currentId != null && currentId in repo.favoriteIds.value

        val favorite = CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Remove favorite" else "Favorite")
            .setIconResId(if (isFavorite) R.drawable.ic_notif_heart_on else R.drawable.ic_notif_heart_off)
            .setSessionCommand(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
            .build()
        val shuffle = CommandButton.Builder()
            .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
            .setIconResId(if (shuffleOn) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .build()
        val repeatButton = CommandButton.Builder()
            .setDisplayName(
                when (repeat) {
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    else -> "Repeat off"
                }
            )
            .setIconResId(
                when (repeat) {
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_notif_repeat_all
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_notif_repeat_one
                    else -> R.drawable.ic_notif_repeat_off
                }
            )
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .build()
        return listOf(favorite, shuffle, repeatButton)
    }

    private fun updateCustomLayout() {
        session?.setCustomLayout(buildLayout())
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(CMD_FAVORITE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(ImmutableList.copyOf(buildLayout()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> exo.shuffleModeEnabled = !exo.shuffleModeEnabled
                CMD_REPEAT -> exo.repeatMode = when (exo.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                CMD_FAVORITE -> {
                    val id = exo.currentMediaItem?.mediaId?.toLongOrNull()
                    if (id != null) appScope.launch(Dispatchers.IO) { repo.toggleFavorite(id) }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private companion object {
        const val CHANNEL_ID = "brutal_beats_playback"
        const val CMD_SHUFFLE = "com.brutalbeats.player.SHUFFLE"
        const val CMD_REPEAT = "com.brutalbeats.player.REPEAT"
        const val CMD_FAVORITE = "com.brutalbeats.player.FAVORITE"
    }
}
