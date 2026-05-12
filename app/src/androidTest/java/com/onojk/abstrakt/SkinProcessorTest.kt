package com.onojk.abstrakt

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SkinProcessorTest {

    @Test
    fun processPortraitImage_producesCorrectOutput() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Write a synthetic 1080×1920 portrait bitmap to internal storage.
        val src = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val srcFile = File(context.filesDir, "test_portrait.jpg")
        FileOutputStream(srcFile).use { src.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        src.recycle()

        val uri    = Uri.fromFile(srcFile)
        val result = processSkinFromUri(context, uri, 0.5f)

        assertTrue("output file must exist", result.exists())
        assertTrue("output must be >0 bytes", result.length() > 0)

        val out = android.graphics.BitmapFactory.decodeFile(result.absolutePath)
        assertNotNull("output must be a valid bitmap", out)
        assertEquals("height must be 256", 256, out!!.height)
        // tier = (1080/16 = 67.5 → resize to 256h gives w≈1080px → tier=1024) × 2 = 2048
        assertTrue("width must be a valid tier×2 value", out.width in listOf(2048, 4096, 8192))
        out.recycle()

        result.delete()
        srcFile.delete()
    } }

    @Test
    fun processPanoramaImage_producesCorrectOutput() { runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 4096×256 panorama — exactly 16:1.
        val src = Bitmap.createBitmap(4096, 256, Bitmap.Config.ARGB_8888)
        val srcFile = File(context.filesDir, "test_panorama.jpg")
        FileOutputStream(srcFile).use { src.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        src.recycle()

        val uri    = Uri.fromFile(srcFile)
        val result = processSkinFromUri(context, uri, 0.5f)

        assertTrue(result.exists())

        val out = android.graphics.BitmapFactory.decodeFile(result.absolutePath)
        assertNotNull(out)
        assertEquals(256, out!!.height)
        assertTrue(out.width in listOf(2048, 4096, 8192))
        out.recycle()

        result.delete()
        srcFile.delete()
    } }
}
