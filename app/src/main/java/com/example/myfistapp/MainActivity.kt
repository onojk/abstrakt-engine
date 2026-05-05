package com.example.myfistapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.myfistapp.ui.theme.MyFistAppTheme
import com.example.myfistapp.visualizers.WaveformView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyFistAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WaveformView()
                }
            }
        }
    }
}
