package com.onojk.abstrakt

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onojk.abstrakt.audio.AudioFile
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ── Wizard step tracking ──────────────────────────────────────────────────────

internal enum class WizardStep { MODE, AUDIO, BEAT, RESOLUTION, KALEIDO, PROGRESS, DONE }

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun ExportWizard(
    viewModel: ExportViewModel,
    onClose: () -> Unit,
) {
    val vm      = viewModel
    val context = LocalContext.current

    // Collect all state from the ViewModel.
    val step            by vm.step.collectAsStateWithLifecycle()
    val selectedMode    by vm.selectedMode.collectAsStateWithLifecycle()
    val selectedAudio   by vm.selectedAudio.collectAsStateWithLifecycle()
    val currentAudio    by vm.currentAudioFile.collectAsStateWithLifecycle()
    val pickedAudio     by vm.pickedAudioFile.collectAsStateWithLifecycle()
    val beatResponse    by vm.beatResponse.collectAsStateWithLifecycle()
    val selectedRes     by vm.selectedRes.collectAsStateWithLifecycle()
    val selectedFps     by vm.selectedFps.collectAsStateWithLifecycle()
    val isLoadingAudio  by vm.isLoadingAudio.collectAsStateWithLifecycle()
    val progressValue   by vm.progressValue.collectAsStateWithLifecycle()
    val progressPhase   by vm.progressPhase.collectAsStateWithLifecycle()
    val encodeStartMs   by vm.encodeStartMs.collectAsStateWithLifecycle()
    val exportedUri     by vm.exportedUri.collectAsStateWithLifecycle()
    val exportedName    by vm.exportedName.collectAsStateWithLifecycle()
    val exportedSize    by vm.exportedSize.collectAsStateWithLifecycle()
    val showCancelDialog  by vm.showCancelDialog.collectAsStateWithLifecycle()
    val exportKaleido     by vm.exportKaleido.collectAsStateWithLifecycle()

    // Storage pre-flight state.
    val estimatedBytes   by vm.estimatedBytes.collectAsStateWithLifecycle()
    val freeBytes        by vm.freeBytes.collectAsStateWithLifecycle()
    val spaceStatus      by vm.spaceStatus.collectAsStateWithLifecycle()
    val prevExportsCount by vm.previousExportsCount.collectAsStateWithLifecycle()
    val prevExportsBytes by vm.previousExportsBytes.collectAsStateWithLifecycle()
    val showOutOfSpace   by vm.showOutOfSpaceDialog.collectAsStateWithLifecycle()

    // Refresh storage once on wizard open.
    LaunchedEffect(Unit) { vm.refreshStorageState() }

    val dismiss = onClose

    // Audio picker — must live in Compose; result forwarded to ViewModel.
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.loadPickedAudio(uri)
    }

    // ── Out-of-space dialog — floats on top of any step ───────────────────────
    if (showOutOfSpace) {
        AlertDialog(
            onDismissRequest = { vm.dismissOutOfSpaceDialog() },
            containerColor   = Color(0xFF1A1A24),
            title = { Text("Out of space", color = NeonCyan) },
            text  = {
                Column {
                    Text(
                        "Your export ran out of storage. Only ${StorageEstimator.formatBytes(freeBytes)} is free.",
                        color = Color.White,
                    )
                    if (prevExportsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You have $prevExportsCount previous export${if (prevExportsCount == 1) "" else "s"} " +
                                "using ${StorageEstimator.formatBytes(prevExportsBytes)}. Clear them to make room?",
                            color = DimWhite,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Free up space in your device settings and try again.", color = DimWhite)
                    }
                }
            },
            confirmButton = {
                if (prevExportsCount > 0) {
                    TextButton(onClick = {
                        vm.clearPreviousExports { count, freed ->
                            Toast.makeText(
                                context,
                                "Deleted $count, freed ${StorageEstimator.formatBytes(freed)}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        vm.dismissOutOfSpaceDialog()
                    }) { Text("Clear previous exports", color = NeonCyan) }
                } else {
                    TextButton(onClick = { vm.dismissOutOfSpaceDialog() }) { Text("OK", color = NeonCyan) }
                }
            },
            dismissButton = if (prevExportsCount > 0) {
                { TextButton(onClick = { vm.dismissOutOfSpaceDialog() }) { Text("Cancel", color = DimWhite) } }
            } else null,
        )
    }

    // ── Step routing ──────────────────────────────────────────────────────────
    when (step) {
        WizardStep.MODE -> Step1Mode(
            modes      = buildExportModeList(),
            selected   = selectedMode,
            onSelect   = { vm.selectMode(it) },
            onBack     = dismiss,
            onContinue = { vm.navTo(WizardStep.AUDIO) },
        )

        WizardStep.AUDIO -> Step2Audio(
            currentAudio    = currentAudio,
            pickedAudio     = pickedAudio,
            selectedSource  = selectedAudio,
            isLoadingAudio  = isLoadingAudio,
            onSelect = { src ->
                vm.selectAudio(src)
                if (src == AudioSource.PickNew && pickedAudio == null) {
                    audioPicker.launch(arrayOf("audio/*"))
                }
            },
            onPickNew  = { audioPicker.launch(arrayOf("audio/*")) },
            onBack     = { vm.navTo(WizardStep.MODE) },
            onContinue = {
                vm.navTo(
                    if (selectedAudio == AudioSource.Silent) WizardStep.RESOLUTION
                    else WizardStep.BEAT
                )
            },
            continueEnabled = when (selectedAudio) {
                AudioSource.UseCurrent -> currentAudio != null
                AudioSource.PickNew    -> pickedAudio != null
                AudioSource.Silent     -> true
            },
        )

        WizardStep.BEAT -> Step3Beat(
            beatResponse = beatResponse,
            onSelect     = { vm.selectBeatResponse(it) },
            onBack       = { vm.navTo(WizardStep.AUDIO) },
            onContinue   = { vm.navTo(WizardStep.RESOLUTION) },
        )

        WizardStep.RESOLUTION -> Step4Resolution(
            selected      = selectedRes,
            onSelect      = { vm.selectResolution(it) },
            selectedFps   = selectedFps,
            onSelectFps   = { vm.selectFps(it) },
            onBack        = { vm.navTo(if (selectedAudio == AudioSource.Silent) WizardStep.AUDIO else WizardStep.BEAT) },
            onStart       = { vm.navTo(WizardStep.KALEIDO) },
        )

        WizardStep.KALEIDO -> Step5Kaleido(
            kaleidoSettings      = exportKaleido,
            onUpdate             = { vm.updateExportKaleido(it) },
            onBack               = { vm.navTo(WizardStep.RESOLUTION) },
            onStart              = { vm.startExport() },
            estimatedBytes       = estimatedBytes,
            freeBytes            = freeBytes,
            spaceStatus          = spaceStatus,
            previousExportsCount = prevExportsCount,
            previousExportsBytes = prevExportsBytes,
            onClearPreviousExports = { onDone -> vm.clearPreviousExports(onDone) },
        )

        WizardStep.PROGRESS -> {
            val etaMs: Long = if (progressValue > 0.05f && encodeStartMs > 0L &&
                progressPhase.startsWith("Encoding")) {
                val elapsed = System.currentTimeMillis() - encodeStartMs
                ((elapsed / progressValue.toDouble()) * (1.0 - progressValue)).toLong()
            } else -1L

            Step6Progress(
                progress = progressValue,
                phase    = progressPhase,
                etaMs    = etaMs,
                onCancel = { vm.requestCancelDialog() },
            )

            if (showCancelDialog) {
                AlertDialog(
                    onDismissRequest = { vm.dismissCancelDialog() },
                    containerColor   = Color(0xFF1A1A24),
                    title  = { Text("Cancel export?", color = NeonCyan) },
                    text   = { Text("Progress will be lost.", color = DimWhite) },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.cancelExport()
                            onClose()
                        }) { Text("Cancel export", color = Color(0xFFFF4040)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.dismissCancelDialog() }) {
                            Text("Keep waiting", color = DimWhite)
                        }
                    },
                )
            }
        }

        WizardStep.DONE -> Step6Done(
            name      = exportedName,
            sizePx    = exportedSize,
            uri       = exportedUri,
            onDismiss = dismiss,
        )
    }
}

