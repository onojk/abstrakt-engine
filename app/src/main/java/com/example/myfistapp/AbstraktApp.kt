package com.example.myfistapp

import android.app.Application
import com.example.myfistapp.gl.PainterSnapshotter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AbstraktApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SkinSlotRegistry.init(this)
        // Clean up any leftover temp camera files from a previous killed session.
        File(filesDir, "camera_captures").listFiles()?.forEach { it.delete() }
        // Pre-warm built-in painter snapshots so first Random Mosaic tap is instant.
        appScope.launch { PainterSnapshotter.ensureBuiltinSnapshots(this@AbstraktApp) }
    }
}
