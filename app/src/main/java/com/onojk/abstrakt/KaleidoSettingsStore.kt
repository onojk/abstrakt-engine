package com.onojk.abstrakt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.onojk.abstrakt.color.ColorHarmony
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
    val REACTIVE_ENABLED       = booleanPreferencesKey("reactive_enabled")
    val REACTIVE_INTENSITY     = floatPreferencesKey("reactive_intensity")
    val BASS_ZOOM_INTENSITY    = floatPreferencesKey("bass_zoom_intensity")
    val CONTRAST               = floatPreferencesKey("contrast")
    val CONTRAST_PASSES        = intPreferencesKey("contrast_passes")
    val SATURATION             = floatPreferencesKey("saturation")
    val DISTORTION_PLUS_ENABLED = booleanPreferencesKey("distortion_plus_enabled")
    val DISTORTION_PLUS_YAW    = floatPreferencesKey("distortion_plus_yaw")
    val DISTORTION_PLUS_PITCH  = floatPreferencesKey("distortion_plus_pitch")
    val DISTORTION_PLUS_ROLL   = floatPreferencesKey("distortion_plus_roll")
    val BEAT_REACTIVITY          = floatPreferencesKey("beat_reactivity")
    val LOCKED_PARAMS            = stringPreferencesKey("locked_params")
    val IMMERSIVE_TOOLTIP_SHOWN  = booleanPreferencesKey("immersive_tooltip_shown")
    val PITCH_TO_HUE             = booleanPreferencesKey("pitch_to_hue")
    val KEY_CHANGE_PARTY_TRIGGER = booleanPreferencesKey("key_change_party_trigger")
    val BPM_ROTATION_LOCK             = booleanPreferencesKey("bpm_rotation_lock")
    val BEATS_PER_REVOLUTION          = intPreferencesKey("beats_per_revolution")
    val CHROMA_ABERRATION_ENABLED     = booleanPreferencesKey("chroma_aberration_enabled")
    val CHROMA_ABERRATION_INTENSITY   = floatPreferencesKey("chroma_aberration_intensity")
    val CHROMA_ABERRATION_AUDIO_REACT = booleanPreferencesKey("chroma_aberration_audio_react")
    val PALETTE_MODE      = stringPreferencesKey("palette_mode")
    val PALETTE_TINT      = floatPreferencesKey("palette_tint")
    val PALETTE_MONO_HUE  = floatPreferencesKey("palette_mono_hue")
    val BLACKHOLE_ENABLED      = booleanPreferencesKey("blackhole_enabled")
    val BLACKHOLE_STRENGTH     = floatPreferencesKey("blackhole_strength")
    val BLACKHOLE_SHRINK_RATE  = floatPreferencesKey("blackhole_shrink_rate")
    val BLACKHOLE_ALPHA_RADIUS = floatPreferencesKey("blackhole_alpha_radius")
    val BLACKHOLE_WANDER_AMOUNT = floatPreferencesKey("blackhole_wander_amount")
    val HARMONY_TYPE           = stringPreferencesKey("harmony_type")
    val HARMONY_ANCHOR_HUE     = floatPreferencesKey("harmony_anchor_hue")
    val HARMONY_SATURATION     = floatPreferencesKey("harmony_saturation")
    val HARMONY_VALUE          = floatPreferencesKey("harmony_value")
    val HARMONY_STRENGTH       = floatPreferencesKey("harmony_strength")
    val LUT_SELECTION          = stringPreferencesKey("lut_selection")
    val LUT_STRENGTH           = floatPreferencesKey("lut_strength")
    val SUDDEN_WARP_ENABLED    = booleanPreferencesKey("sudden_warp_enabled")
    val LIGHTNING_ENABLED           = booleanPreferencesKey("lightning_enabled")
    val LIGHTNING_SPRITES_LIMIT_5S  = booleanPreferencesKey("lightning_sprites_limit_5s")
}

class KaleidoSettingsStore(private val context: Context) {

