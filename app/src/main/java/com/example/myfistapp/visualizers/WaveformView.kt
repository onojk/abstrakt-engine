package com.example.myfistapp.visualizers

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.loadAndAnalyze
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgColor = Color(0xFF0A0A0F)
private val NeonCyan = Color(0xFF00FFFF)
private val DimWhite = Color(0x99FFFFFF)

@Composable
fun WaveformView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var audioFile by remember { mutableStateOf<AudioFile?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableStateOf(0f) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { mediaPlayer.release() }
    }

    // Prepare MediaPlayer whenever a new file is loaded
    LaunchedEffect(audioFile?.uri) {
        val af = audioFile ?: return@LaunchedEffect
        isPlaying = false
        playbackFraction = 0f
        withContext(Dispatchers.IO) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, af.uri)
                mediaPlayer.prepare()
            } catch (_: Exception) {
                // Waveform is still shown; Play will silently no-op until fixed
            }
        }
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            playbackFraction = 0f
        }
    }

    // Cursor sweeps at ~60 fps while playing
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
                .padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "abstrakt / waveform",
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

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = NeonCyan)
                    errorMsg != null -> Text(
                        text = "Error: $errorMsg",
                        color = Color(0xFFFF4040),
                        fontSize = 14.sp,
                    )
                    audioFile != null -> WaveformCanvas(
                        audioFile = audioFile!!,
                        playbackFraction = playbackFraction,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text("No file loaded", color = DimWhite, fontSize = 14.sp)
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
                            } catch (_: IllegalStateException) {
                                // Player still preparing — ignore tap
                            }
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
private fun WaveformCanvas(
    audioFile: AudioFile,
    playbackFraction: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val envelope = audioFile.amplitudeEnvelope
        if (envelope.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val maxAmp = centerY * 0.9f

        // One vertical bar per pixel (or per envelope sample, whichever is fewer)
        val displayCount = w.toInt().coerceAtMost(envelope.size)
        val strokeW = (w / displayCount).coerceAtLeast(1.5f)

        for (i in 0 until displayCount) {
            val envIdx = (i.toFloat() / displayCount * envelope.size)
                .toInt().coerceIn(0, envelope.size - 1)
            val amp = envelope[envIdx]
            val x = w * i / displayCount
            drawLine(
                start = Offset(x, centerY - amp * maxAmp),
                end = Offset(x, centerY + amp * maxAmp),
                color = NeonCyan,
                strokeWidth = strokeW,
            )
        }

        // Playback cursor
        if (playbackFraction > 0f) {
            val cx = playbackFraction * w
            drawLine(
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                color = Color.White,
                strokeWidth = 2f,
            )
        }
    }
}
