package com.example.myfistapp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "SkinSlotRegistry"
private const val JSON_FILE = "skin_slots.json"
private const val USER_SKINS_DIR = "user_skins"

data class FilledSlot(val index: Int, val file: File, val createdAt: Long)

object SkinSlotRegistry {
    private const val MAX_SLOTS = 40
    private val slots: Array<FilledSlot?> = arrayOfNulls(MAX_SLOTS)
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromJson(context.applicationContext)
        val filled = filledSlots().size
        Log.d(TAG, "SkinSlotRegistry initialized: $filled filled, ${MAX_SLOTS - filled} empty")
    }

    fun firstEmptySlotIndex(): Int? =
        (0 until MAX_SLOTS).firstOrNull { slots[it] == null }

    fun fillSlot(index: Int, file: File): Boolean {
        if (index !in 0 until MAX_SLOTS) return false
        slots[index] = FilledSlot(index, file, System.currentTimeMillis())
        appContext?.let { saveToJson(it) }
        return true
    }

    fun clearSlot(index: Int): Boolean {
        if (index !in 0 until MAX_SLOTS) return false
        val slot = slots[index] ?: return false
        slots[index] = null
        slot.file.delete()
        appContext?.let { saveToJson(it) }
        return true
    }

    fun replaceSlot(index: Int, newFile: File): Boolean {
        if (index !in 0 until MAX_SLOTS) return false
        slots[index]?.file?.delete()
        slots[index] = FilledSlot(index, newFile, System.currentTimeMillis())
        appContext?.let { saveToJson(it) }
        return true
    }

    fun filledSlots(): List<FilledSlot> =
        slots.filterNotNull().sortedBy { it.index }

    fun isFull(): Boolean = filledSlots().size == MAX_SLOTS

    // True if there are empty slots beyond the first one (for "···" indicator later).
    fun hasMoreEmptyAfterFirst(): Boolean {
        val first = firstEmptySlotIndex() ?: return false
        return (first + 1 until MAX_SLOTS).any { slots[it] == null }
    }

    private fun saveToJson(context: Context) {
        val array = JSONArray()
        for (slot in filledSlots()) {
            val obj = JSONObject()
            obj.put("slotIndex", slot.index)
            obj.put("filename", slot.file.name)
            obj.put("createdAt", slot.createdAt)
            array.put(obj)
        }
        try {
            File(context.filesDir, JSON_FILE).writeText(array.toString())
        } catch (e: Exception) {
            Log.w(TAG, "saveToJson failed: ${e.message}")
        }
    }

    private fun loadFromJson(context: Context) {
        val jsonFile = File(context.filesDir, JSON_FILE)
        if (!jsonFile.exists()) return
        try {
            val array = JSONArray(jsonFile.readText())
            for (i in 0 until array.length()) {
                val obj       = array.getJSONObject(i)
                val slotIndex = obj.getInt("slotIndex")
                val filename  = obj.getString("filename")
                val createdAt = obj.getLong("createdAt")
                if (slotIndex !in 0 until MAX_SLOTS) continue
                val file = File(context.filesDir, "$USER_SKINS_DIR/$filename")
                if (!file.exists()) {
                    Log.w(TAG, "slot $slotIndex: file missing ($filename), skipping")
                    continue
                }
                slots[slotIndex] = FilledSlot(slotIndex, file, createdAt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadFromJson failed: ${e.message}")
        }
    }
}
