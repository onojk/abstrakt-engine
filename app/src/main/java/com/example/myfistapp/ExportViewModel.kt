package com.example.myfistapp

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.loadAndAnalyze
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class SpaceStatus { OK, SOFT_WARN, HARD_BLOCK }

class ExportViewModel(app: Application) : AndroidViewModel(app) {

    private val kaleidoStore = KaleidoSettingsStore(app)

    // ── Kaleido export settings ───────────────────────────────────────────────

    private val _exportKaleido = MutableStateFlow(KaleidoSettings())
    val exportKaleido: StateFlow<KaleidoSettings> = _exportKaleido.asStateFlow()

    fun updateExportKaleido(settings: KaleidoSettings) { _exportKaleido.value = settings }

    // ── Wizard visibility ─────────────────────────────────────────────────────

    private val _isWizardOpen = MutableStateFlow(false)
    val isWizardOpen: StateFlow<Boolean> = _isWizardOpen.asStateFlow()

    // ── Wizard step ───────────────────────────────────────────────────────────

    private val _step = MutableStateFlow(WizardStep.MODE)
    internal val step: StateFlow<WizardStep> = _step.asStateFlow()

    // ── Config state ──────────────────────────────────────────────────────────

    private val _selectedMode = MutableStateFlow<Mode>(Mode.Cyclone)
    val selectedMode: StateFlow<Mode> = _selectedMode.asStateFlow()

    private val _selectedAudio = MutableStateFlow<AudioSource>(AudioSource.Silent)
    val selectedAudio: StateFlow<AudioSource> = _selectedAudio.asStateFlow()

    private val _currentAudioFile = MutableStateFlow<AudioFile?>(null)
    val currentAudioFile: StateFlow<AudioFile?> = _currentAudioFile.asStateFlow()

    private val _pickedAudioFile = MutableStateFlow<AudioFile?>(null)
    val pickedAudioFile: StateFlow<AudioFile?> = _pickedAudioFile.asStateFlow()

    private val _beatResponse = MutableStateFlow(true)
    val beatResponse: StateFlow<Boolean> = _beatResponse.asStateFlow()

    private val _selectedRes = MutableStateFlow(ExportResolution.FHD_1080P)
    val selectedRes: StateFlow<ExportResolution> = _selectedRes.asStateFlow()

    private val _isLoadingAudio = MutableStateFlow(false)
    val isLoadingAudio: StateFlow<Boolean> = _isLoadingAudio.asStateFlow()

    // ── Progress state ────────────────────────────────────────────────────────

    private val _progressValue = MutableStateFlow(0f)
    val progressValue: StateFlow<Float> = _progressValue.asStateFlow()

    private val _progressPhase = MutableStateFlow("")
    val progressPhase: StateFlow<String> = _progressPhase.asStateFlow()

    private val _encodeStartMs = MutableStateFlow(0L)
    val encodeStartMs: StateFlow<Long> = _encodeStartMs.asStateFlow()

    // ── Done state ────────────────────────────────────────────────────────────

    private val _exportedUri = MutableStateFlow<Uri?>(null)
    val exportedUri: StateFlow<Uri?> = _exportedUri.asStateFlow()

    private val _exportedName = MutableStateFlow("")
    val exportedName: StateFlow<String> = _exportedName.asStateFlow()

    private val _exportedSize = MutableStateFlow(0L)
    val exportedSize: StateFlow<Long> = _exportedSize.asStateFlow()

    // ── Cancel dialog ─────────────────────────────────────────────────────────

    private val _showCancelDialog = MutableStateFlow(false)
    val showCancelDialog: StateFlow<Boolean> = _showCancelDialog.asStateFlow()

    // ── Storage pre-flight ────────────────────────────────────────────────────

    private val _estimatedBytes = MutableStateFlow(0L)
    val estimatedBytes: StateFlow<Long> = _estimatedBytes.asStateFlow()

    private val _freeBytes = MutableStateFlow(0L)
    val freeBytes: StateFlow<Long> = _freeBytes.asStateFlow()

    private val _previousExportsBytes = MutableStateFlow(0L)
    val previousExportsBytes: StateFlow<Long> = _previousExportsBytes.asStateFlow()

    private val _previousExportsCount = MutableStateFlow(0)
    val previousExportsCount: StateFlow<Int> = _previousExportsCount.asStateFlow()

