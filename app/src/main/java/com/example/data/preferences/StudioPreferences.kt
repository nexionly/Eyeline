package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eyeline_studio_preferences"
)

enum class RimLightTone {
    WARM,
    NEUTRAL,
    COOL,
}

data class PrompterSettings(
    val fontSizeSp: Float = 28f,
    val scrollSpeedLevel: Float = 3.0f,
    val backgroundOpacity: Float = 0.56f,
    val showFramingGrid: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val rimLightEnabled: Boolean = false,
    val rimLightTone: RimLightTone = RimLightTone.WARM,
    val rimLightIntensity: Float = 0.72f,
    val volumeDownShutterEnabled: Boolean = true,
    val countdownSoundsEnabled: Boolean = true,
    val hasSeededStarter: Boolean = false,
)

class StudioPreferencesRepository(
    private val context: Context,
) {
    private object Keys {
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val SCROLL_SPEED_LEVEL = floatPreferencesKey("scroll_speed_level")
        val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        val SHOW_FRAMING_GRID = booleanPreferencesKey("show_framing_grid")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RIM_LIGHT_ENABLED = booleanPreferencesKey("rim_light_enabled")
        val RIM_LIGHT_TONE = stringPreferencesKey("rim_light_tone")
        val RIM_LIGHT_INTENSITY = floatPreferencesKey("rim_light_intensity")
        val VOLUME_DOWN_SHUTTER = booleanPreferencesKey("volume_down_shutter_enabled")
        val COUNTDOWN_SOUNDS = booleanPreferencesKey("countdown_sounds_enabled")
        val HAS_SEEDED_STARTER = booleanPreferencesKey("has_seeded_starter")
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val persistedFlow: Flow<PrompterSettings> = context.studioDataStore.data.map { prefs ->
        PrompterSettings(
            fontSizeSp = (prefs[Keys.FONT_SIZE_SP] ?: 28f).coerceIn(18f, 44f),
            scrollSpeedLevel = (prefs[Keys.SCROLL_SPEED_LEVEL] ?: 3.0f).coerceIn(1f, 10f),
            backgroundOpacity = (prefs[Keys.BACKGROUND_OPACITY] ?: 0.56f).coerceIn(0f, 0.90f),
            showFramingGrid = prefs[Keys.SHOW_FRAMING_GRID] ?: true,
            themeMode = prefs[Keys.THEME_MODE]?.let { raw ->
                runCatching { AppThemeMode.valueOf(raw) }.getOrNull()
            } ?: AppThemeMode.DARK,
            rimLightEnabled = prefs[Keys.RIM_LIGHT_ENABLED] ?: false,
            rimLightTone = prefs[Keys.RIM_LIGHT_TONE]?.let { raw ->
                runCatching { RimLightTone.valueOf(raw) }.getOrNull()
            } ?: RimLightTone.WARM,
            rimLightIntensity = (prefs[Keys.RIM_LIGHT_INTENSITY] ?: 0.72f).coerceIn(0.35f, 1f),
            volumeDownShutterEnabled = prefs[Keys.VOLUME_DOWN_SHUTTER] ?: true,
            countdownSoundsEnabled = prefs[Keys.COUNTDOWN_SOUNDS] ?: true,
            hasSeededStarter = prefs[Keys.HAS_SEEDED_STARTER] ?: false,
        )
    }

    private val _cachedSettings = MutableStateFlow(PrompterSettings())
    val settingsState: StateFlow<PrompterSettings> = _cachedSettings.asStateFlow()
    val settingsFlow: Flow<PrompterSettings> = persistedFlow
    val currentSettings: PrompterSettings get() = _cachedSettings.value

    init {
        repoScope.launch {
            persistedFlow.collect { latest ->
                _cachedSettings.value = latest
            }
        }
    }

    suspend fun updateFontSize(fontSizeSp: Float) {
        val clamped = fontSizeSp.coerceIn(18f, 44f)
        _cachedSettings.update { it.copy(fontSizeSp = clamped) }
        context.studioDataStore.edit { it[Keys.FONT_SIZE_SP] = clamped }
    }

    suspend fun updateScrollSpeed(speedLevel: Float) {
        val clamped = speedLevel.coerceIn(1f, 10f)
        _cachedSettings.update { it.copy(scrollSpeedLevel = clamped) }
        context.studioDataStore.edit { it[Keys.SCROLL_SPEED_LEVEL] = clamped }
    }

    suspend fun updateBackgroundOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 0.90f)
        _cachedSettings.update { it.copy(backgroundOpacity = clamped) }
        context.studioDataStore.edit { it[Keys.BACKGROUND_OPACITY] = clamped }
    }

    suspend fun setFramingGrid(enabled: Boolean) {
        _cachedSettings.update { it.copy(showFramingGrid = enabled) }
        context.studioDataStore.edit { it[Keys.SHOW_FRAMING_GRID] = enabled }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        _cachedSettings.update { it.copy(themeMode = mode) }
        context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setRimLightEnabled(enabled: Boolean) {
        _cachedSettings.update { it.copy(rimLightEnabled = enabled) }
        context.studioDataStore.edit { it[Keys.RIM_LIGHT_ENABLED] = enabled }
    }

    suspend fun setRimLightTone(tone: RimLightTone) {
        _cachedSettings.update { it.copy(rimLightTone = tone) }
        context.studioDataStore.edit { it[Keys.RIM_LIGHT_TONE] = tone.name }
    }

    suspend fun updateRimLightIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.35f, 1f)
        _cachedSettings.update { it.copy(rimLightIntensity = clamped) }
        context.studioDataStore.edit { it[Keys.RIM_LIGHT_INTENSITY] = clamped }
    }

    suspend fun setVolumeDownShutterEnabled(enabled: Boolean) {
        _cachedSettings.update { it.copy(volumeDownShutterEnabled = enabled) }
        context.studioDataStore.edit { it[Keys.VOLUME_DOWN_SHUTTER] = enabled }
    }

    suspend fun setCountdownSoundsEnabled(enabled: Boolean) {
        _cachedSettings.update { it.copy(countdownSoundsEnabled = enabled) }
        context.studioDataStore.edit { it[Keys.COUNTDOWN_SOUNDS] = enabled }
    }

    suspend fun markStarterSeeded() {
        _cachedSettings.update { it.copy(hasSeededStarter = true) }
        context.studioDataStore.edit { it[Keys.HAS_SEEDED_STARTER] = true }
    }
}
