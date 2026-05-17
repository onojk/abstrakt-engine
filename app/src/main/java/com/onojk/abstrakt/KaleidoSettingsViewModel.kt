package com.onojk.abstrakt

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

    fun setSquareRotationLocked(value: Boolean) {
        viewModelScope.launch { store.setSquareRotationLocked(value) }
    }

    fun setFrameShape(value: FrameShape) {
        viewModelScope.launch { store.setFrameShape(value) }
    }

    fun setFrameColorArgb(value: Long) {
        viewModelScope.launch { store.setFrameColorArgb(value) }
    }

    fun setZoomMultiplier(value: Float) {
        viewModelScope.launch { store.setZoomMultiplier(value) }
    }

    fun resetZoomMultiplier() {
        viewModelScope.launch { store.setZoomMultiplier(1.0f) }
    }

    fun setShapeKind(value: ShapeKind) {
        viewModelScope.launch { store.setShapeKind(value) }
    }

    fun setInvertColors(value: Boolean) {
        viewModelScope.launch { store.setInvertColors(value) }
    }

    fun setColorizeEnabled(value: Boolean) {
        viewModelScope.launch { store.setColorizeEnabled(value) }
    }

    fun setColorizeHue(value: Float) {
        viewModelScope.launch { store.setColorizeHue(value) }
    }

    fun setDistortionEnabled(value: Boolean) {
        viewModelScope.launch { store.setDistortionEnabled(value) }
    }

    fun setDistortionAmplitude(value: Float) {
        viewModelScope.launch { store.setDistortionAmplitude(value) }
    }

    fun setDistortionFrequency(value: Float) {
        viewModelScope.launch { store.setDistortionFrequency(value) }
    }

    fun setPartyEnabled(value: Boolean) {
        viewModelScope.launch { store.setPartyEnabled(value) }
    }

    fun setRandomEnabled(value: Boolean) {
        viewModelScope.launch { store.setRandomEnabled(value) }
    }

    fun setPartyIntensity(value: Float) {
        viewModelScope.launch { store.setPartyIntensity(value) }
    }

    fun setBassZoomIntensity(value: Float) {
        viewModelScope.launch { store.setBassZoomIntensity(value) }
    }

    fun setContrast(value: Float) {
        viewModelScope.launch { store.setContrast(value) }
    }

    fun setContrastPasses(value: Int) {
        viewModelScope.launch { store.setContrastPasses(value) }
    }

    fun setSaturation(value: Float) {
        viewModelScope.launch { store.setSaturation(value) }
    }

    fun setDistortionPlusEnabled(value: Boolean) {
        viewModelScope.launch { store.setDistortionPlusEnabled(value) }
    }

    fun setDistortionPlusYaw(value: Float) {
        viewModelScope.launch { store.setDistortionPlusYaw(value) }
    }

    fun setDistortionPlusPitch(value: Float) {
        viewModelScope.launch { store.setDistortionPlusPitch(value) }
    }

    fun setDistortionPlusRoll(value: Float) {
        viewModelScope.launch { store.setDistortionPlusRoll(value) }
    }

    fun setBeatReactivity(value: Float) {
        viewModelScope.launch { store.setBeatReactivity(value) }
    }

    fun setPitchToHue(value: Boolean) {
        viewModelScope.launch { store.setPitchToHue(value) }
    }

    fun setKeyChangePartyTrigger(value: Boolean) {
        viewModelScope.launch { store.setKeyChangePartyTrigger(value) }
    }

    fun toggleLock(param: LockableParam) {
        val current = settings.value.lockedParams
        val updated = if (param in current) current - param else current + param
        viewModelScope.launch { store.setLockedParams(updated) }
    }

    val hasShownImmersiveTooltip: StateFlow<Boolean> = store.hasShownImmersiveTooltipFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = true)

    fun markImmersiveTooltipShown() {
        viewModelScope.launch { store.markImmersiveTooltipShown() }
    }
}