// ── Step composables ──────────────────────────────────────────────────────────

@Composable
private fun Step1Mode(
    modes: List<Mode>,
    selected: Mode,
    onSelect: (Mode) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    WizardScaffold(
        title         = "Choose mode",
        onBack        = onBack,
        primaryLabel  = "Continue",
        onPrimary     = onContinue,
    ) {
        Spacer(Modifier.height(8.dp))
        modes.forEach { mode ->
            ModeRow(
                mode     = mode,
                selected = mode == selected,
                onClick  = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun Step2Audio(
    currentAudio: AudioFile?,
    pickedAudio: AudioFile?,
    selectedSource: AudioSource,
    isLoadingAudio: Boolean,
    onSelect: (AudioSource) -> Unit,
    onPickNew: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean,
) {
    val context = LocalContext.current
    WizardScaffold(
        title          = "Audio source",
        onBack         = onBack,
        primaryLabel   = "Continue",
        onPrimary      = onContinue,
        primaryEnabled = continueEnabled && !isLoadingAudio,
    ) {
        Spacer(Modifier.height(8.dp))
        if (isLoadingAudio) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Loading audio…", color = DimWhite, fontSize = 14.sp)
                }
            }
        } else {
            RadioRow(
                selected  = selectedSource == AudioSource.UseCurrent,
                enabled   = currentAudio != null,
                label     = "Use current song",
                sublabel  = if (currentAudio != null) audioFileName(currentAudio, context)
                            else "(no song loaded)",
                onClick   = { onSelect(AudioSource.UseCurrent) },
            )
            RadioRow(
                selected  = selectedSource == AudioSource.PickNew,
                label     = "Pick a different song",
                sublabel  = if (selectedSource == AudioSource.PickNew && pickedAudio != null)
                                audioFileName(pickedAudio, context) else null,
                onClick   = {
                    if (selectedSource != AudioSource.PickNew || pickedAudio == null) {
                        onSelect(AudioSource.PickNew)
                        onPickNew()
                    }
                },
            )
            RadioRow(
                selected  = selectedSource == AudioSource.Silent,
                label     = "No audio (silent video)",
                onClick   = { onSelect(AudioSource.Silent) },
            )
        }
    }
}

@Composable
private fun Step3Beat(
    beatResponse: Boolean,
    onSelect: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    WizardScaffold(
        title        = "Beat response",
        onBack       = onBack,
        primaryLabel = "Continue",
        onPrimary    = onContinue,
    ) {
        Spacer(Modifier.height(8.dp))
        RadioRow(
            selected = beatResponse,
            label    = "Beat-driven",
            sublabel = "Cylinder shakes and ribbons collapse on beats",
            onClick  = { onSelect(true) },
        )
        RadioRow(
            selected = !beatResponse,
            label    = "Passive",
            sublabel = "No shake or collapse; smooth continuous motion",
            onClick  = { onSelect(false) },
        )
    }
}

@Composable
private fun Step4Resolution(
    selected: ExportResolution,
    onSelect: (ExportResolution) -> Unit,
    selectedFps: Int,
    onSelectFps: (Int) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val is4K = selected == ExportResolution.UHD_4K
    WizardScaffold(
        title        = "Resolution & frame rate",
        onBack       = onBack,
        primaryLabel = "Continue",
        onPrimary    = onStart,
    ) {
        Spacer(Modifier.height(8.dp))
        ExportResolution.entries.forEach { res ->
            RadioRow(
                selected      = selected == res,
                label         = res.label,
                sublabel      = if (res == ExportResolution.UHD_4K) "Significantly slower; large file (~100 MB/min)" else null,
                sublabelColor = if (res == ExportResolution.UHD_4K) Color(0xFFFFAA00) else null,
                onClick       = { onSelect(res) },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.height(8.dp))

        RadioRow(
            selected = if (is4K) true else selectedFps == 30,
            label    = "30 fps",
            sublabel = if (is4K) "4K exports are limited to 30 fps"
                       else "Recommended for TikTok / Reels / Shorts — smaller file, no re-encoding",
            sublabelColor = if (is4K) Color(0xFFFFAA00) else null,
            onClick  = { if (!is4K) onSelectFps(30) },
        )
        RadioRow(
            selected  = !is4K && selectedFps == 60,
            enabled   = !is4K,
            label     = "60 fps",
            sublabel  = "Recommended for YouTube / archival — smoother motion",
            onClick   = { onSelectFps(60) },
        )
    }
}

@Composable
private fun Step5Kaleido(
    kaleidoSettings: KaleidoSettings,
    onUpdate: (KaleidoSettings) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    estimatedBytes: Long,
    freeBytes: Long,
    spaceStatus: SpaceStatus,
    previousExportsCount: Int,
    previousExportsBytes: Long,
    onClearPreviousExports: ((Int, Long) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val initialSettings = remember { kaleidoSettings }
    var overrideExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    WizardScaffold(
        title          = "Kaleido settings",
        onBack         = onBack,
        primaryLabel   = "Start Export",
        onPrimary      = onStart,
        primaryEnabled = spaceStatus != SpaceStatus.HARD_BLOCK,
        primaryColor   = Color(0xFF00CC66),
    ) {
        Spacer(Modifier.height(8.dp))
        KaleidoSummaryCard(kaleidoSettings)
        Spacer(Modifier.height(16.dp))

        if (!overrideExpanded) {
            Text(
                "Defaults match your current screen settings.",
                color = DimWhite,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { overrideExpanded = true }) {
                Text("Override for this export", color = NeonCyan)
            }
        } else {
            Text(
                "Adjusting for this export only:",
                color = DimWhite,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            ExportKaleidoOverrideContent(
                settings = kaleidoSettings,
                onUpdate = onUpdate,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                onUpdate(initialSettings)
                overrideExpanded = false
            }) {
                Text("Cancel override", color = DimWhite)
            }
        }

        // ── Storage estimate ─────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.height(10.dp))

        val estText  = if (estimatedBytes > 0L) StorageEstimator.formatBytes(estimatedBytes) else "…"
        val freeText = if (freeBytes > 0L)      StorageEstimator.formatBytes(freeBytes)      else "…"
        Text(
            text = "Estimated: $estText  ·  Free: $freeText",
            color = when (spaceStatus) {
                SpaceStatus.OK         -> DimWhite
                SpaceStatus.SOFT_WARN  -> Color(0xFFFFB020)
                SpaceStatus.HARD_BLOCK -> Color(0xFFFF4040)
            },
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        if (spaceStatus != SpaceStatus.OK) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = if (spaceStatus == SpaceStatus.HARD_BLOCK)
                        Color(0xFF3A1010) else Color(0xFF3A2810),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = when (spaceStatus) {
                            SpaceStatus.HARD_BLOCK -> "Not enough free space"
                            SpaceStatus.SOFT_WARN  -> "Storage is getting tight"
                            SpaceStatus.OK         -> ""
                        },
                        color = if (spaceStatus == SpaceStatus.HARD_BLOCK)
                            Color(0xFFFF6060) else Color(0xFFFFC050),
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (spaceStatus) {
                            SpaceStatus.HARD_BLOCK ->
                                "This export needs $estText but only $freeText is free. Clear space and try again."
                            SpaceStatus.SOFT_WARN  ->
                                "This export needs $estText. After exporting you'll have very little room left."
                            SpaceStatus.OK         -> ""
                        },
                        color    = Color.White,
                        fontSize = 13.sp,
                    )
                    if (previousExportsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showClearConfirm = true },
                            colors  = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        ) {
                            Text(
                                "Clear $previousExportsCount previous export${if (previousExportsCount == 1) "" else "s"}" +
                                    " (${StorageEstimator.formatBytes(previousExportsBytes)})",
                                color = Color.Black,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // ── Clear-previous confirmation dialog ────────────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = Color(0xFF1A1A24),
            title = { Text("Delete $previousExportsCount previous export${if (previousExportsCount == 1) "" else "s"}?", color = NeonCyan) },
            text  = {
                Text(
                    "This will permanently delete ${StorageEstimator.formatBytes(previousExportsBytes)} of video files " +
                        "from Movies/Abstrakt/. Any export currently in progress is not affected.",
                    color = DimWhite,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearPreviousExports { count, freed ->
                        Toast.makeText(
                            context,
                            "Deleted $count file${if (count == 1) "" else "s"}, freed ${StorageEstimator.formatBytes(freed)}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text("Delete", color = Color(0xFFFF4040)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = DimWhite) }
            },
        )
    }
}

@Composable
private fun KaleidoSummaryCard(settings: KaleidoSettings) {
    val orientationSuffix = when {
        settings.foldCount == 4 && settings.squareRotationLocked -> " (Square)"
        settings.foldCount == 4 -> " (Diamond)"
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222230))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryRow("Shape", settings.shapeKind.name)
        SummaryRow("Fold count", "${settings.foldCount}$orientationSuffix")
        SummaryRow("Invert colors", if (settings.invertColors) "On" else "Off")
        SummaryRow("Colorize", if (settings.colorizeEnabled) "${settings.colorizeHue.toInt()}°" else "Off")
        SummaryRow("Distortion", if (settings.distortionEnabled)
            "${(settings.distortionAmplitude * 100).toInt()}% @ ${String.format("%.1f", settings.distortionFrequency)}"
        else "Off")
        SummaryRow("Distortion Plus", if (settings.distortionPlusEnabled)
            "Y${settings.distortionPlusYaw.toInt()} P${settings.distortionPlusPitch.toInt()} R${settings.distortionPlusRoll.toInt()}"
        else "Off")
        val partyLine = buildString {
            append("Party: ${if (settings.partyEnabled) "on" else "off"}")
            append(" | Random: ${if (settings.randomEnabled) "on" else "off"}")
            if (settings.partyEnabled || settings.randomEnabled)
                append(" | ${(settings.partyIntensity * 100).toInt()}%")
        }
        SummaryRow("Auto", partyLine)
        SummaryRow("Beat reactivity", "${(settings.beatReactivity * 100).toInt()}%")
        SummaryRow("Frame shape", settings.frameShape.name)
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Frame color", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                Checkerboard(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(settings.frameColorArgb.toInt())))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "#%08X".format(settings.frameColorArgb),
                color      = DimWhite,
                fontSize   = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.White,  fontSize = 14.sp)
        Text(value, color = DimWhite, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportKaleidoOverrideContent(
    settings: KaleidoSettings,
    onUpdate: (KaleidoSettings) -> Unit,
) {
    var showColorPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Shape", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ShapeKind.entries.toList()) { kind ->
                FilterChip(
                    selected = settings.shapeKind == kind,
                    onClick  = { onUpdate(settings.copy(shapeKind = kind)) },
                    label    = { Text(kind.name) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Fold count: ${settings.foldCount}", color = Color.White, fontSize = 14.sp)
        Slider(
            value         = settings.foldCount.toFloat(),
            onValueChange = { onUpdate(settings.copy(foldCount = it.toInt())) },
            valueRange    = 2f..24f,
            steps         = 21,
        )

        if (settings.foldCount == 4) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !settings.squareRotationLocked,
                    onClick  = { onUpdate(settings.copy(squareRotationLocked = false)) },
                    label    = { Text("Diamond") },
                )
                FilterChip(
                    selected = settings.squareRotationLocked,
                    onClick  = { onUpdate(settings.copy(squareRotationLocked = true)) },
                    label    = { Text("Square") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Frame shape", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FrameShape.entries) { shape ->
                FilterChip(
                    selected = settings.frameShape == shape,
                    onClick  = { onUpdate(settings.copy(frameShape = shape)) },
                    label    = { Text(shape.name) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Frame color", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                    .clickable { showColorPicker = true },
            ) {
                Checkerboard(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color(settings.frameColorArgb.toInt())))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "#%08X".format(settings.frameColorArgb),
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text("Tap to change", color = DimWhite, fontSize = 12.sp)
            }
        }

        if (showColorPicker) {
            FrameColorPickerDialog(
                initialColorArgb = settings.frameColorArgb,
                onDismiss        = { showColorPicker = false },
                onConfirm        = { newArgb ->
                    onUpdate(settings.copy(frameColorArgb = newArgb))
                    showColorPicker = false
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Effects", color = NeonCyan, fontSize = 12.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(4.dp))

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(invertColors = !settings.invertColors)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Invert colors", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked         = settings.invertColors,
                onCheckedChange = { onUpdate(settings.copy(invertColors = it)) },
            )
        }

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(colorizeEnabled = !settings.colorizeEnabled)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Colorize", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked         = settings.colorizeEnabled,
                onCheckedChange = { onUpdate(settings.copy(colorizeEnabled = it)) },
            )
        }

        if (settings.colorizeEnabled) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val swatchColor = run {
                    val hsv = floatArrayOf(settings.colorizeHue, 1f, 1f)
                    Color(android.graphics.Color.HSVToColor(hsv))
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .border(1.dp, Color(0x44FFFFFF), CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value         = settings.colorizeHue,
                    onValueChange = { onUpdate(settings.copy(colorizeHue = it)) },
                    valueRange    = 0f..360f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${settings.colorizeHue.toInt()}°",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(distortionEnabled = !settings.distortionEnabled)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Distortion", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked         = settings.distortionEnabled,
                onCheckedChange = { onUpdate(settings.copy(distortionEnabled = it)) },
            )
        }

        if (settings.distortionEnabled) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Amp", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value         = settings.distortionAmplitude,
                    onValueChange = { onUpdate(settings.copy(distortionAmplitude = it)) },
                    valueRange    = 0f..1f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${(settings.distortionAmplitude * 100).toInt()}%",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Freq", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value         = settings.distortionFrequency,
                    onValueChange = { onUpdate(settings.copy(distortionFrequency = it)) },
                    valueRange    = 0.5f..8f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = String.format("%.1f", settings.distortionFrequency),
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(distortionPlusEnabled = !settings.distortionPlusEnabled)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Distortion Plus", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked         = settings.distortionPlusEnabled,
                onCheckedChange = { onUpdate(settings.copy(distortionPlusEnabled = it)) },
            )
        }

        if (settings.distortionPlusEnabled) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Yaw", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value         = settings.distortionPlusYaw,
                    onValueChange = { onUpdate(settings.copy(distortionPlusYaw = it)) },
                    valueRange    = -180f..180f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${settings.distortionPlusYaw.toInt()}°",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pitch", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value         = settings.distortionPlusPitch,
                    onValueChange = { onUpdate(settings.copy(distortionPlusPitch = it)) },
                    valueRange    = -90f..90f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${settings.distortionPlusPitch.toInt()}°",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Roll", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                Slider(
                    value         = settings.distortionPlusRoll,
                    onValueChange = { onUpdate(settings.copy(distortionPlusRoll = it)) },
                    valueRange    = -180f..180f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${settings.distortionPlusRoll.toInt()}°",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
        }

        // ── Auto: Party + Random ──────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(partyEnabled = !settings.partyEnabled)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Party mode",
                color    = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked         = settings.partyEnabled,
                onCheckedChange = { onUpdate(settings.copy(partyEnabled = it)) },
            )
        }

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { onUpdate(settings.copy(randomEnabled = !settings.randomEnabled)) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Random mode",
                color    = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked         = settings.randomEnabled,
                onCheckedChange = { onUpdate(settings.copy(randomEnabled = it)) },
            )
        }

        if (settings.partyEnabled || settings.randomEnabled) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Intensity",
                    color    = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.width(64.dp),
                )
                Slider(
                    value         = settings.partyIntensity,
                    onValueChange = { onUpdate(settings.copy(partyIntensity = it)) },
                    valueRange    = 0f..1f,
                    modifier      = Modifier.weight(1f),
                )
                Text(
                    text       = "${(settings.partyIntensity * 100).toInt()}%",
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier   = Modifier.width(40.dp).padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Step6Progress(
    progress: Float,
    phase: String,
    etaMs: Long,
    onCancel: () -> Unit,
) {
    val etaText = when {
        etaMs < 0 || phase.startsWith("Analyzing") -> "calculating…"
        etaMs < 60_000 -> "about ${etaMs / 1000}s remaining"
        else -> "about ${etaMs / 60_000}m ${(etaMs % 60_000) / 1000}s remaining"
    }
    val pct = (progress * 100).toInt()
    val phaseLabel = if (phase.isEmpty()) "Starting…" else "$phase $pct%"

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(BgColor),
        contentAlignment = Alignment.Center,
    ) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CircularProgressIndicator(
                    progress    = { progress },
                    color       = NeonCyan,
                    modifier    = Modifier.size(80.dp),
                    strokeWidth = 6.dp,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 48.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(phaseLabel, color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(etaText, color = DimWhite, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color    = NeonCyan,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onCancel,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC2222)),
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    progress    = { progress },
                    color       = NeonCyan,
                    modifier    = Modifier.size(80.dp),
                    strokeWidth = 6.dp,
                )
                Spacer(Modifier.height(24.dp))
                Text(phaseLabel, color = NeonCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(etaText, color = DimWhite, fontSize = 14.sp)
                Spacer(Modifier.height(48.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color    = NeonCyan,
                )
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = onCancel,
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC2222)),
                ) {
                    Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun Step6Done(
    name: String,
    sizePx: Long,
    uri: Uri?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(BgColor).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF00CC66).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color(0xFF00CC66), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Export complete!", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(name, color = DimWhite, fontSize = 13.sp)
        Text(formatBytes(sizePx), color = DimWhite.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))

        if (uri != null) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try { context.startActivity(Intent.createChooser(intent, "Open with")) }
                    catch (_: Exception) { Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show() }
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            ) { Text("View", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share via"))
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF223344)),
            ) { Text("Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Done", color = DimWhite, fontSize = 16.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun WizardScaffold(
    title: String,
    onBack: (() -> Unit)?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryColor: Color = NeonCyan,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BgColor).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 12.dp)) {
            if (onBack != null) {
                Text(
                    "‹",
                    modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(8.dp),
                    color = NeonCyan, fontSize = 28.sp,
                )
            }
            Text(
                title,
                modifier = Modifier.align(Alignment.Center),
                color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            content  = content,
        )

        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick  = onPrimary,
                enabled  = primaryEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = primaryColor),
            ) {
                Text(primaryLabel, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    enabled: Boolean = true,
    label: String,
    sublabel: String? = null,
    sublabelColor: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick  = null,
            enabled  = enabled,
            colors   = RadioButtonDefaults.colors(
                selectedColor   = NeonCyan,
                unselectedColor = DimWhite.copy(alpha = 0.5f),
            ),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                color    = if (enabled) Color.White else DimWhite.copy(alpha = 0.4f),
                fontSize = 16.sp,
            )
            if (sublabel != null) {
                Text(
                    sublabel,
                    color    = sublabelColor ?: DimWhite.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ModeRow(mode: Mode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(modeColor(mode)),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            modeName(mode),
            modifier = Modifier.weight(1f),
            color    = Color.White,
            fontSize = 16.sp,
        )
        RadioButton(
            selected = selected,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(selectedColor = NeonCyan, unselectedColor = DimWhite.copy(alpha = 0.5f)),
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildExportModeList(): List<Mode> = buildList {
    add(Mode.Cyclone)
    for (i in 1..5) add(Mode.Builtin(i))
    for (slot in SkinSlotRegistry.filledSlots()) add(Mode.UserSlot(slot.index))
}

private fun modeColor(mode: Mode): Color = when (mode) {
    Mode.Cyclone     -> Color(0xFF00FFFF)
    is Mode.Builtin  -> listOf(
        Color(0xFF00FFFF), Color(0xFF5533AA), Color(0xFFCCCCCC),
        Color(0xFF0D5559), Color(0xFF3D0D15),
    ).getOrElse(mode.skinIndex - 1) { Color(0xFF404050) }
    is Mode.UserSlot -> Color(0xFF6633CC)
    Mode.AddSlot     -> Color(0xFF404050)
}

private fun modeName(mode: Mode): String = when (mode) {
    Mode.Cyclone     -> "Cyclone"
    is Mode.Builtin  -> "Skin ${mode.skinIndex}"
    is Mode.UserSlot -> "My Skin ${mode.slotIndex + 1}"
    Mode.AddSlot     -> "Add Slot"
}

internal fun resolveUserSkinPath(mode: Mode): String? {
    if (mode !is Mode.UserSlot) return null
    return SkinSlotRegistry.filledSlots().find { it.index == mode.slotIndex }?.file?.absolutePath
}

private fun audioFileName(audioFile: AudioFile, context: Context): String {
    return try {
        context.contentResolver.query(
            audioFile.uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: audioFile.uri.lastPathSegment ?: "audio file"
    } catch (_: Exception) { "audio file" }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L     -> "%.1f KB".format(bytes / 1_000.0)
    else                -> "$bytes B"
}

internal fun timestamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))

internal fun createMediaStoreEntry(context: Context, displayName: String): Uri {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Abstrakt")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Failed to create MediaStore entry")
}

internal fun finalizeMediaStoreEntry(context: Context, uri: Uri) {
    val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
    context.contentResolver.update(uri, values, null, null)
}

internal fun deleteMediaStoreEntry(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}
