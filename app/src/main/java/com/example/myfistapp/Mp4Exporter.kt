package com.example.myfistapp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.util.Log
import android.view.Surface
import com.example.myfistapp.audio.AudioSnapshot
import com.example.myfistapp.gl.AbstraktRenderer
import com.example.myfistapp.gl.GlVizMode
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class Mp4Exporter(
    private val context: Context,
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 60,
    private val durationSeconds: Float = 5f,
    private val bitrate: Int = 10_000_000,
    private val audioSourceUri: Uri? = null,
) {
    companion object {
        private const val TAG = "Mp4Exporter"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    private lateinit var encoder: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var muxerStarted = false

    // Both tracks must be added before muxer.start() is called.
    private var videoMuxTrackIndex = -1
    private var audioMuxTrackIndex = -1
    private var videoTrackReady = false
    private var audioTrackReady = false     // true immediately if no audio source

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE

    private val renderer = AbstraktRenderer(context)
    private var audioExtractor: MediaExtractor? = null
    private val audioPumpBuffer = ByteBuffer.allocate(256 * 1024)

    suspend fun exportVisualizer(onProgress: (Float) -> Unit): Result<File> {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        return try {
            withContext(dispatcher) {
                runExport(onProgress)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        } finally {
            dispatcher.close()
        }
    }

    private fun runExport(onProgress: (Float) -> Unit) {
        outputFile.parentFile?.mkdirs()

        // ── Probe audio source ────────────────────────────────────────────────
        var effectiveDurationSec = durationSeconds
        var sourceAudioFormat: MediaFormat? = null

        if (audioSourceUri != null) {
            val fmt = detectAudioFormat(audioSourceUri)
                ?: throw IllegalStateException("No audio track found in the selected file.")
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                throw IllegalArgumentException(
                    "Audio format '$mime' requires re-encoding, not yet supported. " +
                        "Use an .m4a, .aac, or .mp4 audio file."
                )
            }
            Log.d(TAG, "Audio source: $mime (direct copy)")
            sourceAudioFormat = fmt

            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > 0) effectiveDurationSec = durationUs / 1_000_000f
            Log.d(TAG, "Audio duration: $effectiveDurationSec s, video frames: ${(effectiveDurationSec * fps).toInt()}")

            val extractor = MediaExtractor()
            extractor.setDataSource(context, audioSourceUri, null)
            for (i in 0 until extractor.trackCount) {
                val trackFmt = extractor.getTrackFormat(i)
                if (trackFmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i)
                    break
                }
            }
            audioExtractor = extractor
        }

        audioTrackReady = (audioSourceUri == null)   // no audio = don't wait for audio track

        // ── Video encoder ─────────────────────────────────────────────────────
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        setupEgl(inputSurface)

        renderer.glMode = GlVizMode.CYCLONE
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, width, height)

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add the audio track now so it's registered before muxer.start().
        // muxer.start() is called inside maybeStartMuxer() once the video track
        // also fires INFO_OUTPUT_FORMAT_CHANGED inside drainEncoder.
        if (sourceAudioFormat != null) {
            audioMuxTrackIndex = muxer.addTrack(sourceAudioFormat)
            audioTrackReady = true
            Log.d(TAG, "Audio track added to muxer: index=$audioMuxTrackIndex")
        }

        val totalFrames = (effectiveDurationSec * fps).toInt()
        val dt          = 1f / fps.toFloat()
        val silentSnap  = AudioSnapshot(FloatArray(8), 0f, false)
        Log.d(TAG, "Starting encode: ${width}x${height} @ ${fps}fps, $totalFrames frames")

        try {
            for (frameIndex in 0 until totalFrames) {
                val presentationTimeUs = frameIndex * 1_000_000L / fps
                val timeSec            = frameIndex * dt

                renderer.renderFrame(
                    surfaceWidth  = width,
                    surfaceHeight = height,
                    timeSec       = timeSec,
                    dt            = dt,
                    audioSnapshot = silentSnap,
                    mode          = Mode.Cyclone,
                )

                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1000L)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                drainEncoder(endOfStream = false)

                // Pump audio in lockstep with video — single-threaded, no concurrent muxer access.
                if (muxerStarted && audioExtractor != null) {
                    pumpAudioDirectCopy(uptoUs = presentationTimeUs)
                }

                onProgress(frameIndex.toFloat() / totalFrames)
                if (frameIndex % 60 == 0) Log.d(TAG, "Frame $frameIndex / $totalFrames")
            }

            drainEncoder(endOfStream = true)

            // Flush any audio that trails the last video frame.
            if (muxerStarted && audioExtractor != null) {
                pumpAudioDirectCopy(uptoUs = Long.MAX_VALUE)
            }

            Log.d(TAG, "Encode complete: ${outputFile.length()} bytes")
        } finally {
            audioExtractor?.release()
            audioExtractor = null
            renderer.release()
            releaseEgl()
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            inputSurface.release()
        }
    }

    // Called after each track signals its format — starts muxer once both tracks are ready.
    private fun maybeStartMuxer() {
        if (videoTrackReady && audioTrackReady && !muxerStarted) {
            muxer.start()
            muxerStarted = true
            Log.d(TAG, "Muxer started: videoTrack=$videoMuxTrackIndex audioTrack=$audioMuxTrackIndex")
        }
    }

    // Returns the MediaFormat of the first audio track in the given URI, or null.
    private fun detectAudioFormat(uri: Uri): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return fmt
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "detectAudioFormat failed: ${e.message}")
            null
        } finally {
            extractor.release()
        }
    }

    // Pumps audio samples from audioExtractor into the muxer up to uptoUs (inclusive).
    // Extractor position advances; subsequent calls continue from where the last left off.
    private fun pumpAudioDirectCopy(uptoUs: Long) {
        val ext  = audioExtractor ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val sampleTime = ext.sampleTime
            if (sampleTime < 0 || sampleTime > uptoUs) break
            audioPumpBuffer.clear()
            val size = ext.readSampleData(audioPumpBuffer, 0)
            if (size < 0) break
            info.offset            = 0
            info.size              = size
            info.presentationTimeUs = sampleTime
            info.flags             = ext.sampleFlags
            audioPumpBuffer.position(0)
            audioPumpBuffer.limit(size)
            muxer.writeSampleData(audioMuxTrackIndex, audioPumpBuffer, info)
            if (!ext.advance()) break
        }
    }

    private fun setupEgl(inputSurface: Surface) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0
        ) { "eglChooseConfig failed: ${EGL14.eglGetError()}" }
        val config = configs[0]!!

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed: ${EGL14.eglGetError()}" }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, inputSurface, surfaceAttribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed: ${EGL14.eglGetError()}" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            "eglMakeCurrent failed: ${EGL14.eglGetError()}"
        }
        Log.d(TAG, "EGL ready")
    }

    private fun releaseEgl() {
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) encoder.signalEndOfInputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        loop@ while (true) {
            val index = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break@loop
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!videoTrackReady) { "video format changed twice" }
                    videoMuxTrackIndex = muxer.addTrack(encoder.outputFormat)
                    videoTrackReady = true
                    maybeStartMuxer()
                }
                index >= 0 -> {
                    val buf = encoder.getOutputBuffer(index) ?: run {
                        encoder.releaseOutputBuffer(index, false)
                        continue@loop
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        buf.position(bufferInfo.offset)
                        buf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoMuxTrackIndex, buf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break@loop
                }
            }
        }
    }
}
