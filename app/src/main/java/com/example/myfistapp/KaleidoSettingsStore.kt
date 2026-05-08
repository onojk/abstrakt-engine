package com.example.myfistapp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kaleidoDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "kaleido_settings")

object KaleidoKeys {
    val FOLD_COUNT       = intPreferencesKey("fold_count")
    val SQUARE_ROT_LOCKED = booleanPreferencesKey("square_rot_locked")
}

class KaleidoSettingsStore(private val context: Context) {

    val settingsFlow: Flow<KaleidoSettings> = context.kaleidoDataStore.data.map { prefs ->
        KaleidoSettings(
            foldCount             = (prefs[KaleidoKeys.FOLD_COUNT] ?: 12).coerceIn(2, 24),
            squareRotationLocked  = prefs[KaleidoKeys.SQUARE_ROT_LOCKED] ?: false,
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
}
