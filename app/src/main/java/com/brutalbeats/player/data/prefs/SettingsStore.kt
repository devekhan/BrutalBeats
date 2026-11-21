package com.brutalbeats.player.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

/** User-facing settings (theme, library rules, playback options, profile). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val highContrast: Boolean = false,
    val accentArgb: Int = 0xFFFFD93D.toInt(),
    val glassIntensity: Float = 0.6f,
    val minTrackSeconds: Int = 30,
    val crossfadeSeconds: Int = 0,
    val fadeOnPause: Boolean = true,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
    val playbackSpeed: Float = 1f,
    val songSort: SongSort = SongSort.TITLE,
    val sortAscending: Boolean = true,
    val userName: String = "",
    val avatarPath: String? = null
)

/**
 * Equalizer and sound effects.
 * [presetKey] is a built-in preset name, "custom:<id>" for a saved preset, or "manual".
 * [bandLevelsCsv] holds one millibel value per band.
 */
data class EqSettings(
    val enabled: Boolean = false,
    val presetKey: String = "Flat",
    val bandLevelsCsv: String = "",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessGainMb: Int = 0,
    val reverbPreset: Int = 0
)

/** Last playback session, used to resume after the app restarts. repeatMode uses Media3's values. */
data class SessionState(
    val songId: Long = -1L,
    val positionMs: Long = 0L,
    val index: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brutal_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val ACCENT = intPreferencesKey("accent_argb")
        val GLASS = floatPreferencesKey("glass_intensity")
        val MIN_TRACK = intPreferencesKey("min_track_seconds")
        val CROSSFADE = intPreferencesKey("crossfade_seconds")
        val FADE_ON_PAUSE = booleanPreferencesKey("fade_on_pause")
        val INCLUDED = stringSetPreferencesKey("included_folders")
        val EXCLUDED = stringSetPreferencesKey("excluded_folders")
        val SPEED = floatPreferencesKey("playback_speed")
        val SORT = stringPreferencesKey("song_sort")
        val SORT_ASC = booleanPreferencesKey("sort_ascending")
        val USER_NAME = stringPreferencesKey("user_name")
        val AVATAR = stringPreferencesKey("avatar_path")

        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_LEVELS = stringPreferencesKey("eq_levels")
        val EQ_BASS = intPreferencesKey("eq_bass")
        val EQ_VIRT = intPreferencesKey("eq_virtualizer")
        val EQ_LOUD = intPreferencesKey("eq_loudness")
        val EQ_REVERB = intPreferencesKey("eq_reverb")

        val SESSION_SONG = longPreferencesKey("session_song")
        val SESSION_POS = longPreferencesKey("session_position")
        val SESSION_INDEX = intPreferencesKey("session_index")
        val SESSION_SHUFFLE = booleanPreferencesKey("session_shuffle")
        val SESSION_REPEAT = intPreferencesKey("session_repeat")
    }

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
            highContrast = p[Keys.HIGH_CONTRAST] ?: false,
            accentArgb = p[Keys.ACCENT] ?: 0xFFFFD93D.toInt(),
            glassIntensity = p[Keys.GLASS] ?: 0.6f,
            minTrackSeconds = p[Keys.MIN_TRACK] ?: 30,
            crossfadeSeconds = p[Keys.CROSSFADE] ?: 0,
            fadeOnPause = p[Keys.FADE_ON_PAUSE] ?: true,
            includedFolders = p[Keys.INCLUDED] ?: emptySet(),
            excludedFolders = p[Keys.EXCLUDED] ?: emptySet(),
            playbackSpeed = p[Keys.SPEED] ?: 1f,
            songSort = p[Keys.SORT].toEnum(SongSort.TITLE),
            sortAscending = p[Keys.SORT_ASC] ?: true,
            userName = p[Keys.USER_NAME] ?: "",
            avatarPath = p[Keys.AVATAR]
        )
    }.distinctUntilChanged()

    val eq: Flow<EqSettings> = data.map { p ->
        EqSettings(
            enabled = p[Keys.EQ_ENABLED] ?: false,
            presetKey = p[Keys.EQ_PRESET] ?: "Flat",
            bandLevelsCsv = p[Keys.EQ_LEVELS] ?: "",
            bassBoost = p[Keys.EQ_BASS] ?: 0,
            virtualizer = p[Keys.EQ_VIRT] ?: 0,
            loudnessGainMb = p[Keys.EQ_LOUD] ?: 0,
            reverbPreset = p[Keys.EQ_REVERB] ?: 0
        )
    }.distinctUntilChanged()

    val session: Flow<SessionState> = data.map { p ->
        SessionState(
            songId = p[Keys.SESSION_SONG] ?: -1L,
            positionMs = p[Keys.SESSION_POS] ?: 0L,
            index = p[Keys.SESSION_INDEX] ?: 0,
            shuffle = p[Keys.SESSION_SHUFFLE] ?: false,
            repeatMode = p[Keys.SESSION_REPEAT] ?: 0
        )
    }.distinctUntilChanged()

    /** Change any stored values: store.edit { it[SettingsStore.Keys.GLASS] = 0.8f } */
    suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun saveSession(state: SessionState) = edit {
        it[Keys.SESSION_SONG] = state.songId
        it[Keys.SESSION_POS] = state.positionMs
        it[Keys.SESSION_INDEX] = state.index
        it[Keys.SESSION_SHUFFLE] = state.shuffle
        it[Keys.SESSION_REPEAT] = state.repeatMode
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { s -> enumValues<T>().firstOrNull { it.name == s } } ?: default
}
