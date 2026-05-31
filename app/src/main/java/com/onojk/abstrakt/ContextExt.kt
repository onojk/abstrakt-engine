package com.onojk.abstrakt

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks the ContextWrapper chain until it finds the host Activity.
 * Returns null only if called from a non-Activity context (e.g. a Service).
 *
 * Required because Compose Popup-based containers (ModalBottomSheet, Dialog,
 * DropdownMenu) wrap LocalContext.current in a ContextWrapper, so a plain
 * `LocalContext.current as? Activity` cast returns null inside those composables.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity       -> this
    is ContextWrapper -> baseContext.findActivity()
    else              -> null
}
