package com.onojk.abstrakt

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
import com.onojk.abstrakt.audio.AudioFile
import com.onojk.abstrakt.audio.AudioSnapshot
import com.onojk.abstrakt.audio.analyzeOffline
import com.onojk.abstrakt.gl.AbstraktRenderer
import com.onojk.abstrakt.gl.GlVizMode
import com.onojk.abstrakt.gl.shapeFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import java.util.concurrent.Executors

private enum class AudioPath { DECODE_REENCODE_PCM, DECODE_REENCODE_COMPRESSED }

class Mp4Exporter(
    private val context: Context,
    private val width: Int,
    private val height: Int,
    private val fps: Int = 60,
    private val durationSeconds: Float = 5f,
    private val bitrate: Int = 10_000_000,
    private val audioFile: AudioFile? = null,
    private val exportMode: Mode = Mode.Cyclone,
    private val userSkinFilePath: String? = null,
    private val beatResponseEnabled: Boolean = true,
    private val outputFile: File? = null,
    private val outputFd: FileDescriptor? = null,
    private val kaleidoFoldCount: Int = 12,
    private val kaleidoSquareRotationLocked: Boolean = false,
    private val kaleidoFrameShape: FrameShape = FrameShape.Circle,
    private val kaleidoFrameColorArgb: Long = 0xFFFFFFFFL,
    private val kaleidoShapeKind: ShapeKind = ShapeKind.Cylinder,
    private val invertColors: Boolean = false,
    private val colorizeEnabled: Boolean = false,
    private val colorizeHue: Float = 0f,
    private val distortionEnabled: Boolean = false,
    private val distortionAmplitude: Float = 0.3f,
    private val distortionFrequency: Float = 2.0f,
    private val partyEnabled: Boolean = false,
    private val randomEnabled: Boolean = false,
    private val partyIntensity: Float = 0.5f,
    private val bassZoomIntensity: Float = 0.5f,
    private val contrast: Float = 1.0f,
    private val contrastPasses: Int = 1,
    private val saturation: Float = 1.0f,
    private val distortionPlusEnabled: Boolean = false,
    private val distortionPlusYaw: Float = 0f,
    private val distortionPlusPitch: Float = 0f,
    private val distortionPlusRoll: Float = 0f,
    private val lockedParams: Set<LockableParam> = emptySet(),
    private val beatReactivity: Float = 0.25f,
) {
    companion object {
        private const val TAG = "Mp4Exporter"
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val AUDIO_AAC_BITRATE = 192_000
    }

    private lateinit var encoder: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var muxerStarted = false

    private var videoMuxTrackIndex = -1
    private var audioMuxTrackIndex = -1
    private var videoTrackReady = false
    private var audioTrackReady = false

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE

    private val renderer = AbstraktRenderer(context)
    private var audioExtractor: MediaExtractor? = null

    // Re-encode path — nullable; only allocated when audioPath != DIRECT_COPY
    private var audioDecoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var decoderEosReached = false
    private var encoderEosSignaled = false
    private var encoderEosReached = false

    // Production export — MediaStore fd-based. Rethrows CancellationException.
    suspend fun export(onProgress: (Float, String) -> Unit): Result<Unit> {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        return try {
            withContext(dispatcher) { runExport(onProgress) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            Log.d(TAG, "Export cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        } finally {
            dispatcher.close()
        }
    }

    private suspend fun runExport(onProgress: (Float, String) -> Unit) {
        check(outputFile != null || outputFd != null) { "Either outputFile or outputFd must be provided" }
        outputFile?.parentFile?.mkdirs()

        // ── Probe audio source ────────────────────────────────────────────────
        var effectiveDurationSec = durationSeconds
        var sourceAudioFormat: MediaFormat? = null
        var audioPath = AudioPath.DECODE_REENCODE_COMPRESSED
        val audioSourceUri: Uri? = audioFile?.uri

        if (audioSourceUri != null) {
            // Single extractor: select track first, then get format so CSD-0/CSD-1 are populated.
            val extractor = MediaExtractor()
            extractor.setDataSource(context, audioSourceUri, null)
            var audioTrackIdx = -1
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIdx = i
                    break
                }
            }
            if (audioTrackIdx < 0) {
                extractor.release()
                throw IllegalStateException("This audio file isn't readable on Android.")
            }
            extractor.selectTrack(audioTrackIdx)
            val fmt = extractor.getTrackFormat(audioTrackIdx) // includes CSD after selectTrack
            audioExtractor = extractor

            sourceAudioFormat = fmt
            audioPath = chooseAudioPath(fmt)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: "unknown"
            Log.d(TAG, "Audio source: $mime, path: $audioPath")

            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION))
                fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > 0) effectiveDurationSec = durationUs / 1_000_000f
            Log.d(TAG, "Audio duration: ${effectiveDurationSec}s, frames: ${(effectiveDurationSec * fps).toInt()}")
        }

        audioTrackReady = (audioFile == null)

        // ── Video encoder ─────────────────────────────────────────────────────
        val is4K = width >= 3840
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL,
                if (is4K) MediaCodecInfo.CodecProfileLevel.AVCLevel51
                else      MediaCodecInfo.CodecProfileLevel.AVCLevel41)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = encoder.createInputSurface()
        encoder.start()

        setupEgl(inputSurface)

        renderer.glMode = GlVizMode.CYCLONE
        renderer.onSurfaceCreated(null, null)
        renderer.onSurfaceChanged(null, width, height)

        val modeCfg = skinConfigForMode(exportMode)
        renderer.audioUniforms.activePainter = modeCfg.painter
        renderer.skinIndex      = modeCfg.skinIndex
        renderer.kaleidoFolds   = modeCfg.folds
        renderer.ribbonColor[0] = modeCfg.rr
        renderer.ribbonColor[1] = modeCfg.rg
        renderer.ribbonColor[2] = modeCfg.rb
        renderer.beatThreshold  = modeCfg.beatThreshold
        renderer.userSkinFilePath        = if (exportMode is Mode.UserSlot) userSkinFilePath else null
        renderer.foldCount               = kaleidoFoldCount
        renderer.squareRotationLocked    = kaleidoSquareRotationLocked
        renderer.frameShape              = kaleidoFrameShape
        renderer.frameColorArgb          = kaleidoFrameColorArgb
        renderer.setShapeKind(kaleidoShapeKind)
        renderer.invertColors    = invertColors
        renderer.colorizeEnabled      = colorizeEnabled
        renderer.colorizeHue          = colorizeHue
        renderer.distortionEnabled    = distortionEnabled
        renderer.distortionAmplitude  = distortionAmplitude
        renderer.distortionFrequency      = distortionFrequency
        renderer.distortionPlusEnabled    = distortionPlusEnabled
        renderer.distortionPlusYaw        = distortionPlusYaw
        renderer.distortionPlusPitch      = distortionPlusPitch
        renderer.distortionPlusRoll       = distortionPlusRoll
        renderer.bassZoomIntensity        = bassZoomIntensity
        renderer.beatReactivity           = beatReactivity
        renderer.contrast             = contrast
        renderer.contrastPasses       = contrastPasses
        renderer.saturation           = saturation

        val exportPartyEngine  = PartyEngine  { r -> applyRandomChangeToRenderer(renderer, lockedParams, r) }
        exportPartyEngine.enabled   = partyEnabled
        exportPartyEngine.intensity = partyIntensity
        val exportRandomEngine = RandomEngine { r -> applyRandomChangeToRenderer(renderer, lockedParams, r) }
        exportRandomEngine.enabled   = randomEnabled
        exportRandomEngine.intensity = partyIntensity

        muxer = if (outputFd != null) {
            MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        // ── Audio track registration ──────────────────────────────────────────
        val srcFmt = sourceAudioFormat
        if (srcFmt != null) {
            setupReencodeAudio(srcFmt, audioPath)
        }

        val totalFrames = (effectiveDurationSec * fps).toInt()
        val dt          = 1f / fps.toFloat()
        val silentSnap  = AudioSnapshot(FloatArray(8), 0f, false)

        onProgress(0f, "Analyzing audio…")
        val analyzedSnapshots: List<AudioSnapshot>? = audioFile?.let { af ->
            analyzeOffline(af, totalFrames)
        }
        Log.d(TAG, "Encode start: ${width}x${height} @${fps}fps $totalFrames frames snapshots=${analyzedSnapshots?.size ?: 0}")

        // ── Pre-warm: prime the painter FBO before the first encoded frame ────
        // For skin modes (Builtin/UserSlot): triggers stampFullSkinIntoPainterFbo
        // which fills the entire FBO instantly. UserSlot's texture may not be in
        // cache yet — this render loads it; frame 0 of the encode then succeeds.
        // For Cyclone: seeds the first stripe and sets lastStampedMode so the FBO
        // isn't wiped on encode frame 0. Full warmup follows below.
        renderer.currentMode = exportMode
        renderer.renderFrame(width, height, 0f, dt, silentSnap, exportMode)
        Log.d(TAG, "Pre-warm: painter primed for mode=$exportMode")

        // Cyclone uses a procedural stripe-painter: the cylinder needs ~30 simulated
        // seconds of renders to fully fill the 4096-wide painter FBO (one revolution).
        val needsProceduralWarmup = (exportMode == Mode.Cyclone)
        if (needsProceduralWarmup) {
            val warmupFrames = 30 * fps
            Log.d(TAG, "Running 30s procedural warmup for Cyclone ($warmupFrames frames)")
            for (i in 0 until warmupFrames) {
                currentCoroutineContext().ensureActive()
                // No eglSwapBuffers — painter FBO accumulates stripes; nothing reaches encoder.
                renderer.renderFrame(width, height, i * dt, dt, silentSnap, exportMode)
                if (i % fps == 0) onProgress(i.toFloat() / warmupFrames, "Preparing visualizer…")
            }
            // Reset ephemeral animation counters so the encode starts from t=0.
            // The painter FBO content is preserved — that's the whole point.
            renderer.resetAnimationState()
            Log.d(TAG, "Warmup complete; painter filled")
        }
        onProgress(0f, "Encoding video…")

        try {
            for (frameIndex in 0 until totalFrames) {
                currentCoroutineContext().ensureActive()

                val presentationTimeUs = frameIndex * 1_000_000L / fps
                val timeSec            = frameIndex * dt

                val snap = if (beatResponseEnabled) analyzedSnapshots?.getOrNull(frameIndex) ?: silentSnap
                           else silentSnap
                renderer.renderFrame(
                    surfaceWidth  = width,
                    surfaceHeight = height,
                    timeSec       = timeSec,
                    dt            = dt,
                    audioSnapshot = snap,
                    mode          = exportMode,
                )

                if (snap.isBeat) exportPartyEngine.onBeat()
                exportRandomEngine.tickWithVideoTime(presentationTimeUs / 1000L)

                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeUs * 1000L)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                drainEncoder(endOfStream = false)

                if (muxerStarted && audioExtractor != null) {
                    when (audioPath) {
                        AudioPath.DECODE_REENCODE_PCM        -> pumpAudioReencodePcm()
                        AudioPath.DECODE_REENCODE_COMPRESSED -> pumpAudioReencodeCompressed()
                    }
                }

                onProgress(frameIndex.toFloat() / totalFrames, "Encoding video…")
                if (frameIndex % 60 == 0) Log.d(TAG, "Frame $frameIndex / $totalFrames")
            }

            drainEncoder(endOfStream = true)
            if (audioExtractor != null) finishAudioDrain(audioPath)

            Log.d(TAG, "Encode complete: ${outputFile?.length() ?: "(fd)"}")
        } finally {
            audioExtractor?.release()
            audioExtractor = null
            runCatching { audioDecoder?.stop() }
            audioDecoder?.release()
            audioDecoder = null
            runCatching { audioEncoder?.stop() }
            audioEncoder?.release()
            audioEncoder = null
            renderer.release()
            releaseEgl()
            runCatching { encoder.stop() }
            encoder.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
            inputSurface.release()
        }
    }

    // ── Audio path selection ──────────────────────────────────────────────────

    private fun chooseAudioPath(format: MediaFormat): AudioPath {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return AudioPath.DECODE_REENCODE_COMPRESSED
        return when (mime) {
            "audio/raw", "audio/x-wav" -> AudioPath.DECODE_REENCODE_PCM
            else                       -> AudioPath.DECODE_REENCODE_COMPRESSED
        }
    }

    // ── Re-encode setup ───────────────────────────────────────────────────────

    private fun setupReencodeAudio(srcFmt: MediaFormat, path: AudioPath) {
        val sampleRate   = srcFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = srcFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val aacFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        audioEncoder = enc

        if (path == AudioPath.DECODE_REENCODE_COMPRESSED) {
            val mime = srcFmt.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            dec.configure(srcFmt, null, null, 0)
            dec.start()
            audioDecoder = dec
        }

        // Prime: poll encoder output until INFO_OUTPUT_FORMAT_CHANGED fires.
        // Most AAC encoders emit this immediately after start() without needing any input.
        Log.d(TAG, "Priming audio encoder for output format…")
        val primeDeadline = System.currentTimeMillis() + 5_000L
        val bufInfo = MediaCodec.BufferInfo()
        while (!audioTrackReady && System.currentTimeMillis() < primeDeadline) {
            val idx = enc.dequeueOutputBuffer(bufInfo, 100_000L)
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    audioMuxTrackIndex = muxer.addTrack(enc.outputFormat)
                    audioTrackReady = true
                    Log.d(TAG, "Audio track (reencode) added: index=$audioMuxTrackIndex")
                }
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Encoder needs input to emit its format on this device; nudge it.
                    when (path) {
                        AudioPath.DECODE_REENCODE_COMPRESSED -> { feedDecoderInput(); drainDecoderToEncoder() }
                        AudioPath.DECODE_REENCODE_PCM        -> feedEncoderInputFromExtractor()
                        else -> {}
                    }
                }
                idx >= 0 -> enc.releaseOutputBuffer(idx, false) // early output before muxer; discard
            }
        }
        if (!audioTrackReady) error("Audio encoder failed to emit output format within 5s")
    }

    // ── Per-frame pump functions ──────────────────────────────────────────────

    private fun pumpAudioReencodeCompressed() {
        feedDecoderInput()
        drainDecoderToEncoder()
        drainAudioEncoderToMuxer()
    }

    private fun pumpAudioReencodePcm() {
        feedEncoderInputFromExtractor()
        drainAudioEncoderToMuxer()
    }

    // ── Audio pipeline primitives ─────────────────────────────────────────────

    private fun feedDecoderInput() {
        if (decoderEosReached) return
        val ext = audioExtractor ?: return
        val dec = audioDecoder ?: return
        val idx = dec.dequeueInputBuffer(0L)
        if (idx < 0) return
        val buf = dec.getInputBuffer(idx) ?: return
        val size = ext.readSampleData(buf, 0)
        if (size < 0) {
            dec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            decoderEosReached = true
        } else {
            dec.queueInputBuffer(idx, 0, size, ext.sampleTime, ext.sampleFlags)
            ext.advance()
        }
    }

    private fun drainDecoderToEncoder() {
        if (encoderEosSignaled) return
        val dec = audioDecoder ?: return
        val enc = audioEncoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = dec.dequeueOutputBuffer(info, 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER ||
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> break
                outIdx >= 0 -> {
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    val pcm = dec.getOutputBuffer(outIdx)
                    if (pcm != null && info.size > 0) {
                        val encIdx = enc.dequeueInputBuffer(10_000L)
                        if (encIdx >= 0) {
                            val encBuf = enc.getInputBuffer(encIdx)!!
                            encBuf.clear()
                            pcm.position(info.offset)
                            pcm.limit(info.offset + info.size)
                            encBuf.put(pcm)
                            val flags = if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            enc.queueInputBuffer(encIdx, 0, info.size, info.presentationTimeUs, flags)
                            if (eos) encoderEosSignaled = true
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false)
                    if (eos) {
                        if (!encoderEosSignaled) {
                            val encIdx = enc.dequeueInputBuffer(10_000L)
                            if (encIdx >= 0) {
                                enc.queueInputBuffer(encIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                encoderEosSignaled = true
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    private fun feedEncoderInputFromExtractor() {
        if (encoderEosSignaled) return
        val ext = audioExtractor ?: return
        val enc = audioEncoder ?: return
        val idx = enc.dequeueInputBuffer(0L)
        if (idx < 0) return
        val buf = enc.getInputBuffer(idx)!!
        buf.clear()
        val size = ext.readSampleData(buf, 0)
        if (size < 0) {
            enc.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            encoderEosSignaled = true
        } else {
            enc.queueInputBuffer(idx, 0, size, ext.sampleTime, 0)
            ext.advance()
        }
    }

    private fun drainAudioEncoderToMuxer() {
        if (encoderEosReached) return
        val enc = audioEncoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = enc.dequeueOutputBuffer(info, 0L)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!audioTrackReady) {
                        audioMuxTrackIndex = muxer.addTrack(enc.outputFormat)
                        audioTrackReady = true
                        maybeStartMuxer()
                    }
                }
                outIdx >= 0 -> {
                    val buf = enc.getOutputBuffer(outIdx)
                    if (buf != null) {
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(audioMuxTrackIndex, buf, info)
                        }
                    }
                    enc.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderEosReached = true
                        break
                    }
                }
            }
        }
    }

    // ── End-of-stream audio drain ─────────────────────────────────────────────

    private fun finishAudioDrain(path: AudioPath) {
        val deadline = System.currentTimeMillis() + 10_000L
        when (path) {
            AudioPath.DECODE_REENCODE_PCM -> {
                while (!encoderEosReached && System.currentTimeMillis() < deadline) {
                    if (!encoderEosSignaled) feedEncoderInputFromExtractor()
                    drainAudioEncoderToMuxer()
                }
            }
            AudioPath.DECODE_REENCODE_COMPRESSED -> {
                while (!encoderEosReached && System.currentTimeMillis() < deadline) {
                    if (!decoderEosReached) feedDecoderInput()
                    if (!encoderEosSignaled) drainDecoderToEncoder()
                    drainAudioEncoderToMuxer()
                }
            }
        }
        if (!encoderEosReached) {
            Log.w(TAG, "Audio drain timed out — some trailing audio may be missing")
        }
    }

    // ── Muxer start gate ──────────────────────────────────────────────────────

    private fun maybeStartMuxer() {
        if (videoTrackReady && audioTrackReady && !muxerStarted) {
            muxer.start()
            muxerStarted = true
            Log.d(TAG, "Muxer started: video=$videoMuxTrackIndex audio=$audioMuxTrackIndex")
        }
    }

    // ── Audio format detection ────────────────────────────────────────────────

    private fun detectAudioFormat(uri: Uri): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    // selectTrack then re-fetch so CSD-0/CSD-1 are populated in the format.
                    extractor.selectTrack(i)
                    return extractor.getTrackFormat(i)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "detectAudioFormat failed: ${e.message}")
            null
        } finally {
            extractor.release()
        }
    }

    // ── EGL ───────────────────────────────────────────────────────────────────

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

    // ── Video encoder drain ───────────────────────────────────────────────────

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
