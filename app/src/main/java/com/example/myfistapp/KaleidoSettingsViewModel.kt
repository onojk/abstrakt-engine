package com.example.myfistapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KaleidoSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = KaleidoSettingsStore(application)

    val settings: StateFlow<KaleidoSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, KaleidoSettings())

    fun setFoldCount(value: Int) {
        viewModelScope.launch { store.setFoldCount(value) }
    }
}
