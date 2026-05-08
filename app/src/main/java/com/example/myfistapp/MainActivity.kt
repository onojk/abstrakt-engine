package com.example.myfistapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.StreamingAnalyzer
import com.example.myfistapp.audio.loadAndAnalyze
import com.example.myfistapp.gl.AbstraktGLSurfaceView
import com.example.myfistapp.gl.GlVizMode
import com.example.myfistapp.gl.Painter
import com.example.myfistapp.ui.theme.MyFistAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Carousel mode model — sealed hierarchy for the swipe carousel.
sealed class Mode {
    object Cyclone : Mode()
    data class Builtin(val skinIndex: Int) : Mode()  // 1..5 → SKIN1..SKIN5
    data class UserSlot(val slotIndex: Int) : Mode() // 0..39
    object AddSlot : Mode()
}

internal val BgColor  = Color(0xFF0A0A0F)
internal val NeonCyan = Color(0xFF00FFFF)
internal val DimWhite = Color(0x99FFFFFF)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            MyFistAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VisualizerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualizerScreen() {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val exportVm: ExportViewModel = viewModel()
    val isWizardOpen by exportVm.isWizardOpen.collectAsStateWithLifecycle()

    val kaleidoVm: KaleidoSettingsViewModel = viewModel()
    val kaleidoSettings by kaleidoVm.settings.collectAsStateWithLifecycle()

    var audioFile        by remember { mutableStateOf<AudioFile?>(null) }
    var isLoading        by remember { mutableStateOf(false) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    var isPlaying        by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var currentMode      by remember { mutableStateOf<Mode>(Mode.Cyclone) }
    var registryVersion  by remember { mutableIntStateOf(0) }
    var showCropper      by remember { mutableStateOf(false) }
    var pendingPhotoUri  by remember { mutableStateOf<Uri?>(null) }
    val isRendererReadyState = remember { mutableStateOf(false) }
    var isRendererReady  by isRendererReadyState
    // Temp file for camera capture — held so it can be deleted after crop completes.
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    // "Replace" flow: slotIndex being replaced (null = fresh add).
    var replacingSlotIndex by remember { mutableStateOf<Int?>(null) }

    // Mic state.
    var isMicActive         by remember { mutableStateOf(false) }
    var wasPlayingBeforeMic by remember { mutableStateOf(false) }
    val micAnalyzer = remember { StreamingAnalyzer() }
    val micCapture  = remember { MicCapture(micAnalyzer) }

    // Long-press sheet state.
    var showSlotSheet    by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Add-skin menu anchor — whether the dropdown is visible and which anchor triggered it.
    var showAddMenu      by remember { mutableStateOf(false) }
    // Kaleido settings sheet — survives rotation via ViewModel StateFlow.
    val isSettingsOpen by kaleidoVm.isSheetOpen.collectAsStateWithLifecycle()

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    // Hoist GL view so it survives mode switches (including AddSlot).
    val glView = remember {
        AbstraktGLSurfaceView(context).also { view ->
            view.setRendererReadyCallback { isRendererReadyState.value = true }
        }
    }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) glView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE  -> {
                    glView.onPause()
                    micCapture.stop()
                    isMicActive = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }

    LaunchedEffect(audioFile?.uri) {
        val af = audioFile ?: return@LaunchedEffect
        isPlaying = false
        playbackFraction = 0f
        withContext(Dispatchers.IO) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, af.uri)
                mediaPlayer.prepare()
            } catch (_: Exception) {}
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            playbackFraction = 0f
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val dur = audioFile?.durationMs ?: 0L
            if (dur > 0) playbackFraction = mediaPlayer.currentPosition.toFloat() / dur
            delay(16L)
        }
    }

    // Push global fold count and square-rotation lock to the GL renderer whenever they change.
    LaunchedEffect(kaleidoSettings.foldCount) {
        glView.setFoldCount(kaleidoSettings.foldCount)
    }
    LaunchedEffect(kaleidoSettings.squareRotationLocked) {
        glView.setSquareRotationLocked(kaleidoSettings.squareRotationLocked)
    }
    LaunchedEffect(kaleidoSettings.frameShape) {
        glView.setFrameShape(kaleidoSettings.frameShape)
    }
    LaunchedEffect(kaleidoSettings.frameColorArgb) {
        glView.setFrameColorArgb(kaleidoSettings.frameColorArgb)
    }

    val livePulse = remember { Animatable(0.4f) }

    // Feeds streaming mic snapshots into the GL renderer and drives the LIVE pulse animation.
    LaunchedEffect(isMicActive) {
        if (isMicActive) {
            launch {
                while (true) {
                    livePulse.animateTo(1.0f, animationSpec = tween<Float>(500))
                    livePulse.animateTo(0.4f, animationSpec = tween<Float>(500))
                }
            }
            while (true) {
                glView.setLiveSnapshot(micAnalyzer.streamingSnapshot())
                delay(16L)
            }
        } else {
            livePulse.snapTo(0.4f)
            glView.setLiveSnapshot(null)
        }
    }

    // ── Photo picker (gallery) ────────────────────────────────────────────────
    val photoPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { validatePhotoForSkin(context, uri) }
                when (result) {
                    is ValidationResult.Ok     -> { pendingPhotoUri = uri; showCropper = true }
                    is ValidationResult.Reject ->
                        Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Camera capture ────────────────────────────────────────────────────────
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = pendingCameraUri
            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { validatePhotoForSkin(context, uri) }
                    when (result) {
                        is ValidationResult.Ok     -> { pendingPhotoUri = uri; showCropper = true }
                        is ValidationResult.Reject -> {
                            Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                            pendingCameraFile?.delete()
                            pendingCameraFile = null
                            pendingCameraUri  = null
                        }
                    }
                }
            }
        } else {
            // User cancelled or capture failed — discard temp file.
            pendingCameraFile?.delete()
            pendingCameraFile = null
            pendingCameraUri  = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera(context) { file, uri ->
            pendingCameraFile = file
            pendingCameraUri  = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wasPlayingBeforeMic = isPlaying
            if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
            micCapture.start(scope)
            isMicActive = true
        } else {
            Toast.makeText(
                context,
                "Microphone permission denied. Enable it in Settings → App permissions.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun onTakePhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera(context) { file, uri ->
                pendingCameraFile = file
                pendingCameraUri  = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onPickGallery() {
        photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    fun onMicToggle() {
        if (isMicActive) {
            micCapture.stop()
            isMicActive = false
            if (wasPlayingBeforeMic && audioFile != null) {
                try { mediaPlayer.start(); isPlaying = true } catch (_: IllegalStateException) {}
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                wasPlayingBeforeMic = isPlaying
                if (isPlaying) { mediaPlayer.pause(); isPlaying = false }
                micCapture.start(scope)
                isMicActive = true
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Shared "save skin" logic used by both fresh add and replace.
    fun onCropConfirm(file: File) {
        val replacing = replacingSlotIndex
        if (replacing != null) {
            // Replace: invalidate old texture, swap file.
            val old = SkinSlotRegistry.filledSlots().find { it.index == replacing }?.file
            if (old != null) glView.invalidateUserSkinTexture(old.absolutePath)
            SkinSlotRegistry.replaceSlot(replacing, file)
            registryVersion++
            currentMode = Mode.UserSlot(replacing)
        } else {
            val slot = SkinSlotRegistry.firstEmptySlotIndex()
            if (slot == null) {
                Toast.makeText(context, "All 40 slots are full", Toast.LENGTH_SHORT).show()
            } else {
                SkinSlotRegistry.fillSlot(slot, file)
                registryVersion++
                currentMode = Mode.UserSlot(slot)
                Toast.makeText(context, "Skin saved to slot ${slot + 1}", Toast.LENGTH_SHORT).show()
            }
        }
        replacingSlotIndex = null
    }

    // ── Audio launcher ────────────────────────────────────────────────────────
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                errorMsg  = null
                try {
                    audioFile = loadAndAnalyze(context, uri)
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Unknown error"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Swipe hint — once on first launch.
    val prefs = remember { context.getSharedPreferences("abstrakt_prefs", Context.MODE_PRIVATE) }
    var hintVisible by remember { mutableStateOf(!prefs.getBoolean("swipe_hint_seen", false)) }
    val hintAlpha by animateFloatAsState(
        targetValue  = if (hintVisible) 1f else 0f,
        animationSpec = if (hintVisible) snap() else tween(1500),
        label = "swipe_hint",
    )
    // Fades to 0 once the GL renderer is ready — drives the loading overlay opacity.
    val loadingOverlayAlpha by animateFloatAsState(
        targetValue  = if (!isRendererReady) 1f else 0f,
        animationSpec = tween(300),
        label = "renderer_ready",
    )
    LaunchedEffect(Unit) {
        if (hintVisible) {
            delay(1000L)
            hintVisible = false
            prefs.edit().putBoolean("swipe_hint_seen", true).apply()
        }
    }

    // Carousel — rebuilt whenever the registry changes.
    val modes: List<Mode> = remember(registryVersion) {
        buildList {
            add(Mode.Cyclone)
            for (i in 1..5) add(Mode.Builtin(i))
            for (slot in SkinSlotRegistry.filledSlots()) add(Mode.UserSlot(slot.index))
            if (SkinSlotRegistry.firstEmptySlotIndex() != null) add(Mode.AddSlot)
        }
    }

    // ── Export wizard swap ────────────────────────────────────────────────────
    if (isWizardOpen) {
        ExportWizard(
            viewModel = exportVm,
            onClose   = { exportVm.closeWizard() },
        )
        return
    }

    // ── Crop screen swap ──────────────────────────────────────────────────────
    val uri = pendingPhotoUri
    if (showCropper && uri != null) {
        CropSliderScreen(
            photoUri  = uri,
            onCancel  = {
                showCropper = false
                pendingCameraFile?.delete()
                pendingCameraFile = null
                pendingCameraUri  = null
                replacingSlotIndex = null
            },
            onConfirm = { file ->
                onCropConfirm(file)
                showCropper = false
                pendingCameraFile?.delete()
                pendingCameraFile = null
                pendingCameraUri  = null
            },
        )
        return
    }

    val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    var dragAccum    by remember { mutableFloatStateOf(0f) }
    val currentModeIdx = modes.indexOf(currentMode).coerceAtLeast(0)
    val updatedModeIdx = rememberUpdatedState(currentModeIdx)
    val updatedModes   = rememberUpdatedState(modes)

    val title = when (val m = currentMode) {
        Mode.Cyclone     -> "abstrakt"
        is Mode.Builtin  -> "abstrakt / skin ${m.skinIndex}"
        is Mode.UserSlot -> "abstrakt / user ${m.slotIndex + 1}"
        Mode.AddSlot     -> "abstrakt"
    }

    // ── Add-skin entry point — checks full, then shows menu ──────────────────
    fun onAddSkinTapped() {
        if (SkinSlotRegistry.isFull()) {
            Toast.makeText(
                context,
                "All 40 slots are full. Long-press a skin to clear it.",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            showAddMenu = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Top control row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic toggle button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMicActive) Color(0xFFFF2D78).copy(alpha = 0.20f)
                        else NeonCyan.copy(alpha = 0.15f)
                    )
                    .alpha(if (isRendererReady) 1f else 0.3f)
                    .clickable(enabled = isRendererReady) { onMicToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMicActive) "Stop microphone" else "Use microphone",
                    tint = if (isMicActive) Color(0xFFFF2D78) else NeonCyan,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Export button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .alpha(if (isRendererReady) 1f else 0.3f)
                    .clickable(enabled = isRendererReady) {
                        exportVm.openWizard(currentMode, audioFile)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("↓", color = NeonCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // "+" button — Add Skin
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .alpha(if (isRendererReady) 1f else 0.3f)
                    .clickable(enabled = isRendererReady) { onAddSkinTapped() },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                DropdownMenu(
                    expanded         = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                ) {
                    DropdownMenuItem(
                        text    = { Text("Take Photo") },
                        onClick = { showAddMenu = false; onTakePhoto() },
                    )
                    DropdownMenuItem(
                        text    = { Text("Pick from Gallery") },
                        onClick = { showAddMenu = false; onPickGallery() },
                    )
                    Box(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text  = "Photos: 1024×256 to 8000×6000, max 50 MB",
                            color = DimWhite.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // ⚙ Settings button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f))
                    .clickable { kaleidoVm.openSheet() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.Settings,
                    contentDescription = "Visual settings",
                    tint               = NeonCyan,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }

        // "● LIVE" pulsing indicator
        if (isMicActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text          = "● LIVE",
                    color         = Color(0xFFFF2D78).copy(alpha = livePulse.value),
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = NeonCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { audioLauncher.launch(arrayOf("audio/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            ) {
                Text("Pick audio file", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            audioFile?.let { af ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${af.durationMs / 1000}s · ${af.sampleRate} Hz · " +
                        if (af.channelCount == 1) "mono" else "stereo",
                    color = DimWhite,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.alpha(if (isRendererReady) 1f else 0.3f)) {
                DotsRow(modes, currentMode, currentModeIdx)
            }

            Spacer(Modifier.height(10.dp))

            // Visualizer canvas / AddSlot prompt.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    currentMode == Mode.AddSlot -> AddSlotPrompt(
                        onClick   = { onAddSkinTapped() },
                        onSwipe   = { delta -> dragAccum += delta },
                        onSwipeEnd = {
                            val idx      = updatedModeIdx.value
                            val modeList = updatedModes.value
                            currentMode = when {
                                dragAccum >  swipeThresholdPx ->
                                    modeList.getOrElse(idx - 1) { modeList.first() }
                                dragAccum < -swipeThresholdPx ->
                                    modeList.getOrElse(idx + 1) { modeList.last() }
                                else -> currentMode
                            }
                            dragAccum = 0f
                        },
                    )
                    isLoading -> CircularProgressIndicator(color = NeonCyan)
                    errorMsg != null -> Text(
                        text = "Error: $errorMsg",
                        color = Color(0xFFFF4040),
                        fontSize = 14.sp,
                    )
                    else -> GlCanvas(
                        glView = glView,
                        audioFile = audioFile,
                        playbackFraction = playbackFraction,
                        currentMode = currentMode,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Loading overlay — fades out once the GL renderer signals ready.
                if (loadingOverlayAlpha > 0f && currentMode != Mode.AddSlot && !isLoading && errorMsg == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = loadingOverlayAlpha)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(loadingOverlayAlpha),
                        ) {
                            CircularProgressIndicator(
                                color    = NeonCyan,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Loading visualizer...", color = DimWhite, fontSize = 16.sp)
                        }
                    }
                }

                // Transparent overlay — above GLSurfaceView, captures swipe + long-press.
                // Only when GL is active and ready (AddSlotPrompt handles its own gestures).
                if (currentMode != Mode.AddSlot && !isLoading && errorMsg == null && isRendererReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val idx      = updatedModeIdx.value
                                        val modeList = updatedModes.value
                                        currentMode = when {
                                            dragAccum >  swipeThresholdPx ->
                                                modeList.getOrElse(idx - 1) { modeList.first() }
                                            dragAccum < -swipeThresholdPx ->
                                                modeList.getOrElse(idx + 1) { modeList.last() }
                                            else -> currentMode
                                        }
                                        dragAccum = 0f
                                    },
                                    onHorizontalDrag = { _, delta -> dragAccum += delta },
                                )
                            }
                            .pointerInput(currentMode) {
                                detectTapGestures(
                                    onDoubleTap = { glView.toggleShape() },
                                    onLongPress = {
                                        if (currentMode is Mode.UserSlot) showSlotSheet = true
                                        else glView.triggerPainterStatsDump()
                                    },
                                )
                            },
                    )
                }

                if (hintAlpha > 0f) {
                    Text(
                        text = "← swipe to change mode →",
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.alpha(hintAlpha),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (audioFile != null && !isLoading && currentMode != Mode.AddSlot) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            try {
                                mediaPlayer.start()
                                isPlaying = true
                            } catch (_: IllegalStateException) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFFF2D78) else NeonCyan,
                    ),
                ) {
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

        }
    }

    // ── Long-press bottom sheet ───────────────────────────────────────────────
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showSlotSheet) {
        val slotMode = currentMode as? Mode.UserSlot
        ModalBottomSheet(
            onDismissRequest = { showSlotSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1A1A24),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Skin ${(slotMode?.slotIndex ?: 0) + 1}",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TextButton(
                    onClick = {
                        showSlotSheet = false
                        slotMode?.let { replacingSlotIndex = it.slotIndex }
                        showAddMenu = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Replace photo", color = NeonCyan, fontSize = 16.sp)
                }
                TextButton(
                    onClick = { showSlotSheet = false; showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear this slot", color = Color(0xFFFF4040), fontSize = 16.sp)
                }
                TextButton(
                    onClick = { showSlotSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", color = DimWhite, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Clear confirmation dialog ─────────────────────────────────────────────
    if (showClearConfirm) {
        val slotMode = currentMode as? Mode.UserSlot
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Color(0xFF1A1A24),
            title = { Text("Remove this skin?", color = NeonCyan) },
            text  = { Text("This cannot be undone.", color = DimWhite) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    val idx = slotMode?.slotIndex ?: return@TextButton
                    val old = SkinSlotRegistry.filledSlots().find { it.index == idx }?.file
                    if (old != null) glView.invalidateUserSkinTexture(old.absolutePath)
                    SkinSlotRegistry.clearSlot(idx)
                    registryVersion++
                    // Navigate to the mode before the one just removed.
                    val newModes = buildList {
                        add(Mode.Cyclone)
                        for (i in 1..5) add(Mode.Builtin(i))
                        for (slot in SkinSlotRegistry.filledSlots()) add(Mode.UserSlot(slot.index))
                        if (SkinSlotRegistry.firstEmptySlotIndex() != null) add(Mode.AddSlot)
                    }
                    val removedIdx = modes.indexOf(slotMode)
                    currentMode = newModes.getOrElse(
                        (removedIdx - 1).coerceAtLeast(0)
                    ) { Mode.Cyclone }
                }) {
                    Text("Remove", color = Color(0xFFFF4040))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = DimWhite)
                }
            },
        )
    }

    // ── Kaleido settings sheet ────────────────────────────────────────────────
    if (isSettingsOpen) {
        ModalBottomSheet(
            onDismissRequest = { kaleidoVm.closeSheet() },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor   = Color(0xFF1A1A24),
        ) {
            KaleidoSettingsContent(
                settings                     = kaleidoSettings,
                onFoldCountChange            = { kaleidoVm.setFoldCount(it) },
                onSquareRotationLockedChange = { kaleidoVm.setSquareRotationLocked(it) },
                onFrameShapeChange           = { kaleidoVm.setFrameShape(it) },
                onFrameColorChange           = { kaleidoVm.setFrameColorArgb(it) },
            )
        }
    }
}

// ── Camera helpers ────────────────────────────────────────────────────────────

private fun launchCamera(context: Context, onReady: (File, Uri) -> Unit) {
    val dir  = File(context.filesDir, "camera_captures").also { it.mkdirs() }
    val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
    val uri  = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    onReady(file, uri)
}

// ── Dots indicator ────────────────────────────────────────────────────────────

@Composable
private fun DotsRow(modes: List<Mode>, currentMode: Mode, currentModeIdx: Int) {
    val hasEllipsis = SkinSlotRegistry.hasMoreEmptyAfterFirst()
    val totalDots   = modes.size + (if (hasEllipsis) 3 else 0)
    val listState   = rememberLazyListState()

    LaunchedEffect(currentModeIdx) {
        if (totalDots > 12) listState.animateScrollToItem(currentModeIdx.coerceAtLeast(0))
    }

    if (totalDots <= 12) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEachIndexed { i, mode -> ModeDot(mode, active = i == currentModeIdx) }
            if (hasEllipsis) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(DimWhite.copy(alpha = 0.20f)),
                    )
                }
            }
        }
    } else {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(modes) { i, mode -> ModeDot(mode, active = i == currentModeIdx) }
            if (hasEllipsis) {
                items(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(DimWhite.copy(alpha = 0.20f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeDot(mode: Mode, active: Boolean) {
    if (mode == Mode.AddSlot) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = if (active) NeonCyan else DimWhite.copy(alpha = 0.30f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (active) NeonCyan else DimWhite.copy(alpha = 0.30f),
                fontSize = 5.sp,
                lineHeight = 8.sp,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) NeonCyan else DimWhite.copy(alpha = 0.30f)),
        )
    }
}

// ── AddSlot prompt ────────────────────────────────────────────────────────────

@Composable
private fun AddSlotPrompt(onClick: () -> Unit, onSwipe: (Float) -> Unit, onSwipeEnd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { onSwipeEnd() },
                    onHorizontalDrag = { _, delta -> onSwipe(delta) },
                )
            }
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", color = NeonCyan, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a Skin",
            color = NeonCyan,
            fontSize = 16.sp,
            letterSpacing = 2.sp,
        )
    }
}

// ── GL canvas ─────────────────────────────────────────────────────────────────

@Composable
private fun GlCanvas(
    glView: AbstraktGLSurfaceView,
    audioFile: AudioFile?,
    playbackFraction: Float,
    currentMode: Mode,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { glView },
        update  = { view ->
            view.setAudioFile(audioFile)
            view.setPlaybackFraction(playbackFraction)
            view.setGlMode(GlVizMode.CYCLONE)
            view.setCurrentMode(currentMode)

            when (val m = currentMode) {
                Mode.AddSlot -> { /* not rendered */ }
                else -> {
                    val cfg = skinConfigForMode(m)
                    val userPath = if (m is Mode.UserSlot)
                        SkinSlotRegistry.filledSlots().find { it.index == m.slotIndex }?.file?.absolutePath
                    else null
                    view.setPainter(cfg.painter)
                    view.setSkinIndex(cfg.skinIndex)
                    view.setUserSkinFile(userPath)
                    view.setRibbonColor(cfg.rr, cfg.rg, cfg.rb)
                    view.setBeatThreshold(cfg.beatThreshold)
                }
            }
        },
        modifier = modifier,
    )
}

// ── Kaleido settings sheet content ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KaleidoSettingsContent(
    settings: KaleidoSettings,
    onFoldCountChange: (Int) -> Unit,
    onSquareRotationLockedChange: (Boolean) -> Unit,
    onFrameShapeChange: (FrameShape) -> Unit,
    onFrameColorChange: (Long) -> Unit,
) {
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Text(
            "Kaleido Settings",
            style = MaterialTheme.typography.titleLarge,
            color = NeonCyan,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Fold count: ${settings.foldCount}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Slider(
            value       = settings.foldCount.toFloat(),
            onValueChange = { onFoldCountChange(it.toInt()) },
            valueRange  = 2f..24f,
            steps       = 21,
        )
        Text(
            text  = foldCountHint(settings.foldCount),
            style = MaterialTheme.typography.bodySmall,
            color = DimWhite,
        )

        if (settings.foldCount == 4) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Orientation",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !settings.squareRotationLocked,
                    onClick  = { onSquareRotationLockedChange(false) },
                    label    = { Text("Diamond") },
                    leadingIcon = if (!settings.squareRotationLocked) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = settings.squareRotationLocked,
                    onClick  = { onSquareRotationLockedChange(true) },
                    label    = { Text("Square") },
                    leadingIcon = if (settings.squareRotationLocked) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = if (settings.squareRotationLocked)
                    "Pattern aligned to vertical and horizontal axes"
                else
                    "Pattern aligned to diagonal axes (default)",
                style    = MaterialTheme.typography.bodySmall,
                color    = DimWhite,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Frame shape",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(horizontal = 4.dp),
        ) {
            items(FrameShape.entries) { shape ->
                FilterChip(
                    selected    = settings.frameShape == shape,
                    onClick     = { onFrameShapeChange(shape) },
                    label       = { Text(shape.name) },
                    leadingIcon = if (settings.frameShape == shape) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Frame color", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))

        val currentColor = Color(settings.frameColorArgb.toInt())
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { showColorPicker = true },
            ) {
                Checkerboard(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(currentColor))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text       = "#%08X".format(settings.frameColorArgb),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color      = Color.White,
                )
                Text(
                    text  = "Tap to change",
                    style = MaterialTheme.typography.bodySmall,
                    color = DimWhite,
                )
            }
        }

        if (showColorPicker) {
            FrameColorPickerDialog(
                initialColorArgb = settings.frameColorArgb,
                onDismiss        = { showColorPicker = false },
                onConfirm        = { newArgb ->
                    onFrameColorChange(newArgb)
                    showColorPicker = false
                },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun foldCountHint(count: Int): String = when (count) {
    2          -> "Mirror — split-screen reflection"
    3          -> "Tri-mirror — three-way symmetry"
    4          -> "Cross — four-way symmetry"
    5          -> "Penta — five-way star"
    6          -> "Hex — hexagonal symmetry"
    7, 8       -> "Octagonal feel"
    9, 10, 11  -> "Decagonal — many sides"
    12         -> "Clock-face — classic kaleidoscope"
    in 13..16  -> "Fine pinwheel"
    in 17..20  -> "Very fine pinwheel"
    in 21..24  -> "Pinwheel — almost rotationally smooth"
    else       -> ""
}
