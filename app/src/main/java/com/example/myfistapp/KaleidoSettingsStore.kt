package com.example.myfistapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kaleidoDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "kaleido_settings")

object KaleidoKeys {
    val FOLD_COUNT        = intPreferencesKey("fold_count")
    val SQUARE_ROT_LOCKED = booleanPreferencesKey("square_rot_locked")
    val FRAME_SHAPE       = stringPreferencesKey("frame_shape")
    val FRAME_COLOR_ARGB  = longPreferencesKey("frame_color_argb")
    val ZOOM_MULTIPLIER   = floatPreferencesKey("zoom_multiplier")
    val SHAPE_KIND        = stringPreferencesKey("shape_kind")
    val INVERT_COLORS     = booleanPreferencesKey("invert_colors")
    val COLORIZE_ENABLED       = booleanPreferencesKey("colorize_enabled")
    val COLORIZE_HUE           = floatPreferencesKey("colorize_hue")
    val DISTORTION_ENABLED     = booleanPreferencesKey("distortion_enabled")
    val DISTORTION_AMPLITUDE   = floatPreferencesKey("distortion_amplitude")
    val DISTORTION_FREQUENCY   = floatPreferencesKey("distortion_frequency")
    val PARTY_ENABLED          = booleanPreferencesKey("party_enabled")
    val RANDOM_ENABLED         = booleanPreferencesKey("random_enabled")
    val PARTY_INTENSITY        = floatPreferencesKey("party_intensity")
    val BASS_ZOOM_INTENSITY    = floatPreferencesKey("bass_zoom_intensity")
    val CONTRAST               = floatPreferencesKey("contrast")
    val CONTRAST_PASSES        = intPreferencesKey("contrast_passes")
    val SATURATION             = floatPreferencesKey("saturation")
    val DISTORTION_PLUS_ENABLED = booleanPreferencesKey("distortion_plus_enabled")
    val DISTORTION_PLUS_YAW    = floatPreferencesKey("distortion_plus_yaw")
    val DISTORTION_PLUS_PITCH  = floatPreferencesKey("distortion_plus_pitch")
    val DISTORTION_PLUS_ROLL   = floatPreferencesKey("distortion_plus_roll")
    val LOCKED_PARAMS           = stringPreferencesKey("locked_params")
    val IMMERSIVE_TOOLTIP_SHOWN = booleanPreferencesKey("immersive_tooltip_shown")
}

class KaleidoSettingsStore(private val context: Context) {

    val settingsFlow: Flow<KaleidoSettings> = context.kaleidoDataStore.data.map { prefs ->
        KaleidoSettings(
            foldCount            = (prefs[KaleidoKeys.FOLD_COUNT] ?: 12).coerceIn(2, 24),
            squareRotationLocked = prefs[KaleidoKeys.SQUARE_ROT_LOCKED] ?: false,
            frameShape           = prefs[KaleidoKeys.FRAME_SHAPE]
                ?.let { runCatching { FrameShape.valueOf(it) }.getOrNull() }
                ?: FrameShape.Circle,
            frameColorArgb       = prefs[KaleidoKeys.FRAME_COLOR_ARGB] ?: 0xFFFFFFFFL,
            zoomMultiplier       = (prefs[KaleidoKeys.ZOOM_MULTIPLIER] ?: 1.0f).coerceIn(0.5f, 1.5f),
            shapeKind            = prefs[KaleidoKeys.SHAPE_KIND]
                ?.let { runCatching { ShapeKind.valueOf(it) }.getOrNull() }
                ?: ShapeKind.Cylinder,
            invertColors         = prefs[KaleidoKeys.INVERT_COLORS] ?: false,
            colorizeEnabled      = prefs[KaleidoKeys.COLORIZE_ENABLED] ?: false,
            colorizeHue          = (prefs[KaleidoKeys.COLORIZE_HUE] ?: 0f).coerceIn(0f, 360f),
            distortionEnabled    = prefs[KaleidoKeys.DISTORTION_ENABLED] ?: false,
            distortionAmplitude  = (prefs[KaleidoKeys.DISTORTION_AMPLITUDE] ?: 0.3f).coerceIn(0f, 1f),
            distortionFrequency  = (prefs[KaleidoKeys.DISTORTION_FREQUENCY] ?: 2.0f).coerceIn(0.5f, 8.0f),
            partyEnabled         = prefs[KaleidoKeys.PARTY_ENABLED] ?: false,
            randomEnabled        = prefs[KaleidoKeys.RANDOM_ENABLED] ?: false,
            partyIntensity       = (prefs[KaleidoKeys.PARTY_INTENSITY] ?: 0.5f).coerceIn(0f, 1f),
            bassZoomIntensity    = (prefs[KaleidoKeys.BASS_ZOOM_INTENSITY] ?: 0.5f).coerceIn(0f, 1f),
            contrast             = (prefs[KaleidoKeys.CONTRAST] ?: 1.0f).coerceIn(0f, 2f),
            contrastPasses       = (prefs[KaleidoKeys.CONTRAST_PASSES] ?: 1).coerceIn(1, 6),
            saturation           = (prefs[KaleidoKeys.SATURATION] ?: 1.0f).coerceIn(0f, 2f),
            distortionPlusEnabled = prefs[KaleidoKeys.DISTORTION_PLUS_ENABLED] ?: false,
            distortionPlusYaw    = (prefs[KaleidoKeys.DISTORTION_PLUS_YAW] ?: 0f).coerceIn(-180f, 180f),
            distortionPlusPitch  = (prefs[KaleidoKeys.DISTORTION_PLUS_PITCH] ?: 0f).coerceIn(-90f, 90f),
            distortionPlusRoll   = (prefs[KaleidoKeys.DISTORTION_PLUS_ROLL] ?: 0f).coerceIn(-180f, 180f),
            lockedParams         = prefs[KaleidoKeys.LOCKED_PARAMS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { runCatching { LockableParam.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: emptySet(),
        )
    }

    suspend fun setFoldCount(value: Int) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.FOLD_COUNT] = value.coerceIn(2, 24)
        }
    }