    val settingsFlow: Flow<KaleidoSettings> = context.kaleidoDataStore.data.map { prefs ->
        KaleidoSettings(
            foldCount            = (prefs[KaleidoKeys.FOLD_COUNT] ?: 12).coerceIn(2, 24),
            squareRotationLocked = prefs[KaleidoKeys.SQUARE_ROT_LOCKED] ?: false,
            frameShape           = prefs[KaleidoKeys.FRAME_SHAPE]
                ?.let { runCatching { FrameShape.valueOf(it) }.getOrNull() }
                ?: FrameShape.None,
            frameColorArgb       = prefs[KaleidoKeys.FRAME_COLOR_ARGB] ?: 0xFFFFFFFFL,
            zoomMultiplier       = (prefs[KaleidoKeys.ZOOM_MULTIPLIER] ?: 1.0f).coerceIn(0.5f, 1.5f),
            shapeKind            = prefs[KaleidoKeys.SHAPE_KIND]
                ?.let { runCatching { ShapeKind.valueOf(it) }.getOrNull() }
                ?: ShapeKind.Cube,
            invertColors         = prefs[KaleidoKeys.INVERT_COLORS] ?: false,
            colorizeEnabled      = prefs[KaleidoKeys.COLORIZE_ENABLED] ?: false,
            colorizeHue          = (prefs[KaleidoKeys.COLORIZE_HUE] ?: 0f).coerceIn(0f, 360f),
            distortionEnabled    = prefs[KaleidoKeys.DISTORTION_ENABLED] ?: false,
            distortionAmplitude  = (prefs[KaleidoKeys.DISTORTION_AMPLITUDE] ?: 0.3f).coerceIn(0f, 1f),
            distortionFrequency  = (prefs[KaleidoKeys.DISTORTION_FREQUENCY] ?: 2.0f).coerceIn(0.5f, 8.0f),
            partyEnabled         = prefs[KaleidoKeys.PARTY_ENABLED] ?: false,
            randomEnabled        = prefs[KaleidoKeys.RANDOM_ENABLED] ?: false,
            partyIntensity       = (prefs[KaleidoKeys.PARTY_INTENSITY] ?: 0.5f).coerceIn(0f, 1f),
            reactiveEnabled      = prefs[KaleidoKeys.REACTIVE_ENABLED]   ?: false,
            reactiveIntensity    = (prefs[KaleidoKeys.REACTIVE_INTENSITY] ?: 0.5f).coerceIn(0f, 1f),
            bassZoomIntensity    = (prefs[KaleidoKeys.BASS_ZOOM_INTENSITY] ?: 0.5f).coerceIn(0f, 1f),
            contrast             = (prefs[KaleidoKeys.CONTRAST] ?: 2.0f).coerceIn(0f, 2f),
            contrastPasses       = (prefs[KaleidoKeys.CONTRAST_PASSES] ?: 6).coerceIn(1, 6),
            saturation           = (prefs[KaleidoKeys.SATURATION] ?: 2.0f).coerceIn(0f, 2f),
            distortionPlusEnabled = prefs[KaleidoKeys.DISTORTION_PLUS_ENABLED] ?: false,
            distortionPlusYaw    = (prefs[KaleidoKeys.DISTORTION_PLUS_YAW] ?: 0f).coerceIn(-180f, 180f),
            distortionPlusPitch  = (prefs[KaleidoKeys.DISTORTION_PLUS_PITCH] ?: 0f).coerceIn(-90f, 90f),
            distortionPlusRoll   = (prefs[KaleidoKeys.DISTORTION_PLUS_ROLL] ?: 0f).coerceIn(-180f, 180f),
            beatReactivity           = (prefs[KaleidoKeys.BEAT_REACTIVITY] ?: 0.25f).coerceIn(0f, 1f),
            pitchToHue               = prefs[KaleidoKeys.PITCH_TO_HUE]             ?: false,
            keyChangePartyTrigger    = prefs[KaleidoKeys.KEY_CHANGE_PARTY_TRIGGER] ?: false,
            bpmRotationLock          = prefs[KaleidoKeys.BPM_ROTATION_LOCK]        ?: false,
            beatsPerRevolution       = (prefs[KaleidoKeys.BEATS_PER_REVOLUTION]    ?: 8)
                .coerceIn(1, 16),
            chromaAberrationEnabled     = prefs[KaleidoKeys.CHROMA_ABERRATION_ENABLED]     ?: false,
            chromaAberrationIntensity   = (prefs[KaleidoKeys.CHROMA_ABERRATION_INTENSITY]   ?: 0.008f).coerceIn(0f, 0.02f),
            chromaAberrationAudioReact  = prefs[KaleidoKeys.CHROMA_ABERRATION_AUDIO_REACT]  ?: false,
            paletteMode          = prefs[KaleidoKeys.PALETTE_MODE]
                ?.let { runCatching { PaletteMode.valueOf(it) }.getOrNull() }
                ?: PaletteMode.Off,
            paletteTint          = (prefs[KaleidoKeys.PALETTE_TINT] ?: 1.0f).coerceIn(0f, 1f),
            paletteMonoHue       = (prefs[KaleidoKeys.PALETTE_MONO_HUE] ?: 200f).coerceIn(0f, 360f),
            blackholeEnabled      = prefs[KaleidoKeys.BLACKHOLE_ENABLED]      ?: false,
            blackholeStrength     = (prefs[KaleidoKeys.BLACKHOLE_STRENGTH]     ?: 0.5f).coerceIn(0f, 0.98f),
            blackholeShrinkRate   = (prefs[KaleidoKeys.BLACKHOLE_SHRINK_RATE]  ?: 0.97f).coerceIn(0.90f, 0.999f),
            blackholeAlphaRadius  = (prefs[KaleidoKeys.BLACKHOLE_ALPHA_RADIUS] ?: 0.5f).coerceIn(0.1f, 0.9f),
            blackholeWanderAmount = (prefs[KaleidoKeys.BLACKHOLE_WANDER_AMOUNT] ?: 0.005f).coerceIn(0f, 0.02f),
            harmonyType          = prefs[KaleidoKeys.HARMONY_TYPE]
                ?.let { runCatching { ColorHarmony.valueOf(it) }.getOrNull() }
                ?: ColorHarmony.Triadic,
            harmonyAnchorHue     = (prefs[KaleidoKeys.HARMONY_ANCHOR_HUE]  ?: 0f).coerceIn(0f, 360f),
            harmonySaturation    = (prefs[KaleidoKeys.HARMONY_SATURATION]  ?: 0.8f).coerceIn(0f, 1f),
            harmonyValue         = (prefs[KaleidoKeys.HARMONY_VALUE]       ?: 0.8f).coerceIn(0f, 1f),
            harmonyStrength      = (prefs[KaleidoKeys.HARMONY_STRENGTH]    ?: 0.6f).coerceIn(0f, 1f),
            lutSelection         = prefs[KaleidoKeys.LUT_SELECTION] ?: "none",
            lutStrength          = (prefs[KaleidoKeys.LUT_STRENGTH] ?: 1.0f).coerceIn(0f, 1f),
            suddenWarpEnabled    = prefs[KaleidoKeys.SUDDEN_WARP_ENABLED] ?: false,
            lightningEnabled          = prefs[KaleidoKeys.LIGHTNING_ENABLED]          ?: false,
            lightningSpritesLimit5s   = prefs[KaleidoKeys.LIGHTNING_SPRITES_LIMIT_5S]  ?: true,
            lockedParams             = prefs[KaleidoKeys.LOCKED_PARAMS]
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

    suspend fun setReactiveEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.REACTIVE_ENABLED] = value }
    }

    suspend fun setReactiveIntensity(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.REACTIVE_INTENSITY] = value.coerceIn(0f, 1f) }
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

    suspend fun setBeatReactivity(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BEAT_REACTIVITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setPitchToHue(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PITCH_TO_HUE] = value }
    }

    suspend fun setKeyChangePartyTrigger(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.KEY_CHANGE_PARTY_TRIGGER] = value }
    }

    suspend fun setBpmRotationLock(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BPM_ROTATION_LOCK] = value }
    }

    suspend fun setBeatsPerRevolution(value: Int) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BEATS_PER_REVOLUTION] = value.coerceIn(1, 16) }
    }

    suspend fun setChromaAberrationEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.CHROMA_ABERRATION_ENABLED] = value }
    }

    suspend fun setChromaAberrationIntensity(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.CHROMA_ABERRATION_INTENSITY] = value.coerceIn(0f, 0.02f) }
    }

    suspend fun setChromaAberrationAudioReact(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.CHROMA_ABERRATION_AUDIO_REACT] = value }
    }

    suspend fun setPaletteMode(value: PaletteMode) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PALETTE_MODE] = value.name }
    }

    suspend fun setPaletteTint(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PALETTE_TINT] = value.coerceIn(0f, 1f) }
    }

    suspend fun setPaletteMonoHue(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.PALETTE_MONO_HUE] = value.coerceIn(0f, 360f) }
    }

    suspend fun setBlackholeEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BLACKHOLE_ENABLED] = value }
    }

    suspend fun setBlackholeStrength(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BLACKHOLE_STRENGTH] = value.coerceIn(0f, 0.98f) }
    }

    suspend fun setBlackholeShrinkRate(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BLACKHOLE_SHRINK_RATE] = value.coerceIn(0.90f, 0.999f) }
    }

    suspend fun setBlackholeAlphaRadius(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BLACKHOLE_ALPHA_RADIUS] = value.coerceIn(0.1f, 0.9f) }
    }

    suspend fun setBlackholeWanderAmount(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.BLACKHOLE_WANDER_AMOUNT] = value.coerceIn(0f, 0.02f) }
    }

    suspend fun setHarmonyType(value: ColorHarmony) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.HARMONY_TYPE] = value.name }
    }

    suspend fun setHarmonyAnchorHue(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.HARMONY_ANCHOR_HUE] = value.coerceIn(0f, 360f) }
    }

    suspend fun setHarmonySaturation(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.HARMONY_SATURATION] = value.coerceIn(0f, 1f) }
    }

    suspend fun setHarmonyValue(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.HARMONY_VALUE] = value.coerceIn(0f, 1f) }
    }

    suspend fun setHarmonyStrength(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.HARMONY_STRENGTH] = value.coerceIn(0f, 1f) }
    }

    suspend fun setLutSelection(value: String) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.LUT_SELECTION] = value }
    }

    suspend fun setLutStrength(value: Float) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.LUT_STRENGTH] = value.coerceIn(0f, 1f) }
    }

    suspend fun setSuddenWarpEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.SUDDEN_WARP_ENABLED] = value }
    }

    suspend fun setLightningEnabled(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.LIGHTNING_ENABLED] = value }
    }

    suspend fun setLightningSpritesLimit5s(value: Boolean) {
        context.kaleidoDataStore.edit { it[KaleidoKeys.LIGHTNING_SPRITES_LIMIT_5S] = value }
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
