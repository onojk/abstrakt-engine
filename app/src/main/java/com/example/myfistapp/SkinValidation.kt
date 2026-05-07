package com.example.myfistapp

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri

sealed class ValidationResult {
    object Ok : ValidationResult()
    data class Reject(val reason: String) : ValidationResult()
}

fun validatePhotoForSkin(context: Context, uri: Uri): ValidationResult {
    // Check 1: File size ≤ 50 MB (cheapest — one syscall).
    val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    if (fileSize > 50L * 1024L * 1024L) {
        return ValidationResult.Reject(
            "Photo is too large (${fileSize / 1024 / 1024} MB). Maximum 50 MB."
        )
    }
    if (fileSize < 0) {
        return ValidationResult.Reject("Could not read photo file.")
    }

    // Check 2: Resolution ≥ 1024×256 AND ≤ 8000×6000.
    // inJustDecodeBounds reads only JPEG/PNG header — no pixel decode.
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    val w = opts.outWidth
    val h = opts.outHeight
    if (w <= 0 || h <= 0) {
        return ValidationResult.Reject("Could not read photo dimensions.")
    }
    if (w < 1024 || h < 256) {
        return ValidationResult.Reject("Photo too small: ${w}×${h}. Minimum 1024×256.")
    }
    if (w > 8000 || h > 6000) {
        return ValidationResult.Reject("Photo too large: ${w}×${h}. Maximum 8000×6000.")
    }

    // Check 3: Aspect ratio — verify at least one crop orientation yields a valid 16:1 strip.
    val canExtractStrip = w >= 1024 || (h * 16 >= 1024 && w >= h * 16)
    if (!canExtractStrip) {
        return ValidationResult.Reject(
            "Photo aspect ratio cannot produce a valid skin. Try a wider or more standard photo."
        )
    }

    return ValidationResult.Ok
}