    suspend fun setSquareRotationLocked(value: Boolean) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.SQUARE_ROT_LOCKED] = value
        }
    }

    suspend fun setFrameShape(value: FrameShape) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.FRAME_SHAPE] = value.name
        }
    }

    suspend fun setFrameColorArgb(value: Long) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.FRAME_COLOR_ARGB] = value
        }
    }

    suspend fun setZoomMultiplier(value: Float) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.ZOOM_MULTIPLIER] = value.coerceIn(0.5f, 1.5f)
        }
    }

    suspend fun setShapeKind(value: ShapeKind) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.SHAPE_KIND] = value.name
        }
    }

    suspend fun setInvertColors(value: Boolean) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.INVERT_COLORS] = value
        }
    }

    suspend fun setColorizeEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.COLORIZE_ENABLED] = value
        }
    }

    suspend fun setColorizeHue(value: Float) {
        context.kaleidoDataStore.edit { prefs ->
            prefs[KaleidoKeys.COLORIZE_HUE] = value.coerceIn(0f, 360f)
        }
    }

    suspend fun setDistortionEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_ENABLED] = value }
    }

    suspend fun setDistortionAmplitude(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_AMPLITUDE] = value.coerceIn(0f, 1f) }
    }

    suspend fun setDistortionFrequency(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_FREQUENCY] = value.coerceIn(0.5f, 8.0f) }
    }

    suspend fun setPartyEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PARTY_ENABLED] = value }
    }

    suspend fun setRandomEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.RANDOM_ENABLED] = value }
    }

    suspend fun setPartyIntensity(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PARTY_INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setBassZoomIntensity(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BASS_ZOOM_INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setContrast(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.CONTRAST] = value.coerceIn(0f, 2f) }
    }

    suspend fun setContrastPasses(value: Int) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.CONTRAST_PASSES] = value.coerceIn(1, 6) }
    }

    suspend fun setSaturation(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.SATURATION] = value.coerceIn(0f, 2f) }
    }

    suspend fun setDistortionPlusEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_PLUS_ENABLED] = value }
    }

    suspend fun setDistortionPlusYaw(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_PLUS_YAW] = value.coerceIn(-180f, 180f) }
    }

    suspend fun setDistortionPlusPitch(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_PLUS_PITCH] = value.coerceIn(-90f, 90f) }
    }

    suspend fun setDistortionPlusRoll(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.DISTORTION_PLUS_ROLL] = value.coerceIn(-180f, 180f) }
    }

    suspend fun setLockedParams(value: Set<LockableParam>) {
        context.kaleidoDataStore.edit {
            it[KaleidoKeys.LOCKED_PARAMS] = value.joinToString(",") { p -> p.name }
        }
    }

    val hasShownImmersiveTooltipFlow: Flow<Boolean> = context.kaleidoDataStore.data.map { prefs ->
        prefs[KaleidoKeys.IMMERSIVE_TOOLTIP_SHOWN] ?: false
    }

    suspend fun markImmersiveTooltipShown() {
        context.kaleidoDataStore.edit { it[KaleidoKeys.IMMERSIVE_TOOLTIP_SHOWN] = true }
    }
}
