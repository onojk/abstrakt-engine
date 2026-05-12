package com.onojk.abstrakt

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporting {
    private const val PREFS = "abstrakt_prefs"
    private const val KEY   = "crash_reporting_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .apply()
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
    }

    fun setCustomKey(key: String, value: String) {
        FirebaseCrashlytics.getInstance().setCustomKey(key, value)
    }

    fun logHandledException(t: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(t)
    }
}
