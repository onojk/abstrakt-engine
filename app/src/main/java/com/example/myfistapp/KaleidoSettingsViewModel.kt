package com.example.myfistapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class KaleidoSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = KaleidoSettingsStore(application)

    val settings: StateFlow<KaleidoSettings> = store.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, KaleidoSettings())

    private val _isSheetOpen = MutableStateFlow(false)
    val isSheetOpen: StateFlow<Boolean> = _isSheetOpen.asStateFlow()

    fun openSheet()  { _isSheetOpen.value = true  }
    fun closeSheet() { _isSheetOpen.value = false }

    fun setFoldCount(value: Int) {
        viewModelScope.launch { store.setFoldCount(value) }
    }
}
