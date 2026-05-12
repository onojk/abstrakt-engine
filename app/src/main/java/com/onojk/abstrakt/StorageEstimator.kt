package com.onojk.abstrakt

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "StorageEstimator"

data class ExportEntry(val uri: Uri, val displayName: String, val size: Long)

object StorageEstimator {

    /**
     * Estimated bytes for an MP4 export.
     * video = bitrate * duration / 8
     * audio = 128 kbps * duration / 8 (skipped if no audio)
     * +2% MP4 container overhead
     */
    fun estimateBytes(videoBitrate: Int, durationSec: Double, hasAudio: Boolean): Long {
        val videoBytes = (videoBitrate.toLong() * durationSec / 8.0).toLong()
        val audioBytes = if (hasAudio) (128_000L * durationSec / 8.0).toLong() else 0L
        return ((videoBytes + audioBytes) * 1.02).toLong()
    }

    /** Free bytes on the external volume MediaStore writes to. */
    fun freeBytesOnExternal(context: Context): Long {
        val path = Environment.getExternalStorageDirectory()
        val stat = StatFs(path.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /** Pretty-print: "847 KB" / "142 MB" / "8.2 GB". */
    fun formatBytes(bytes: Long): String {
        val kb = 1024.0; val mb = kb * 1024; val gb = mb * 1024
        return when {
            bytes >= gb -> "%.1f GB".format(bytes / gb)
            bytes >= mb -> "%.0f MB".format(bytes / mb)
            bytes >= kb -> "%.0f KB".format(bytes / kb)
            else        -> "$bytes B"
        }
    }

    /** Lists Movies/Abstrakt/ exports from MediaStore, newest-first. */
    suspend fun listPreviousExports(context: Context): List<ExportEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExportEntry>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args      = arrayOf("${Environment.DIRECTORY_MOVIES}/Abstrakt%")
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection, args,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            while (cursor.moveToNext()) {
                val id  = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                results += ExportEntry(uri, cursor.getString(nameCol) ?: "", cursor.getLong(sizeCol))
            }
        }
        results
    }

    /**
     * Deletes all exports in Movies/Abstrakt/ via ContentResolver.
     * Returns (deletedCount, totalBytesFreed). Continues past individual failures.
     */
    suspend fun deleteAllPreviousExports(context: Context): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val entries = listPreviousExports(context)
        var freed = 0L; var count = 0
        for (e in entries) {
            try {
                if (context.contentResolver.delete(e.uri, null, null) > 0) {
                    count++; freed += e.size
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to delete ${e.displayName}: ${ex.message}")
            }
        }
        Pair(count, freed)
    }
}
