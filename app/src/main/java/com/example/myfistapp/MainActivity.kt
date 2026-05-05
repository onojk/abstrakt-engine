package com.example.myfistapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfistapp.ui.theme.MyFistAppTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFistAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RippleField()
                }
            }
        }
    }
}

data class Ripple(
    val id: Long,
    val x: Float,
    val y: Float,
    val color: Color,
    val startTime: Long
)

@Composable
fun RippleField() {
    var ripples by remember { mutableStateOf(listOf<Ripple>()) }
    var tapCount by remember { mutableStateOf(0) }
    var hue by remember { mutableStateOf(0f) }

    // Slowly cycle the background hue
    LaunchedEffect(Unit) {
        while (true) {
            delay(50)
            hue = (hue + 0.5f) % 360f
        }
    }

    // Clean up old ripples (older than 2 seconds)
    LaunchedEffect(ripples.size) {
        if (ripples.isNotEmpty()) {
            delay(2100)
            val now = System.currentTimeMillis()
            ripples = ripples.filter { now - it.startTime < 2000 }
        }
    }

    val backgroundColor = Color.hsv(hue, 0.3f, 0.15f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    tapCount++
                    val newRipple = Ripple(
                        id = System.currentTimeMillis(),
                        x = offset.x,
                        y = offset.y,
                        color = Color.hsv(
                            Random.nextFloat() * 360f,
                            0.8f,
                            1f
                        ),
                        startTime = System.currentTimeMillis()
                    )
                    ripples = ripples + newRipple
                }
            }
    ) {
        // Draw all active ripples
        ripples.forEach { ripple ->
            AnimatedRipple(ripple)
        }

        // Counter at bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Tap anywhere",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            Text(
                text = "$tapCount",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title at top
        Text(
            text = "Abstrakt: ripples",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AnimatedRipple(ripple: Ripple) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(ripple.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = 400f
        val currentRadius = maxRadius * progress.value
        val alpha = 1f - progress.value

        // Draw 3 concentric rings with offset radii
        for (i in 0..2) {
            val offsetRadius = currentRadius - (i * 30f)
            if (offsetRadius > 0) {
                drawCircle(
                    color = ripple.color.copy(alpha = alpha * 0.8f),
                    radius = offsetRadius,
                    center = Offset(ripple.x, ripple.y),
                    style = Stroke(width = 4f - i)
                )
            }
        }
    }
}