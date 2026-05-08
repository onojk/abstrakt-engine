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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class ExportViewModel(app: Application) : AndroidViewModel(app) {

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

    // ── Internal job tracking ─────────────────────────────────────────────────

    private var exportJob: Job? = null
    private var pendingStoreUri: Uri? = null

    // ── Wizard lifecycle ──────────────────────────────────────────────────────

    // Opens the wizard. If an export is in progress, resumes the PROGRESS step.
    // If on the DONE step, re-shows the completion screen. Otherwise resets for
    // a fresh flow and initializes config from the caller's current screen state.
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
    }

    // Closes the wizard UI without canceling an in-progress export.
    // If the export just completed (DONE step), clears completion state so the
    // next open starts fresh rather than jumping back to the done screen.
    fun closeWizard() {
        _isWizardOpen.value = false
        if (_step.value == WizardStep.DONE) {
            resetWizardState()
        }
    }

    // ── Config mutators ───────────────────────────────────────────────────────

    fun selectMode(mode: Mode)                  { _selectedMode.value = mode }
    fun selectAudio(source: AudioSource)        { _selectedAudio.value = source }
    fun selectBeatResponse(on: Boolean)         { _beatResponse.value = on }
    fun selectResolution(res: ExportResolution) { _selectedRes.value = res }
    internal fun navTo(step: WizardStep)        { _step.value = step }
    fun requestCancelDialog()                   { _showCancelDialog.value = true }
    fun dismissCancelDialog()                   { _showCancelDialog.value = false }

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
                val exporter = Mp4Exporter(
                    context             = ctx,
                    width               = res.width,
                    height              = res.height,
                    fps                 = fps,
                    bitrate             = res.bitrate,
                    audioFile           = resolvedAudio,
                    exportMode          = mode,
                    userSkinFilePath    = userSkinPath,
                    beatResponseEnabled = _beatResponse.value,
                    outputFd            = pfd.fileDescriptor,
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
                        withContext(Dispatchers.Main) {
                            Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    // ── Internal reset ────────────────────────────────────────────────────────

    private fun resetWizardState() {
        cancelExport()
        _step.value             = WizardStep.MODE
        _selectedMode.value     = Mode.Cyclone
        _selectedAudio.value    = AudioSource.Silent
        _currentAudioFile.value = null
        _pickedAudioFile.value  = null
        _beatResponse.value     = true
        _selectedRes.value      = ExportResolution.FHD_1080P
        _isLoadingAudio.value   = false
        _progressValue.value    = 0f
        _progressPhase.value    = ""
        _encodeStartMs.value    = 0L
        _exportedUri.value      = null
        _exportedName.value     = ""
        _exportedSize.value     = 0L
        _showCancelDialog.value = false
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
        pendingStoreUri?.let { deleteMediaStoreEntry(getApplication(), it) }
    }
}
