package com.brutalbeats.player.ui.screens.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brutalbeats.player.data.db.EqPreset
import com.brutalbeats.player.data.db.EqPresetDao
import com.brutalbeats.player.data.prefs.EqSettings
import com.brutalbeats.player.data.prefs.SettingsStore
import com.brutalbeats.player.playback.AudioEffectsManager
import com.brutalbeats.player.playback.EffectCaps
import com.brutalbeats.player.playback.EqPresets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reads a saved list of band levels, or flat zeros if it doesn't match the band count. */
fun parseBandLevels(csv: String, bands: Int): List<Int> {
    val parsed = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (parsed.size == bands) parsed else List(bands) { 0 }
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val store: SettingsStore,
    private val effects: AudioEffectsManager,
    private val presetDao: EqPresetDao
) : ViewModel() {

    val eq: StateFlow<EqSettings> =
        store.eq.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqSettings())
    val caps: StateFlow<EffectCaps> = effects.caps
    val customPresets: StateFlow<List<EqPreset>> = presetDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Applies settings to the audio right now (while a slider moves) without saving. */
    fun preview(settings: EqSettings) = effects.apply(settings)

    /** Applies and saves. The music service picks the saved values up too. */
    fun commit(settings: EqSettings) {
        effects.apply(settings)
        viewModelScope.launch {
            store.edit {
                it[SettingsStore.Keys.EQ_ENABLED] = settings.enabled
                it[SettingsStore.Keys.EQ_PRESET] = settings.presetKey
                it[SettingsStore.Keys.EQ_LEVELS] = settings.bandLevelsCsv
                it[SettingsStore.Keys.EQ_BASS] = settings.bassBoost
                it[SettingsStore.Keys.EQ_VIRT] = settings.virtualizer
                it[SettingsStore.Keys.EQ_LOUD] = settings.loudnessGainMb
                it[SettingsStore.Keys.EQ_REVERB] = settings.reverbPreset
            }
        }
    }

    fun applyBuiltIn(name: String) {
        val levels = EqPresets.levels(name, caps.value)
        commit(eq.value.copy(presetKey = name, bandLevelsCsv = levels.joinToString(",")))
    }

    fun applyCustom(preset: EqPreset) {
        val bands = caps.value.bandCount
        val levels = parseBandLevels(preset.levelsCsv, bands)
        commit(eq.value.copy(presetKey = "custom:${preset.id}", bandLevelsCsv = levels.joinToString(",")))
    }

    /** Saves the current curve as a named preset and selects it. */
    fun saveCustom(name: String) {
        val clean = name.trim().ifEmpty { "My preset" }
        val bands = caps.value.bandCount
        val levels = parseBandLevels(eq.value.bandLevelsCsv, bands)
        viewModelScope.launch {
            val id = presetDao.insert(EqPreset(name = clean, bandCount = bands, levelsCsv = levels.joinToString(",")))
            commit(eq.value.copy(presetKey = "custom:$id", bandLevelsCsv = levels.joinToString(",")))
        }
    }

    fun renameCustom(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) viewModelScope.launch { presetDao.rename(id, clean) }
    }

    fun deleteCustom(preset: EqPreset) {
        viewModelScope.launch {
            presetDao.delete(preset.id)
            if (eq.value.presetKey == "custom:${preset.id}") {
                commit(eq.value.copy(presetKey = "manual"))
            }
        }
    }

    fun reset() {
        val flat = EqPresets.levels("Flat", caps.value)
        commit(
            EqSettings(
                enabled = eq.value.enabled,
                presetKey = "Flat",
                bandLevelsCsv = flat.joinToString(","),
                bassBoost = 0,
                virtualizer = 0,
                loudnessGainMb = 0,
                reverbPreset = 0
            )
        )
    }
}
