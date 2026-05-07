package com.example.myfistapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
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
import com.example.myfistapp.audio.AudioFile
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

private val BgColor  = Color(0xFF0A0A0F)
private val NeonCyan = Color(0xFF00FFFF)
private val DimWhite = Color(0x99FFFFFF)

// Kept for Builtin mode config lookup — not used as current-mode state.
private enum class Viz(val label: String) {
    GL_CYCLONE("Cyclone"),
    SKIN1("Skin 1"),
    SKIN2("Skin 2"),
    SKIN3("Skin 3"),
    SKIN4("Skin 4"),
    SKIN5("Skin 5"),
}

private data class SkinConfig(
    val painter: Painter,
    val skinIndex: Int,
    val folds: Float,
    val rr: Float, val rg: Float, val rb: Float,
    val beatThreshold: Float,
)

private val VIZ_CONFIG = mapOf(
    Viz.GL_CYCLONE to SkinConfig(Painter.IMAGE,      0,  12f, 0f,    0f,    0f,    0.40f),
    Viz.SKIN1      to SkinConfig(Painter.SKIN,       0,  12f, 0f,    0f,    0f,    0.40f),
    Viz.SKIN2      to SkinConfig(Painter.SKIN,       1,   8f, 0.20f, 0.05f, 0.30f, 0.30f),
    Viz.SKIN3      to SkinConfig(Painter.SKIN,       2,  16f, 0.95f, 0.95f, 0.95f, 0.50f),
    Viz.SKIN4      to SkinConfig(Painter.SKIN,       3,   6f, 0.05f, 0.20f, 0.22f, 0.35f),
    Viz.SKIN5      to SkinConfig(Painter.SKIN,       4,  10f, 0.25f, 0.05f, 0.08f, 0.45f),
)

private fun builtinConfig(skinIndex: Int): SkinConfig {
    val viz = when (skinIndex) {
        1 -> Viz.SKIN1; 2 -> Viz.SKIN2; 3 -> Viz.SKIN3
        4 -> Viz.SKIN4; 5 -> Viz.SKIN5; else -> Viz.GL_CYCLONE
    }
    return VIZ_CONFIG[viz]!!
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    var audioFile        by remember { mutableStateOf<AudioFile?>(null) }
    var isLoading        by remember { mutableStateOf(false) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    var isPlaying        by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var currentMode      by remember { mutableStateOf<Mode>(Mode.Cyclone) }
    var registryVersion  by remember { mutableIntStateOf(0) }
    var showCropper      by remember { mutableStateOf(false) }
    var pendingPhotoUri  by remember { mutableStateOf<Uri?>(null) }
    // Temp file for camera capture — held so it can be deleted after crop completes.
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    // "Replace" flow: slotIndex being replaced (null = fresh add).
    var replacingSlotIndex by remember { mutableStateOf<Int?>(null) }

    // Long-press sheet state.
    var showSlotSheet    by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    // Add-skin menu anchor — whether the dropdown is visible and which anchor triggered it.
    var showAddMenu      by remember { mutableStateOf(false) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

    // Hoist GL view so it survives mode switches (including AddSlot).
    val glView = remember { AbstraktGLSurfaceView(context) }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) glView.onResume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE  -> glView.onPause()
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

    val titleSuffix = when (val m = currentMode) {
        Mode.Cyclone    -> "cyclone"
        is Mode.Builtin -> "skin ${m.skinIndex}"
        is Mode.UserSlot -> "user ${m.slotIndex + 1}"
        Mode.AddSlot    -> "add skin"
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "abstrakt / $titleSuffix",
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

            DotsRow(modes, currentMode, currentModeIdx)

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

                // Transparent overlay — above GLSurfaceView, captures swipe + long-press.
                // Only when GL is active (AddSlotPrompt handles its own gestures).
                if (currentMode != Mode.AddSlot && !isLoading && errorMsg == null) {
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
                                    onLongPress = {
                                        if (currentMode is Mode.UserSlot) showSlotSheet = true
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

        // ── "+" button — top-right corner ─────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(NeonCyan.copy(alpha = 0.15f))
                .clickable { onAddSkinTapped() },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = NeonCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)

            // Add-skin menu anchored to the "+" button.
            DropdownMenu(
                expanded  = showAddMenu,
                onDismissRequest = { showAddMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Take Photo") },
                    onClick = { showAddMenu = false; onTakePhoto() },
                )
                DropdownMenuItem(
                    text = { Text("Pick from Gallery") },
                    onClick = { showAddMenu = false; onPickGallery() },
                )
                Box(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Photos: 1024×256 to 8000×6000, max 50 MB",
                        color = DimWhite.copy(alpha = 0.5f),
                        fontSize = 14.sp,
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
                Mode.Cyclone -> {
                    val cfg = VIZ_CONFIG[Viz.GL_CYCLONE]!!
                    view.setPainter(cfg.painter)
                    view.setSkinIndex(cfg.skinIndex)
                    view.setUserSkinFile(null)
                    view.setKaleidoFolds(cfg.folds)
                    view.setRibbonColor(cfg.rr, cfg.rg, cfg.rb)
                    view.setBeatThreshold(cfg.beatThreshold)
                }
                is Mode.Builtin -> {
                    val cfg = builtinConfig(m.skinIndex)
                    view.setPainter(cfg.painter)
                    view.setSkinIndex(cfg.skinIndex)
                    view.setUserSkinFile(null)
                    view.setKaleidoFolds(cfg.folds)
                    view.setRibbonColor(cfg.rr, cfg.rg, cfg.rb)
                    view.setBeatThreshold(cfg.beatThreshold)
                }
                is Mode.UserSlot -> {
                    val slot = SkinSlotRegistry.filledSlots().find { it.index == m.slotIndex }
                    view.setPainter(Painter.SKIN)
                    view.setSkinIndex(-1)
                    view.setUserSkinFile(slot?.file?.absolutePath)
                    view.setKaleidoFolds(12f)
                    view.setRibbonColor(0f, 0f, 0f)
                    view.setBeatThreshold(0.40f)
                }
                Mode.AddSlot -> { /* not rendered */ }
            }
        },
        modifier = modifier,
    )
}
