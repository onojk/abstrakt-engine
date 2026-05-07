package com.example.myfistapp

import android.app.Application
import java.io.File

class AbstraktApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SkinSlotRegistry.init(this)
        // Clean up any leftover temp camera files from a previous killed session.
        File(filesDir, "camera_captures").listFiles()?.forEach { it.delete() }
    }
}