    val spaceStatus: StateFlow<SpaceStatus> = combine(_estimatedBytes, _freeBytes) { est, free ->
        when {
            free <= 0L        -> SpaceStatus.OK
            est  <= 0L        -> SpaceStatus.OK
            est  >= free      -> SpaceStatus.HARD_BLOCK
            est  >= free * 0.8 -> SpaceStatus.SOFT_WARN
            else              -> SpaceStatus.OK
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SpaceStatus.OK)

    // ── Out-of-space dialog ───────────────────────────────────────────────────

    private val _showOutOfSpaceDialog = MutableStateFlow(false)
    val showOutOfSpaceDialog: StateFlow<Boolean> = _showOutOfSpaceDialog.asStateFlow()

    fun dismissOutOfSpaceDialog() { _showOutOfSpaceDialog.value = false }

    // ── Internal job tracking ─────────────────────────────────────────────────

    private var exportJob: Job? = null
    private var pendingStoreUri: Uri? = null

    // ── Init — reactive estimate recomputation ────────────────────────────────

    init {
        viewModelScope.launch {
            combine(_selectedRes, _selectedAudio, _currentAudioFile, _pickedAudioFile) { res, src, cur, picked ->
                val resolved = when (src) {
                    AudioSource.UseCurrent -> cur
                    AudioSource.PickNew    -> picked
                    AudioSource.Silent     -> null
                }
                val durSec   = resolved?.let { it.durationMs / 1000.0 } ?: 60.0
                val hasAudio = resolved != null
                StorageEstimator.estimateBytes(res.bitrate, durSec, hasAudio)
            }.collect { _estimatedBytes.value = it }
        }
    }

    // ── Wizard lifecycle ──────────────────────────────────────────────────────

    fun openWizard(mode: Mode, audioFile: AudioFile?) {
        if (exportJob?.isActive == true || _step.value == WizardStep.DONE) {
            _isWizardOpen.value = true
            return
        }
        resetWizardState()
        _selectedMode.value     = if (mode != Mode.AddSlot) mode else Mode.Cyclone
        _selectedAudio.value    = if (audioFile != null) AudioSource.UseCurrent else AudioSource.Silent
        _currentAudioFile.value = audioFile
        _step.value             = WizardStep.MODE
        _isWizardOpen.value     = true
        viewModelScope.launch { _exportKaleido.value = kaleidoStore.settingsFlow.first() }
        refreshStorageState()
    }

    fun closeWizard() {
        _isWizardOpen.value = false
        if (_step.value == WizardStep.DONE) resetWizardState()
    }

    // ── Config mutators ───────────────────────────────────────────────────────

    fun selectMode(mode: Mode)                  { _selectedMode.value = mode }
    fun selectAudio(source: AudioSource)        { _selectedAudio.value = source }
    fun selectBeatResponse(on: Boolean)         { _beatResponse.value = on }
    fun selectResolution(res: ExportResolution) { _selectedRes.value = res }
    internal fun navTo(step: WizardStep)        { _step.value = step }
    fun requestCancelDialog()                   { _showCancelDialog.value = true }
    fun dismissCancelDialog()                   { _showCancelDialog.value = false }

    // ── Storage ───────────────────────────────────────────────────────────────

    fun refreshStorageState() {
        viewModelScope.launch {
            _freeBytes.value = StorageEstimator.freeBytesOnExternal(getApplication())
            val entries = StorageEstimator.listPreviousExports(getApplication())
            _previousExportsCount.value = entries.size
            _previousExportsBytes.value = entries.sumOf { it.size }
        }
    }

    fun clearPreviousExports(onDone: (Int, Long) -> Unit) {
        viewModelScope.launch {
            val (count, freed) = StorageEstimator.deleteAllPreviousExports(getApplication())
            refreshStorageState()
            withContext(Dispatchers.Main) { onDone(count, freed) }
        }
    }

    // ── Audio loading ─────────────────────────────────────────────────────────

    fun loadPickedAudio(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _isLoadingAudio.value = true
            try {
                val file = withContext(Dispatchers.IO) { loadAndAnalyze(ctx, uri) }
                _pickedAudioFile.value = file
                _selectedAudio.value   = AudioSource.PickNew
                _step.value            = WizardStep.BEAT
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Failed to load audio: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isLoadingAudio.value = false
            }
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    fun startExport() {
        if (spaceStatus.value == SpaceStatus.HARD_BLOCK) return  // defense-in-depth; UI disables button

        val ctx = getApplication<Application>()
        val mode = _selectedMode.value
        val res  = _selectedRes.value
        val fps  = if (res == ExportResolution.UHD_4K) 30 else 60
        val displayName = "abstrakt_${timestamp()}.mp4"
        val resolvedAudio = when (_selectedAudio.value) {
            AudioSource.UseCurrent -> _currentAudioFile.value
            AudioSource.PickNew    -> _pickedAudioFile.value
            AudioSource.Silent     -> null
        }
        val userSkinPath = resolveUserSkinPath(mode)

        _step.value          = WizardStep.PROGRESS
        _progressValue.value = 0f
        _progressPhase.value = ""
        _encodeStartMs.value = 0L

        exportJob = viewModelScope.launch {
            val storeUri = try {
                createMediaStoreEntry(ctx, displayName)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Cannot create export file: ${e.message}", Toast.LENGTH_LONG).show()
                }
                _step.value = WizardStep.RESOLUTION
                return@launch
            }
            pendingStoreUri = storeUri

            try {
                val pfd = ctx.contentResolver.openFileDescriptor(storeUri, "w")
                    ?: throw IOException("Cannot open output file")
                val kaleido = _exportKaleido.value
                val exporter = Mp4Exporter(
                    context                      = ctx,
                    width                        = res.width,
                    height                       = res.height,
                    fps                          = fps,
                    bitrate                      = res.bitrate,
                    audioFile                    = resolvedAudio,
                    exportMode                   = mode,
                    userSkinFilePath             = userSkinPath,
                    beatResponseEnabled          = _beatResponse.value,
                    outputFd                     = pfd.fileDescriptor,
                    kaleidoFoldCount             = kaleido.foldCount,
                    kaleidoSquareRotationLocked  = kaleido.squareRotationLocked,
                    kaleidoFrameShape            = kaleido.frameShape,
                    kaleidoFrameColorArgb        = kaleido.frameColorArgb,
                    kaleidoShapeKind             = kaleido.shapeKind,
                    invertColors                 = kaleido.invertColors,
                    colorizeEnabled              = kaleido.colorizeEnabled,
                    colorizeHue                  = kaleido.colorizeHue,
                    distortionEnabled            = kaleido.distortionEnabled,
                    distortionAmplitude          = kaleido.distortionAmplitude,
                    distortionFrequency          = kaleido.distortionFrequency,
                    partyEnabled                 = kaleido.partyEnabled,
                    randomEnabled                = kaleido.randomEnabled,
                    partyIntensity               = kaleido.partyIntensity,
                    bassZoomIntensity            = kaleido.bassZoomIntensity,
                    contrast                     = kaleido.contrast,
                    contrastPasses               = kaleido.contrastPasses,
                    saturation                   = kaleido.saturation,
                    distortionPlusEnabled        = kaleido.distortionPlusEnabled,
                    distortionPlusYaw            = kaleido.distortionPlusYaw,
                    distortionPlusPitch          = kaleido.distortionPlusPitch,
                    distortionPlusRoll           = kaleido.distortionPlusRoll,
                    lockedParams                 = kaleido.lockedParams,
                )
                val result = exporter.export { fraction, phase ->
                    _progressValue.value = fraction
                    _progressPhase.value = phase
                    if (phase.startsWith("Encoding") && _encodeStartMs.value == 0L) {
                        _encodeStartMs.value = System.currentTimeMillis()
                    }
                }
                val fileSize = pfd.statSize
                pfd.close()

                result.fold(
                    onSuccess = {
                        finalizeMediaStoreEntry(ctx, storeUri)
                        _exportedUri.value  = storeUri
                        _exportedName.value = displayName
                        _exportedSize.value = fileSize
                        pendingStoreUri     = null
                        _step.value         = WizardStep.DONE
                    },
                    onFailure = { e ->
                        deleteMediaStoreEntry(ctx, storeUri)
                        pendingStoreUri = null
                        if (isOutOfSpace(e)) {
                            withContext(Dispatchers.Main) {
                                refreshStorageState()
                                _showOutOfSpaceDialog.value = true
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        _step.value = WizardStep.RESOLUTION
                    },
                )
            } catch (e: CancellationException) {
                pendingStoreUri?.let { deleteMediaStoreEntry(ctx, it) }
                pendingStoreUri = null
                throw e
            } catch (e: Exception) {
                pendingStoreUri?.let { deleteMediaStoreEntry(ctx, it) }
                pendingStoreUri = null
                if (isOutOfSpace(e)) {
                    withContext(Dispatchers.Main) {
                        refreshStorageState()
                        _showOutOfSpaceDialog.value = true
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                _step.value = WizardStep.RESOLUTION
            }
        }
    }

    fun cancelExport() {
        val uri = pendingStoreUri
        pendingStoreUri = null
        _showCancelDialog.value = false
        exportJob?.cancel()
        exportJob = null
        if (uri != null) deleteMediaStoreEntry(getApplication(), uri)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun isOutOfSpace(e: Throwable): Boolean {
        if (e is android.system.ErrnoException && e.errno == android.system.OsConstants.ENOSPC) return true
        val msg = e.message?.lowercase() ?: ""
        if ("enospc" in msg || "no space left" in msg) return true
        var cause = e.cause
        while (cause != null && cause !== e) {
            if (cause is android.system.ErrnoException && cause.errno == android.system.OsConstants.ENOSPC) return true
            val cmsg = cause.message?.lowercase() ?: ""
            if ("enospc" in cmsg || "no space left" in cmsg) return true
            cause = cause.cause
        }
        return false
    }

    private fun resetWizardState() {
        cancelExport()
        _step.value                 = WizardStep.MODE
        _selectedMode.value         = Mode.Cyclone
        _selectedAudio.value        = AudioSource.Silent
        _currentAudioFile.value     = null
        _pickedAudioFile.value      = null
        _beatResponse.value         = true
        _selectedRes.value          = ExportResolution.FHD_1080P
        _isLoadingAudio.value       = false
        _progressValue.value        = 0f
        _progressPhase.value        = ""
        _encodeStartMs.value        = 0L
        _exportedUri.value          = null
        _exportedName.value         = ""
        _exportedSize.value         = 0L
        _showCancelDialog.value     = false
        _showOutOfSpaceDialog.value = false
        _exportKaleido.value        = KaleidoSettings()
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
        pendingStoreUri?.let { deleteMediaStoreEntry(getApplication(), it) }
    }
}
