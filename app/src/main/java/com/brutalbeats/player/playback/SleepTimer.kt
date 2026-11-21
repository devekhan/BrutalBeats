package com.brutalbeats.player.playback

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.brutalbeats.player.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepState {
    data object Off : SleepState

    /** [endAtMs] is on the SystemClock.elapsedRealtime() clock. */
    data class Countdown(val endAtMs: Long, val totalMs: Long) : SleepState

    /** Pause when the current song ends. */
    data object FinishSong : SleepState
}

/**
 * Sleep timer. Lives for the whole process and talks to the player the service attaches,
 * so it keeps working with the app closed. Volume fades out slowly before it stops.
 */
@Singleton
class SleepTimer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state.asStateFlow()

    /** 1.0 normally, ramps to 0 during the last seconds. The service multiplies it into the volume. */
    @Volatile
    var volumeFactor: Float = 1f
        private set

    private var player: ExoPlayer? = null
    private var job: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value is SleepState.FinishSong
            ) {
                player?.pauseAtEndOfMediaItems = false
                _state.value = SleepState.Off
            }
        }
    }

    fun attach(exo: ExoPlayer) {
        player = exo
        exo.addListener(listener)
    }

    fun detach() {
        player?.removeListener(listener)
        cancel()
        player = null
    }

    /** Stop after [minutes]. Presets and a custom value both use this. */
    fun startMinutes(minutes: Int) {
        cancelInternal()
        val total = minutes.coerceIn(1, 24 * 60) * 60_000L
        _state.value = SleepState.Countdown(SystemClock.elapsedRealtime() + total, total)
        job = scope.launch(Dispatchers.Main) { runCountdown() }
    }

    /** Add time to a running timer (starts a new one if none is running). */
    fun extend(minutes: Int) {
        val s = _state.value
        if (s is SleepState.Countdown) {
            val add = minutes * 60_000L
            _state.value = SleepState.Countdown(s.endAtMs + add, s.totalMs + add)
        } else {
            startMinutes(minutes)
        }
    }

    fun finishCurrentSong() {
        cancelInternal()
        player?.pauseAtEndOfMediaItems = true
        _state.value = SleepState.FinishSong
    }

    fun cancel() = cancelInternal()

    /** Milliseconds left on a countdown, or 0. */
    fun remainingMs(): Long {
        val s = _state.value
        return if (s is SleepState.Countdown) {
            (s.endAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else 0L
    }

    private fun cancelInternal() {
        job?.cancel()
        job = null
        volumeFactor = 1f
        player?.pauseAtEndOfMediaItems = false
        _state.value = SleepState.Off
    }

    private suspend fun runCountdown() {
        while (true) {
            val s = _state.value as? SleepState.Countdown ?: break
            val remaining = s.endAtMs - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                player?.pause()
                volumeFactor = 1f
                _state.value = SleepState.Off
                break
            }
            volumeFactor =
                if (remaining < FADE_MS) (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f) else 1f
            delay(if (remaining < FADE_MS) 100L else 1000L)
        }
    }

    private companion object {
        const val FADE_MS = 15_000L
    }
}
