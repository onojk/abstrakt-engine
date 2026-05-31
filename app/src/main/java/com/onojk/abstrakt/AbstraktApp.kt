package com.onojk.abstrakt

import android.app.Application
import android.content.Context
import android.util.Log
import com.onojk.abstrakt.gl.PainterSnapshotter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AbstraktApp : Application() {

    val billingManager by lazy { BillingManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SkinSlotRegistry.init(this)
        // Clean up any leftover temp camera files from a previous killed session.
        File(filesDir, "camera_captures").listFiles()?.forEach { it.delete() }
        // Pre-warm built-in painter snapshots so first Random Mosaic tap is instant.
        appScope.launch { PainterSnapshotter.ensureBuiltinSnapshots(this@AbstraktApp) }

        // Crashlytics: respect user's opt-out preference. Default = enabled.
        // SharedPreferences is used (not DataStore) because we need a synchronous
        // read here before any coroutine context exists.
        val prefs = applicationContext.getSharedPreferences(
            "abstrakt_prefs", Context.MODE_PRIVATE
        )
        val crashReportingEnabled = prefs.getBoolean("crash_reporting_enabled", true)
        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            .setCrashlyticsCollectionEnabled(crashReportingEnabled)

        // MobileAds.initialize() is called from MainActivity after the UMP consent flow
        // completes, so it is NOT called here. Calling it before consent violates Google's
        // requirement that consent be obtained before SDK initialization.

        billingManager.initialize()
    }
}
