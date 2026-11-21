package com.brutalbeats.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.brutalbeats.player.data.prefs.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What this phone's audio hardware supports. The equalizer screen is built from this. */
data class EffectCaps(
    val equalizer: Boolean = false,
    val bandCount: Int = 0,
    val centerFreqsHz: List<Int> = emptyList(),
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val loudness: Boolean = false,
    val reverb: Boolean = false
)

/** Built-in equalizer presets, stretched to however many bands the device has. */
object EqPresets {
    val names = listOf(
        "Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Classical", "Vocal",
        "Treble Boost", "Dance", "Hip-Hop", "Electronic", "Acoustic", "Loudness"
    )

    // Five anchor points from low to high frequency, each -1..1.
    private val curves: Map<String, FloatArray> = mapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to floatArrayOf(0.9f, 0.6f, 0f, 0f, 0f),
        "Rock" to floatArrayOf(0.7f, 0.3f, -0.4f, 0.3f, 0.6f),
        "Pop" to floatArrayOf(-0.2f, 0.4f, 0.6f, 0.3f, -0.2f),
        "Jazz" to floatArrayOf(0.4f, 0.2f, -0.2f, 0.2f, 0.5f),
        "Classical" to floatArrayOf(0.5f, 0.3f, -0.1f, 0.3f, 0.6f),
        "Vocal" to floatArrayOf(-0.4f, -0.1f, 0.6f, 0.5f, 0.1f),
        "Treble Boost" to floatArrayOf(0f, 0f, 0.2f, 0.6f, 0.9f),
        "Dance" to floatArrayOf(0.8f, 0.4f, 0f, 0.4f, 0.5f),
        "Hip-Hop" to floatArrayOf(0.7f, 0.5f, -0.1f, 0.2f, 0.3f),
        "Electronic" to floatArrayOf(0.6f, 0.2f, -0.3f, 0.3f, 0.7f),
        "Acoustic" to floatArrayOf(0.5f, 0.3f, 0.1f, 0.3f, 0.4f),
        "Loudness" to floatArrayOf(0.8f, 0.2f, -0.2f, 0.2f, 0.8f)
    )

    /** Band levels (millibels) for a preset on this device. */
    fun levels(name: String, caps: EffectCaps): List<Int> {
        val n = caps.bandCount
        if (n == 0) return emptyList()
        val curve = curves[name] ?: curves.getValue("Flat")
        val scale = caps.maxLevelMb * 0.7f
        return List(n) { i ->
            val pos = if (n == 1) 0f else i.toFloat() / (n - 1) * (curve.size - 1)
            val lo = pos.toInt().coerceAtMost(curve.size - 2)
            val frac = pos - lo
            val v = curve[lo] * (1f - frac) + curve[lo + 1] * frac
            (v * scale).toInt().coerceIn(caps.minLevelMb, caps.maxLevelMb)
        }
    }
}

/**
 * Owns the Android audio effects attached to the player's audio session.
 * Every effect is created in a try/catch, so a phone that lacks one simply skips it.
 * Settings are re-applied automatically whenever the audio session changes (so on every song).
 */
@Singleton
class AudioEffectsManager @Inject constructor() {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null

    private val _caps = MutableStateFlow(EffectCaps())
    val caps: StateFlow<EffectCaps> = _caps.asStateFlow()

    private var last = EqSettings()

    @Synchronized
    fun attach(sessionId: Int) {
        releaseLocked()
        if (sessionId == 0) return

        equalizer = try { Equalizer(0, sessionId) } catch (e: Exception) { null }
        bassBoost = try { BassBoost(0, sessionId) } catch (e: Exception) { null }
        virtualizer = try { Virtualizer(0, sessionId) } catch (e: Exception) { null }
        loudness = try { LoudnessEnhancer(sessionId) } catch (e: Exception) { null }
        reverb = try { PresetReverb(0, sessionId) } catch (e: Exception) { null }

        var bands = 0
        var freqs = emptyList<Int>()
        var minMb = -1500
        var maxMb = 1500
        equalizer?.let { e ->
            try {
                bands = e.numberOfBands.toInt()
                val range = e.bandLevelRange
                minMb = range[0].toInt()
                maxMb = range[1].toInt()
                freqs = List(bands) { b -> e.getCenterFreq(b.toShort()) / 1000 }
            } catch (ex: Exception) {
                bands = 0
            }
        }
        _caps.value = EffectCaps(
            equalizer = equalizer != null && bands > 0,
            bandCount = bands,
            centerFreqsHz = freqs,
            minLevelMb = minMb,
            maxLevelMb = maxMb,
            bassBoost = bassBoost?.strengthSupported == true,
            virtualizer = virtualizer?.strengthSupported == true,
            loudness = loudness != null,
            reverb = reverb != null
        )
        applyLocked(last)
    }

    @Synchronized
    fun apply(settings: EqSettings) {
        last = settings
        applyLocked(settings)
    }

    @Synchronized
    fun release() = releaseLocked()

    private fun applyLocked(s: EqSettings) {
        val caps = _caps.value
        equalizer?.let { e ->
            try {
                e.setEnabled(s.enabled)
                if (s.enabled && caps.bandCount > 0) {
                    val levels = parseLevels(s.bandLevelsCsv, caps.bandCount)
                    for (b in 0 until caps.bandCount) {
                        e.setBandLevel(
                            b.toShort(),
                            levels[b].coerceIn(caps.minLevelMb, caps.maxLevelMb).toShort()
                        )
                    }
                }
            } catch (ex: Exception) {
            }
        }
        bassBoost?.let { b ->
            try {
                if (b.strengthSupported) b.setStrength(s.bassBoost.coerceIn(0, 1000).toShort())
                b.setEnabled(s.enabled && s.bassBoost > 0)
            } catch (ex: Exception) {
            }
        }
        virtualizer?.let { v ->
            try {
                if (v.strengthSupported) v.setStrength(s.virtualizer.coerceIn(0, 1000).toShort())
                v.setEnabled(s.enabled && s.virtualizer > 0)
            } catch (ex: Exception) {
            }
        }
        loudness?.let { l ->
            try {
                val gain = s.loudnessGainMb.coerceIn(0, 1500)
                l.setTargetGain(gain)
                l.setEnabled(s.enabled && gain > 0)
            } catch (ex: Exception) {
            }
        }
        reverb?.let { r ->
            try {
                r.setPreset(s.reverbPreset.coerceIn(0, 6).toShort())
                r.setEnabled(s.enabled && s.reverbPreset > 0)
            } catch (ex: Exception) {
            }
        }
    }

    private fun parseLevels(csv: String, bands: Int): List<Int> {
        val parsed = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parsed.size == bands) parsed else List(bands) { 0 }
    }

    private fun releaseLocked() {
        try { equalizer?.release() } catch (e: Exception) { }
        try { bassBoost?.release() } catch (e: Exception) { }
        try { virtualizer?.release() } catch (e: Exception) { }
        try { loudness?.release() } catch (e: Exception) { }
        try { reverb?.release() } catch (e: Exception) { }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        reverb = null
    }
}
