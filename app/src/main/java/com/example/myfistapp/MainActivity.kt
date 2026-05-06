package com.example.myfistapp

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.loadAndAnalyze
import com.example.myfistapp.gl.AbstraktGLSurfaceView
import com.example.myfistapp.gl.GlVizMode
import com.example.myfistapp.ui.theme.MyFistAppTheme
import com.example.myfistapp.visualizers.KaleidoscopeCanvas
import com.example.myfistapp.visualizers.WarpfieldCanvas
import com.example.myfistapp.visualizers.WaveformCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgColor   = Color(0xFF0A0A0F)
private val NeonCyan  = Color(0xFF00FFFF)
private val DimWhite  = Color(0x99FFFFFF)
private val DimBg     = Color(0xFF1A1A2E)

private enum class Viz(val label: String) {
    WAVEFORM("Wave"),
    WARPFIELD("Warp"),
    KALEIDO("Echo"),
    GL_TEST("GL"),
    GL_WARP("Warp"),   // GL renderer — replaces Compose Warp as the primary warp toggle
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

@Composable
private fun VisualizerScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var audioFile       by remember { mutableStateOf<AudioFile?>(null) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMsg        by remember { mutableStateOf<String?>(null) }
    var isPlaying       by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableStateOf(0f) }
    var currentViz      by remember { mutableStateOf(Viz.WAVEFORM) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { mediaPlayer.release() } }

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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                errorMsg = null
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
                text = "abstrakt / ${currentViz.label.lowercase()}",
                color = NeonCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { launcher.launch(arrayOf("audio/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            ) {
                Text("Pick audio file", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            audioFile?.let { af ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${af.durationMs / 1000}s · ${af.sampleRate} Hz · " +
                        if (af.channelCount == 1) "mono" else "stereo",
                    color = DimWhite,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Visualizer toggle — Wave | Warp(GL) | Echo | GL.  WARPFIELD is kept in the enum
            // for Echo's internal WarpfieldCanvas layer but has no top-level button.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Viz.WAVEFORM, Viz.GL_WARP, Viz.KALEIDO, Viz.GL_TEST).forEach { viz ->
                    val active = currentViz == viz
                    Button(
                        onClick = { currentViz = viz },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) NeonCyan else DimBg,
                        ),
                    ) {
                        Text(
                            text = viz.label,
                            color = if (active) Color.Black else DimWhite,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = NeonCyan)
                    errorMsg != null -> Text(
                        text = "Error: $errorMsg",
                        color = Color(0xFFFF4040),
                        fontSize = 14.sp,
                    )
                    else -> when (currentViz) {
                        Viz.WAVEFORM -> if (audioFile != null) {
                            WaveformCanvas(
                                audioFile = audioFile!!,
                                playbackFraction = playbackFraction,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("No file loaded", color = DimWhite, fontSize = 14.sp)
                        }
                        Viz.WARPFIELD -> WarpfieldCanvas(
                            audioFile = audioFile,
                            playbackFraction = playbackFraction,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Viz.KALEIDO -> KaleidoscopeCanvas(
                            audioFile = audioFile,
                            playbackFraction = playbackFraction,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Viz.GL_TEST -> GlCanvas(
                            audioFile = audioFile,
                            playbackFraction = playbackFraction,
                            glMode = GlVizMode.TEST,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Viz.GL_WARP -> GlCanvas(
                            audioFile = audioFile,
                            playbackFraction = playbackFraction,
                            glMode = GlVizMode.WARP,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (audioFile != null && !isLoading) {
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
}

@Composable
private fun GlCanvas(
    audioFile: AudioFile?,
    playbackFraction: Float,
    glMode: GlVizMode,
    modifier: Modifier = Modifier,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Scoped to this composable's lifetime: torn down when user toggles away, fresh on toggle-back.
    val glView = remember { AbstraktGLSurfaceView(context) }

    DisposableEffect(lifecycleOwner) {
        // Lifecycle is already RESUMED when this composable first enters composition;
        // the observer won't fire ON_RESUME retroactively, so kick it manually.
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView.onResume()
        }
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
            glView.onPause()   // stop GL thread when composable leaves composition
        }
    }

    AndroidView(
        factory = { glView },
        update = { view ->
            // Called on every recomposition — pushes latest audio + mode state to the GL thread.
            view.setAudioFile(audioFile)
            view.setPlaybackFraction(playbackFraction)
            view.setGlMode(glMode)
        },
        modifier = modifier,
    )
}
