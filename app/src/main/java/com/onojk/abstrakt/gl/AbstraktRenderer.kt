package com.onojk.abstrakt.gl

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.onojk.abstrakt.FrameShape
import com.onojk.abstrakt.Mode
import com.onojk.abstrakt.PaletteMode
import com.onojk.abstrakt.ShapeKind
import com.onojk.abstrakt.color.ColorHarmony
import com.onojk.abstrakt.color.wrapHue
import com.onojk.abstrakt.audio.AudioFile
import com.onojk.abstrakt.audio.AudioSnapshot
import com.onojk.abstrakt.skin.CubeLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AbstraktGL"

private val QUAD_VERTS = floatArrayOf(
    -1f, -1f,
     1f, -1f,
    -1f,  1f,
     1f,  1f,
)

private const val WARP_GRID_DIM      = 70f
private const val WARP_DOT_RADIUS    =  6f
private const val PAINTER_TEX_W      = 4096
private const val PAINTER_TEX_H      = 256
private const val PAINTER_STRIPE_W   = 16
// Noise-floor gate for beatDecay: snap.peak below this is ambient noise (mic USB/AC hum peaks
// at 0.003–0.012); real audio consistently above 0.030. Prevents shake in silent rooms.
private const val RENDERER_BEAT_PEAK_FLOOR = 0.020f

internal class AbstraktRenderer(private val context: Context) : GLSurfaceView.Renderer {

    val audioUniforms = AudioUniforms()
    private val influencers = Influencers(audioUniforms)
    private val driftState  = DriftState()

    @Volatile var glMode: GlVizMode = GlVizMode.TEST

    fun setAudioFile(file: AudioFile?)  { audioUniforms.audioFile        = file }
    fun setPlaybackFraction(f: Float)   { audioUniforms.playbackFraction = f    }

    private var testProgram:    ShaderProgram? = null
    private var warpProgram:    ShaderProgram? = null
    private var driftProgram:   ShaderProgram? = null
    private var cycloneProgram: ShaderProgram? = null
    private var kaleidoProgram: ShaderProgram? = null
    private val painterPrograms: MutableMap<Painter, ShaderProgram> = mutableMapOf()
    private var vaoId              = 0
    private var vboId              = 0
    @Volatile var currentShape: Shape = CylinderShape()
    private var shapeVao         = 0
    private var shapePositionVbo = 0
    private var shapeUvVbo       = 0
    private var shapeIbo         = 0
    private var shapeIndexCount  = 0
    private var shapeMeshLoaded  = false
    private var loadedShapeName: String? = null
    private var shapeAngleRad    = 0f
    private var kaleidoRotationRad = 0f
    private var painterFBO      = 0
    private var painterTexture  = 0
    private var imageTexture    = 0
    private var imageWidth      = 0
    private var imageHeight     = 0
    private var ribbonProgram:  ShaderProgram? = null
    private var blitProgram:    ShaderProgram? = null
    private var ribbonFboA    = 0; private var ribbonTexA = 0
    private var ribbonFboB    = 0; private var ribbonTexB = 0
    private var ribbonReadIsA   = true
    private val collapseState   = FloatArray(4) { 0f }
    private val collapsePhase   = FloatArray(4) { 0f }
    private val collapseTimer   = FloatArray(4) { 0f }
    private var beatDecay       = 0f
    // EMA-smoothed dt used ONLY for rotation accumulation — keeps rotation smooth
    // despite OS compositor jitter (observed spans up to 13ms at 120Hz).
    // Real dt is still used for audio-reactive decay (beatDecay, smoothedBass, collapse).
    private var rotDt = 1f / 120f
    // Per-mode skin textures (indices 0-4 = skin1..skin5).
    private val skinTextures    = IntArray(5)
    // Per-mode parameters — written from UI thread, read on GL thread.
    @Volatile var kaleidoFolds          = 12f
    @Volatile var foldCount             = 12
    @Volatile var squareRotationLocked  = false
    @Volatile var frameShape: FrameShape = FrameShape.Circle
    @Volatile var frameColorArgb: Long   = 0xFFFFFFFFL
    private var frameOverlayProgram: ShaderProgram? = null
    private var kaleidoFBO  = 0
    private var kaleidoTex  = 0
    @Volatile internal var kaleidoTexW = 0
    @Volatile internal var kaleidoTexH = 0
    internal val captureController = CaptureMosaicController()
    private var distortionPlusFbo  = 0
    private var distortionPlusTex  = 0
    private var distortionPlusFboW = 0
    private var distortionPlusFboH = 0
    private var distortionPlusProgram: ShaderProgram? = null
    private var chromaAberrationFbo  = 0
    private var chromaAberrationTex  = 0
    private var chromaAberrationFboW = 0
    private var chromaAberrationFboH = 0
    private var chromaAberrationProgram: ShaderProgram? = null
    private var shapeFbo         = 0
    private var shapeColorTex    = 0
    private var shapeDepthBuffer = 0
    private var shapeFboW        = 0
    private var shapeFboH        = 0
    @Volatile var zoomMultiplier      = 1.0f
    @Volatile var beatReactivity      = 0.25f  // 0=silent, 1=full; master beat-magnitude scale
    @Volatile var bassZoomIntensity   = 0.5f   // 0=off, 1=max bass-driven zoom pulse
    private var smoothedBass          = 0.0f   // EMA of snap.bands[0]; GL thread only
    @Volatile var contrast            = 1.0f   // 0..2, 1=passthrough
    @Volatile var contrastPasses      = 1      // 1..6, >1=posterization
    @Volatile var saturation          = 1.0f   // 0..2, 1=passthrough
    @Volatile var invertColors        = false
    @Volatile var colorizeEnabled      = false
    @Volatile var colorizeHue          = 0f        // degrees 0..360
    @Volatile var distortionEnabled    = false
    @Volatile var distortionAmplitude  = 0.3f      // 0..1
    @Volatile var distortionFrequency  = 2.0f      // 0.5..8.0
    @Volatile var suddenWarpEnabled    = false
    @Volatile var warpAmplitude        = 0.18f
    private var warpStartSec           = -1f
    private var warpDurationSec        = 0f
    private var warpSeed               = 0f
    private val warpRandom             = java.util.Random()
    @Volatile var distortionPlusEnabled = false
    @Volatile var distortionPlusYaw     = 0f       // -180..180 degrees
    @Volatile var distortionPlusPitch   = 0f       // -90..90 degrees
    @Volatile var distortionPlusRoll    = 0f       // -180..180 degrees
    @Volatile var chromaAberrationEnabled  = false
    @Volatile var chromaAberrationIntensity = 0.008f  // 0..0.02 UV offset per channel
    @Volatile var chromaAberrationAudioReact = false
    private var blackholeFboA = 0; private var blackholeTexA = 0
    private var blackholeFboB = 0; private var blackholeTexB = 0
    private var blackholeFboW     = 0
    private var blackholeFboH     = 0
    private var blackholeReadIsA  = true
    private var blackholeWasEnabled = false
    private var blackholeProgram: ShaderProgram? = null
    @Volatile var blackholeEnabled     = false
    @Volatile var blackholeStrength    = 0.5f   // feedback blend weight 0..0.98
    @Volatile var blackholeShrinkRate  = 0.97f  // per-frame UV scale 0.90..0.999
    @Volatile var blackholeAlphaRadius = 0.5f   // edge-blend start 0.1..0.9
    @Volatile var blackholeWanderAmount = 0.005f // vanishing-point drift 0..0.02
    private var lightningProgram: ShaderProgram? = null
    private var lightningCompositeProgram: ShaderProgram? = null
    @Volatile var lightningEnabled          = false
    @Volatile var lightningSpritesLimit5s   = true
    @Volatile var lightningTouching    = false
    @Volatile var lightningTouchX      = 0f
    @Volatile var lightningTouchY      = 0f
    private var lightningFbo           = 0
    private var lightningTex           = 0
    private var lightningFboW          = 0
    private var lightningFboH          = 0
    private var boltLowFbo             = 0
    private var boltLowTex             = 0
    private var boltLowFboW            = 0
    private var boltLowFboH            = 0
    private val lightningSystem = LightningSystem()
    @Volatile var paletteMode    = PaletteMode.Off
    @Volatile var paletteTint    = 1.0f
    @Volatile var paletteMonoHue = 200.0f
    @Volatile var harmonyType: ColorHarmony = ColorHarmony.Triadic
    @Volatile var harmonyAnchorHue: Float   = 0f
    @Volatile var harmonySaturation: Float  = 0.8f
    @Volatile var harmonyValue: Float       = 0.8f
    @Volatile var harmonyStrength: Float    = 0.6f
    @Volatile var lutEnabled  = false
    @Volatile var lutStrength = 1.0f
    private var currentLutData: CubeLut? = null
    private var lutTextureId  = 0
    private var lutDiagPending = false
    @Volatile var beatThreshold  = 0.4f
    @Volatile var skinIndex      = 0
    val ribbonColor              = FloatArray(3) { 0f }   // rgb; default black
    @Volatile var userSkinFilePath: String? = null
    private val userSkinTextureCache = mutableMapOf<String, Int>()
    // FBO fields kept for createFBO/destroyFBO helpers — not used by Drift's polar branch.
    private var fboId        = 0
    private var fboTexId     = 0
    private var surfaceWidth  = 1
    private var surfaceHeight = 1
    private var startTimeNs   = 0L
    private var lastFrameNs   = 0L
    private var frameCount      = 0
    private var lastFrameLogMs  = 0L
    private var lastTimeLogMs   = 0L
    @Volatile var dumpPainterStatsOnNextFrame = false
    // SHAKEDIAG — dt jitter tracking over 5-second windows
    private var dtMin = Float.MAX_VALUE
    private var dtMax = 0f
    private var dtLogMs = 0L

    // BPM rotation lock: null = use shape's natural speed; non-null = EMA target in rad/s.
    @Volatile var rotationSpeedTarget: Float? = null
    private var smoothedRotSpeed    = 0f
    private var rotSpeedInitialized = false

    private val _currentShapeName = MutableStateFlow("Cylinder")
    val currentShapeName: StateFlow<String> = _currentShapeName.asStateFlow()

    fun setShapeKind(kind: ShapeKind) {
        currentShape = shapeFor(kind)
        _currentShapeName.value = currentShape.name
        Log.d(TAG, "setShapeKind → ${currentShape.name}")
    }

    fun cyclePainter() {
        val all     = Painter.entries
        val current = audioUniforms.activePainter
        audioUniforms.activePainter = all[(current.ordinal + 1) % all.size]
    }
    // Mode-change stamp: track which mode was last fully stamped into painterFBO.
    @Volatile var currentMode: Mode = Mode.Cyclone
    private var lastStampedMode: Mode? = null

    // Callback fired on the main thread once all critical GL resources are ready.
    var onReadyCallback: (() -> Unit)? = null
    private var allResourcesReady = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val scStart = SystemClock.elapsedRealtime()
        Log.d(TAG, "onSurfaceCreated — building GL resources")
        testProgram        = null
        warpProgram        = null
        driftProgram       = null
        cycloneProgram     = null
        kaleidoProgram     = null
        painterPrograms.clear()
        vaoId              = 0
        vboId              = 0
        shapeVao           = 0
        shapePositionVbo   = 0
        shapeUvVbo         = 0
        shapeIbo           = 0
        shapeIndexCount    = 0
        shapeMeshLoaded    = false
        loadedShapeName    = null
        shapeAngleRad      = 0f
        kaleidoRotationRad = 0f
        painterFBO      = 0
        painterTexture  = 0
        imageTexture    = 0
        imageWidth      = 0
        imageHeight     = 0
        ribbonProgram        = null
        blitProgram          = null
        frameOverlayProgram  = null
        ribbonFboA = 0; ribbonTexA = 0
        ribbonFboB = 0; ribbonTexB = 0
        kaleidoFBO = 0; kaleidoTex = 0; kaleidoTexW = 0; kaleidoTexH = 0
        shapeFbo = 0; shapeColorTex = 0; shapeDepthBuffer = 0; shapeFboW = 0; shapeFboH = 0
        distortionPlusFbo = 0; distortionPlusTex = 0; distortionPlusFboW = 0; distortionPlusFboH = 0
        distortionPlusProgram = null
        chromaAberrationFbo = 0; chromaAberrationTex = 0; chromaAberrationFboW = 0; chromaAberrationFboH = 0
        chromaAberrationProgram = null
        blackholeFboA = 0; blackholeTexA = 0; blackholeFboB = 0; blackholeTexB = 0
        blackholeFboW = 0; blackholeFboH = 0; blackholeReadIsA = true; blackholeWasEnabled = false
        blackholeProgram = null
        lightningProgram = null; lightningCompositeProgram = null
        lightningFbo = 0; lightningTex = 0; lightningFboW = 0; lightningFboH = 0
        boltLowFbo = 0; boltLowTex = 0; boltLowFboW = 0; boltLowFboH = 0
        lightningSystem.reset()
        ribbonReadIsA    = true
        collapseState.fill(0f)
        collapsePhase.fill(0f)
        collapseTimer.fill(0f)
        skinTextures.fill(0)
        userSkinTextureCache.clear()
        fboId           = 0
        fboTexId        = 0
        startTimeNs     = 0L
        lastFrameNs     = 0L
        frameCount      = 0
        lastFrameLogMs  = 0L
        dtMin = Float.MAX_VALUE; dtMax = 0f; dtLogMs = 0L
        rotDt = 1f / 120f
        lastTimeLogMs   = 0L
        lastStampedMode   = null   // force re-stamp after context loss
        allResourcesReady = false
        audioUniforms.uniformsLogged = false
        lutTextureId = 0  // reset on context loss; re-uploaded when needed

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        testProgram    = ShaderProgram(Shaders.TEST_VERT,    Shaders.TEST_FRAG)
        warpProgram    = ShaderProgram(Shaders.WARP_VERT,    Shaders.WARP_FRAG)
        driftProgram   = ShaderProgram(Shaders.TEST_VERT,    Shaders.DRIFT_POLAR_FRAG)
        cycloneProgram = ShaderProgram(Shaders.CYCLONE_VERT, Shaders.CYCLONE_FRAG)
        kaleidoProgram = ShaderProgram(Shaders.TEST_VERT,    Shaders.CYCLONE_KALEIDO_FRAG)
        allPainters().forEach { entry ->
            painterPrograms[entry.painter] = ShaderProgram(entry.vert, entry.frag)
        }

        ribbonProgram          = ShaderProgram(Shaders.TEST_VERT, Shaders.RIBBON_FRAG)
        blitProgram            = ShaderProgram(Shaders.TEST_VERT, Shaders.BLIT_FRAG)
        frameOverlayProgram    = ShaderProgram(Shaders.TEST_VERT, Shaders.FRAME_OVERLAY_FRAG)
        distortionPlusProgram  = ShaderProgram(Shaders.TEST_VERT, Shaders.DISTORTION_PLUS_FRAG)
        chromaAberrationProgram = ShaderProgram(Shaders.TEST_VERT, Shaders.CHROMA_ABERRATION_FRAG)
        blackholeProgram        = ShaderProgram(Shaders.TEST_VERT, Shaders.BLACKHOLE_FRAG)
        lightningProgram          = ShaderProgram(Shaders.TEST_VERT, Shaders.LIGHTNING_BOLT_FRAG)
        lightningCompositeProgram = ShaderProgram(Shaders.TEST_VERT, Shaders.LIGHTNING_COMPOSITE_FRAG)

        // ── Fullscreen quad VAO/VBO (shared by 2D modes) ─────────────────────
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        vaoId = vaos[0]
        GLES30.glBindVertexArray(vaoId)

        val vbos = IntArray(1)
        GLES30.glGenBuffers(1, vbos, 0)
        vboId = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)

        val buf = ByteBuffer
            .allocateDirect(QUAD_VERTS.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(QUAD_VERTS)
        buf.position(0)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            QUAD_VERTS.size * Float.SIZE_BYTES,
            buf,
            GLES30.GL_STATIC_DRAW,
        )

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        // ── Shape mesh upload (Cyclone mode) ─────────────────────────────────
        loadShapeMeshIfNeeded()

        // ── Painter texture + FBO (Cyclone rolling paint) ────────────────────
        val pTexIds = IntArray(1)
        GLES30.glGenTextures(1, pTexIds, 0)
        painterTexture = pTexIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, painterTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            PAINTER_TEX_W, PAINTER_TEX_H, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // GL_REPEAT in S lets the cylinder UV wrap cleanly at the seam.
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val pFboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, pFboIds, 0)
        painterFBO = pFboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, painterTexture, 0,
        )
        val painterStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        Log.d(TAG, "painterFBO ${PAINTER_TEX_W}x${PAINTER_TEX_H}: ${
            if (painterStatus == GLES30.GL_FRAMEBUFFER_COMPLETE) "COMPLETE"
            else "ERROR 0x${painterStatus.toString(16)}"
        }")
        // One-time clear to black — thereafter only scissored stripe writes touch it.
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // ── Image texture (Cyclone IMAGE painter) ───────────────────────────
        try {
            val opts = BitmapFactory.Options().also { it.inScaled = false }
            val bmp  = context.assets.open("cyclone_image.jpg")
                .use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp != null) {
                val iTexIds = IntArray(1)
                GLES30.glGenTextures(1, iTexIds, 0)
                imageTexture = iTexIds[0]
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexture)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,     GLES30.GL_REPEAT)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,     GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                imageWidth  = bmp.width
                imageHeight = bmp.height
                bmp.recycle()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                Log.d(TAG, "imageTexture loaded: ${imageWidth}x${imageHeight} id=$imageTexture")
            } else {
                Log.w(TAG, "cyclone_image.jpg decode returned null")
            }
        } catch (e: Exception) {
            Log.w(TAG, "cyclone_image.jpg load failed: ${e.message}")
        }

        // Skin textures (skin1..skin5) are deferred — loaded on first access via
        // loadBuiltinSkinTexture() to avoid blocking the first rendered frame.

        Log.d(TAG, "onSurfaceCreated complete in ${SystemClock.elapsedRealtime() - scStart}ms — " +
            "quad vao=$vaoId shape vao=$shapeVao shape=${currentShape.name} " +
            "cyclone=${cycloneProgram?.id} painters=${painterPrograms.mapValues { it.value.id }}")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        val swStart = SystemClock.elapsedRealtime()
        surfaceWidth  = w
        surfaceHeight = h
        GLES30.glViewport(0, 0, w, h)
        if (w > 0 && h > 0) createRibbonFBOs(PAINTER_TEX_W, PAINTER_TEX_H)
        Log.d(TAG, "onSurfaceChanged complete in ${SystemClock.elapsedRealtime() - swStart}ms — ${w}x${h}")
        if (!allResourcesReady) {
            allResourcesReady = true
            Handler(Looper.getMainLooper()).post { onReadyCallback?.invoke() }
        }
    }

    // ── FBO helpers — available for future two-pass visualizers. ──────────────
    // Call createFBO from onSurfaceChanged and destroyFBO from onSurfaceCreated
    // (context loss) when a mode that needs them is active.

    private fun destroyFBO() {
        if (fboTexId != 0) { GLES30.glDeleteTextures(1, intArrayOf(fboTexId), 0); fboTexId = 0 }
        if (fboId    != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0); fboId = 0 }
    }

    @Suppress("unused")
    private fun createFBO(w: Int, h: Int) {
        destroyFBO()

        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        fboTexId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, fboTexId, 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        Log.d(TAG, "FBO ${w}x${h}: ${
            if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) "COMPLETE"
            else "ERROR 0x${status.toString(16)}"
        }")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun createKaleidoFBO(w: Int, h: Int) {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        kaleidoTex = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, kaleidoTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        kaleidoFBO = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, kaleidoFBO)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, kaleidoTex, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        kaleidoTexW = w
        kaleidoTexH = h
        Log.d(TAG, "kaleidoFBO: ${w}x${h} fbo=$kaleidoFBO tex=$kaleidoTex")
    }

    private fun destroyKaleidoFBO() {
        if (kaleidoTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(kaleidoTex), 0); kaleidoTex = 0 }
        if (kaleidoFBO != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(kaleidoFBO), 0); kaleidoFBO = 0 }
        kaleidoTexW = 0; kaleidoTexH = 0
    }

    private fun createDistortionPlusFBO(w: Int, h: Int) {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        distortionPlusTex = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, distortionPlusTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // GL_REPEAT on S: yaw wraps the horizon seamlessly.
        // GL_CLAMP_TO_EDGE on T: no wrap past the poles.
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        distortionPlusFbo = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, distortionPlusFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, distortionPlusTex, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        distortionPlusFboW = w
        distortionPlusFboH = h
        Log.d(TAG, "distortionPlusFBO: ${w}x${h} fbo=$distortionPlusFbo tex=$distortionPlusTex")
    }

    private fun destroyDistortionPlusFBO() {
        if (distortionPlusTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(distortionPlusTex), 0); distortionPlusTex = 0 }
        if (distortionPlusFbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(distortionPlusFbo), 0); distortionPlusFbo = 0 }
        distortionPlusFboW = 0; distortionPlusFboH = 0
    }

    private fun createChromaAberrationFBO(w: Int, h: Int) {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        chromaAberrationTex = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, chromaAberrationTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        chromaAberrationFbo = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, chromaAberrationFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, chromaAberrationTex, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        chromaAberrationFboW = w
        chromaAberrationFboH = h
        Log.d(TAG, "chromaAberrationFBO: ${w}x${h} fbo=$chromaAberrationFbo tex=$chromaAberrationTex")
    }

    private fun destroyChromaAberrationFBO() {
        if (chromaAberrationTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(chromaAberrationTex), 0); chromaAberrationTex = 0 }
        if (chromaAberrationFbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(chromaAberrationFbo), 0); chromaAberrationFbo = 0 }
        chromaAberrationFboW = 0; chromaAberrationFboH = 0
    }

    private fun createBlackholeFeedbackFBOs(w: Int, h: Int) {
        val texIds = IntArray(2)
        GLES30.glGenTextures(2, texIds, 0)
        blackholeTexA = texIds[0]; blackholeTexB = texIds[1]
        for (tex in texIds) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(2)
        GLES30.glGenFramebuffers(2, fboIds, 0)
        blackholeFboA = fboIds[0]; blackholeFboB = fboIds[1]
        for ((fbo, tex) in listOf(Pair(blackholeFboA, blackholeTexA), Pair(blackholeFboB, blackholeTexB))) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, tex, 0)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        blackholeFboW = w; blackholeFboH = h
        Log.d(TAG, "blackholeFeedbackFBOs: ${w}x${h} A=$blackholeFboA B=$blackholeFboB")
    }

    private fun destroyBlackholeFeedbackFBOs() {
        if (blackholeTexA != 0) { GLES30.glDeleteTextures(1, intArrayOf(blackholeTexA), 0); blackholeTexA = 0 }
        if (blackholeTexB != 0) { GLES30.glDeleteTextures(1, intArrayOf(blackholeTexB), 0); blackholeTexB = 0 }
        if (blackholeFboA != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(blackholeFboA), 0); blackholeFboA = 0 }
        if (blackholeFboB != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(blackholeFboB), 0); blackholeFboB = 0 }
        blackholeFboW = 0; blackholeFboH = 0
    }

    private fun createLightningFBO(w: Int, h: Int) {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        lightningTex = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lightningTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        lightningFbo = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, lightningFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, lightningTex, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        lightningFboW = w; lightningFboH = h
        Log.d(TAG, "lightningFBO: ${w}x${h} fbo=$lightningFbo tex=$lightningTex")
    }

    private fun destroyLightningFBO() {
        if (lightningTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(lightningTex), 0); lightningTex = 0 }
        if (lightningFbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(lightningFbo), 0); lightningFbo = 0 }
        lightningFboW = 0; lightningFboH = 0
    }

    private fun createBoltLowFBO(w: Int, h: Int) {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        boltLowTex = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, boltLowTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        boltLowFbo = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, boltLowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, boltLowTex, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        boltLowFboW = w; boltLowFboH = h
        Log.d(TAG, "boltLowFBO: ${w}x${h} fbo=$boltLowFbo tex=$boltLowTex")
    }

    private fun destroyBoltLowFBO() {
        if (boltLowTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(boltLowTex), 0); boltLowTex = 0 }
        if (boltLowFbo != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(boltLowFbo), 0); boltLowFbo = 0 }
        boltLowFboW = 0; boltLowFboH = 0
    }

    private fun createShapeFBO(w: Int, h: Int) {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        shapeColorTex = tex[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shapeColorTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val rb = IntArray(1)
        GLES30.glGenRenderbuffers(1, rb, 0)
        shapeDepthBuffer = rb[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, shapeDepthBuffer)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, w, h)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        shapeFbo = fbo[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shapeFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, shapeColorTex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER, shapeDepthBuffer)

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE)
            Log.e(TAG, "shapeFbo incomplete: 0x${status.toString(16)}")
        else
            Log.d(TAG, "shapeFbo created: ${w}x${h} fbo=$shapeFbo tex=$shapeColorTex")

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        shapeFboW = w; shapeFboH = h
    }

    private fun destroyShapeFBO() {
        if (shapeColorTex    != 0) { GLES30.glDeleteTextures(1,      intArrayOf(shapeColorTex),    0); shapeColorTex    = 0 }
        if (shapeDepthBuffer != 0) { GLES30.glDeleteRenderbuffers(1, intArrayOf(shapeDepthBuffer), 0); shapeDepthBuffer = 0 }
        if (shapeFbo         != 0) { GLES30.glDeleteFramebuffers(1,  intArrayOf(shapeFbo),         0); shapeFbo         = 0 }
        shapeFboW = 0; shapeFboH = 0
    }

    private fun destroyRibbonFBOs() {
        if (ribbonTexA != 0) { GLES30.glDeleteTextures(1, intArrayOf(ribbonTexA), 0); ribbonTexA = 0 }
        if (ribbonTexB != 0) { GLES30.glDeleteTextures(1, intArrayOf(ribbonTexB), 0); ribbonTexB = 0 }
        if (ribbonFboA != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(ribbonFboA), 0); ribbonFboA = 0 }
        if (ribbonFboB != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(ribbonFboB), 0); ribbonFboB = 0 }
    }

    private fun createRibbonFBOs(w: Int, h: Int) {
        destroyRibbonFBOs()
        val texIds = IntArray(2)
        GLES30.glGenTextures(2, texIds, 0)
        ribbonTexA = texIds[0]; ribbonTexB = texIds[1]
        for (tex in texIds) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            // GL_RGBA16F: half-float per channel prevents 8-bit quantization from
            // freezing low-alpha trail values (round(n*0.992)=n for n<=63 in RGBA8).
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        val fboIds = IntArray(2)
        GLES30.glGenFramebuffers(2, fboIds, 0)
        ribbonFboA = fboIds[0]; ribbonFboB = fboIds[1]
        for ((fbo, tex) in listOf(Pair(ribbonFboA, ribbonTexA), Pair(ribbonFboB, ribbonTexB))) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, tex, 0)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        ribbonReadIsA = true
        Log.d(TAG, "ribbonFBOs created: ${w}x${h} A=$ribbonFboA B=$ribbonFboB")
    }

    // ── Screen draw callback — thin wrapper; CYCLONE delegates to renderFrame. ──

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        if (startTimeNs == 0L) { startTimeNs = nowNs; lastFrameNs = nowNs }

        // Keep u_time in [0, 600) to prevent Float precision loss in shader trig.
        // At 600s Float still has ~36µs resolution — far finer than one frame interval.
        // Compute in Double first so the intermediate result isn't truncated to Float range.
        val elapsedSec = (nowNs - startTimeNs).toDouble() / 1_000_000_000.0
        val timeSec    = elapsedSec.rem(600.0).toFloat()
        val dt         = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceAtMost(0.1f)
        lastFrameNs    = nowNs

        frameCount++
        val fpsNow = System.currentTimeMillis()
        if (lastFrameLogMs == 0L) lastFrameLogMs = fpsNow
        if (fpsNow - lastFrameLogMs >= 5000) {
            Log.d(TAG, "FPS over last 5s: ${"%.1f".format(frameCount * 1000.0 / (fpsNow - lastFrameLogMs))}")
            frameCount = 0
            lastFrameLogMs = fpsNow
        }
        if (fpsNow - lastTimeLogMs >= 60_000L || lastTimeLogMs == 0L) {
            Log.d(TAG, "u_time: raw=${"%.1f".format(elapsedSec)}s  wrapped=${"%.3f".format(timeSec)}s")
            lastTimeLogMs = fpsNow
        }
        // SHAKEDIAG — dt jitter (frame-time instability → accumulated-angle jitter)
        if (dtLogMs == 0L) dtLogMs = fpsNow
        if (dtMin == Float.MAX_VALUE) { dtMin = dt; dtMax = dt }
        else { if (dt < dtMin) dtMin = dt; if (dt > dtMax) dtMax = dt }
        if (fpsNow - dtLogMs >= 5000) {
            val liveAudio = audioUniforms.liveSnapshot != null
            val forceOn   = audioUniforms.debugForceSilentSnapshot
            Log.d("SHAKEDIAG", "dt 5s: min=${"%.1f".format(dtMin*1000)}ms " +
                "max=${"%.1f".format(dtMax*1000)}ms " +
                "span=${"%.1f".format((dtMax-dtMin)*1000)}ms " +
                "liveAudio=$liveAudio forceSilent=$forceOn")
            dtMin = Float.MAX_VALUE; dtMax = 0f; dtLogMs = fpsNow
        }
        if (dumpPainterStatsOnNextFrame) {
            dumpPainterStatsOnNextFrame = false
            debugPainterStats()
        }

        val wW = surfaceWidth.toFloat()
        val wH = surfaceHeight.toFloat()

        when (glMode) {
            GlVizMode.TEST -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = testProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                audioUniforms.applyToProgram(prog, timeSec)
                drawQuad()
            }

            GlVizMode.WARP -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = warpProgram ?: return
                prog.use()
                prog.setVec2("u_resolution", wW, wH)
                prog.setFloat("u_grid_dim",   WARP_GRID_DIM)
                prog.setFloat("u_dot_radius", WARP_DOT_RADIUS)
                audioUniforms.applyToProgram(prog, timeSec)
                influencers.updateForFrame(timeSec, dt, wW, wH)
                influencers.applyToProgram(prog)
                drawQuad()
            }

            GlVizMode.DRIFT -> {
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                val prog = driftProgram ?: return
                val snap = audioUniforms.getSnapshot()
                val hasRealSignalDrift = snap.peak > RENDERER_BEAT_PEAK_FLOOR
                beatDecay = if (snap.isBeat && hasRealSignalDrift) 1.0f
                            else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                                .coerceAtLeast(0f)
                val scaledBeatDecayDrift = beatDecay * beatReactivity
                driftState.update(timeSec, snap.bands)
                prog.use()
                audioUniforms.applyToProgram(prog, timeSec)
                driftState.applyToProgram(prog)
                prog.setFloat("u_beat_decay", scaledBeatDecayDrift)
                drawQuad()
            }

            GlVizMode.CYCLONE -> {
                renderFrame(
                    surfaceWidth  = surfaceWidth,
                    surfaceHeight = surfaceHeight,
                    timeSec       = timeSec,
                    dt            = dt,
                    audioSnapshot = audioUniforms.getSnapshot(),
                    mode          = currentMode,
                )
            }
        }
    }

    // ── Pure render function — called by onDrawFrame (screen) and Mp4Exporter. ─
    // Assumes a GL context is current. Does NOT call eglSwapBuffers.
    // dt: elapsed seconds since previous frame (use 1f/fps for encoder, real dt for screen).

    fun renderFrame(
        surfaceWidth: Int,
        surfaceHeight: Int,
        timeSec: Float,
        dt: Float,
        audioSnapshot: AudioSnapshot?,
        mode: Mode,
        outputFbo: Int = 0,
        absoluteTimeSec: Float = Float.NaN,
    ) {
        val wW   = surfaceWidth.toFloat()
        val wH   = surfaceHeight.toFloat()
        val snap = audioSnapshot ?: AudioSnapshot(FloatArray(8), 0f, false)

        // ── Mode-change: stamp full skin or clear FBO on mode switch ──────────
        if (mode != lastStampedMode) {
            if (mode is Mode.Builtin || mode is Mode.UserSlot) {
                if (stampFullSkinIntoPainterFbo(mode)) lastStampedMode = mode
            } else if (mode == Mode.Cyclone && stampFullImageIntoPainterFbo()) {
                lastStampedMode = mode
            } else {
                clearPainterFbo()
                lastStampedMode = mode
            }
        }

        val hasRealSignal = snap.peak > RENDERER_BEAT_PEAK_FLOOR
        beatDecay = if (snap.isBeat && hasRealSignal) 1.0f
                    else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                        .coerceAtLeast(0f)
        if (snap.isBeat && hasRealSignal) Log.d(TAG, "BEAT! peak=${snap.peak} beatDecay=$beatDecay")
        val scaledBeatDecay = beatDecay * beatReactivity

        val cProg = cycloneProgram ?: return
        val pProg = painterPrograms[audioUniforms.activePainter] ?: return
        val rProg = ribbonProgram
        val bProg = blitProgram

        // Advance rotation. Keep angles in [0, 2π) so mediump shaders never see
        // large values where float ULP exceeds one frame's increment.
        val twoPi        = (2.0 * Math.PI).toFloat()
        val naturalSpeed = currentShape.rotationSpeedRadPerSec()
        if (!rotSpeedInitialized) { smoothedRotSpeed = naturalSpeed; rotSpeedInitialized = true }
        val target       = rotationSpeedTarget ?: naturalSpeed
        smoothedRotSpeed = smoothedRotSpeed * 0.95f + target * 0.05f
        if (absoluteTimeSec.isNaN()) {
            rotDt              = rotDt * 0.95f + dt * 0.05f  // filter OS jitter; τ ≈ 20 frames
            shapeAngleRad      = (shapeAngleRad + rotDt * smoothedRotSpeed).rem(twoPi)
            kaleidoRotationRad = (kaleidoRotationRad + rotDt * 0.05f).rem(twoPi)
        } else {
            shapeAngleRad      = (absoluteTimeSec * smoothedRotSpeed).rem(twoPi)
            kaleidoRotationRad = (absoluteTimeSec * 0.05f).rem(twoPi)
        }

        // Shake values hoisted here so Pass 3 (kaleido) can reuse the same frame values.
        val shakeAmp = beatDecay * 0.35f * beatReactivity
        val shakeX   = (shakeAmp * Math.sin(timeSec * 53.0)).toFloat()
        val shakeY   = (shakeAmp * Math.sin(timeSec * 45.0 + 1.3)).toFloat()

        // ── Collapse animation update ─────────────────────────────────────────
        smoothedBass  = smoothedBass * 0.7f + snap.bands[0] * 0.3f
        val riBass    = (snap.bands[0] + snap.bands[1]) * 0.5f
        val riMid     = (snap.bands[2] + snap.bands[3] + snap.bands[4]) / 3.0f
        val riTreble  = (snap.bands[5] + snap.bands[6] + snap.bands[7]) / 3.0f
        val riOverall = (riBass + riMid + riTreble) / 3.0f
        val riAmps    = floatArrayOf(riBass, riMid, riTreble, riOverall)

        for (i in 0..3) {
            val canTrigger = collapsePhase[i] == 0f
            if (snap.isBeat && riAmps[i] > beatThreshold && canTrigger) {
                collapsePhase[i] = 1.0f
                collapseTimer[i] = 0f
            }
            if (collapsePhase[i] > 0f || collapseTimer[i] > 0f) {
                collapseTimer[i] += dt
                val t = (collapseTimer[i] / 2.0f).coerceIn(0f, 1f)
                collapseState[i] = Math.sin(t * Math.PI).toFloat()
                if (collapseTimer[i] >= 2.0f) {
                    collapsePhase[i] = 0f
                    collapseState[i] = 0f
                    collapseTimer[i] = 0f
                }
            }
        }

        // ── Ribbon scratch update (painter-texture space 4096×256) ────────────
        val scratchReadTex  = if (ribbonReadIsA) ribbonTexA else ribbonTexB
        val scratchWriteFbo = if (ribbonReadIsA) ribbonFboB else ribbonFboA
        val scratchWriteTex = if (ribbonReadIsA) ribbonTexB else ribbonTexA

        if (rProg != null && ribbonFboA != 0 && ribbonFboB != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, scratchWriteFbo)
            GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, scratchReadTex)
            rProg.use()
            rProg.setVec2("u_resolution", PAINTER_TEX_W.toFloat(), PAINTER_TEX_H.toFloat())
            rProg.setFloat("u_time", timeSec)
            rProg.setFloatArray("u_bands", snap.bands)
            rProg.setVec4("u_collapse",
                collapseState[0], collapseState[1],
                collapseState[2], collapseState[3])
            rProg.setVec3("u_ribbon_color", ribbonColor[0], ribbonColor[1], ribbonColor[2])
            rProg.setInt("u_trail", 0)
            drawQuad()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        }
        ribbonReadIsA = !ribbonReadIsA

        // ── Pass 1: Painter — write a hue stripe at the rear angle ────────────
        // Rear is 270° past front (front = α+π/2, rear = α+3π/2).
        val rearU = ((shapeAngleRad + 3.0 * Math.PI / 2.0)
            .mod(2.0 * Math.PI) / (2.0 * Math.PI)).toFloat()
        val rearX = (rearU * PAINTER_TEX_W).toInt().coerceIn(0, PAINTER_TEX_W - 1)
        val endX  = rearX + PAINTER_STRIPE_W

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        // Blend enabled so painters that output alpha<1 leave existing FBO content intact.
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        pProg.use()

        val activePainter = audioUniforms.activePainter

        // Full painter contract — unused uniforms are optimized out (loc=-1, no-op).
        pProg.setFloat("u_time", timeSec)
        pProg.setFloat("u_peak", snap.peak)
        pProg.setFloat("u_beat", if (snap.isBeat) 1f else 0f)
        pProg.setFloat("u_beat_decay", scaledBeatDecay)
        pProg.setFloat("u_playback_fraction", audioUniforms.playbackFraction)
        pProg.setFloat("u_cyclone_angle", shapeAngleRad)
        pProg.setFloatArray("u_bands", snap.bands)
        // IMAGE painter: bind source image to unit 1.
        if (audioUniforms.activePainter == Painter.IMAGE && imageTexture != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexture)
            pProg.setInt("u_image", 1)
            pProg.setFloat("u_image_width", imageWidth.toFloat())
        }
        // SKIN painter: bind the selected skin texture to unit 0.
        if (audioUniforms.activePainter == Painter.SKIN) {
            val sTex = if (skinIndex >= 0) {
                val t = skinTextures.getOrElse(skinIndex) { 0 }
                if (t != 0) t else loadBuiltinSkinTexture(skinIndex)
            } else {
                val path = userSkinFilePath
                if (path != null) userSkinTextureCache.getOrPut(path) { loadUserSkinTexture(path) }
                else 0
            }
            if (sTex != 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sTex)
                pProg.setInt("u_skin", 0)
            }
        }
        if (endX <= PAINTER_TEX_W) {
            GLES30.glScissor(rearX, 0, PAINTER_STRIPE_W, PAINTER_TEX_H)
            drawQuad()
        } else {
            // Stripe wraps past the right edge — two scissored draws.
            GLES30.glScissor(rearX, 0, PAINTER_TEX_W - rearX, PAINTER_TEX_H)
            drawQuad()
            GLES30.glScissor(0, 0, endX - PAINTER_TEX_W, PAINTER_TEX_H)
            drawQuad()
        }
        if (audioUniforms.activePainter == Painter.IMAGE) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }
        if (audioUniforms.activePainter == Painter.SKIN) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
        // Ribbon stamp: blend ribbon scratch into the same painter stripe.
        // Black (RGB=0) with ribbon alpha composites over the fresh paint.
        if (bProg != null && ribbonFboA != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, scratchWriteTex)
            bProg.use()
            bProg.setInt("u_tex", 0)
            if (endX <= PAINTER_TEX_W) {
                GLES30.glScissor(rearX, 0, PAINTER_STRIPE_W, PAINTER_TEX_H)
                drawQuad()
            } else {
                GLES30.glScissor(rearX, 0, PAINTER_TEX_W - rearX, PAINTER_TEX_H)
                drawQuad()
                GLES30.glScissor(0, 0, endX - PAINTER_TEX_W, PAINTER_TEX_H)
                drawQuad()
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        // ── Pass 2: Shape — render 3D mesh into shapeFbo (kaleido reads this) ──
        if (shapeFbo == 0 || shapeFboW != surfaceWidth || shapeFboH != surfaceHeight) {
            destroyShapeFBO()
            createShapeFBO(surfaceWidth, surfaceHeight)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shapeFbo)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val modelM = FloatArray(16)
        val viewM  = FloatArray(16)
        val projM  = FloatArray(16)
        val vpM    = FloatArray(16)
        val mvpM   = FloatArray(16)

        val rotAxis = currentShape.rotationAxis()
        val scale   = currentShape.modelScale()
        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, shakeX, shakeY, 0f)
        Matrix.scaleM(modelM, 0, scale, scale, scale)
        Matrix.rotateM(modelM, 0,
            Math.toDegrees(shapeAngleRad.toDouble()).toFloat(),
            rotAxis[0], rotAxis[1], rotAxis[2])
        Matrix.setLookAtM(viewM, 0,
            0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.perspectiveM(projM, 0, 45f, wW / wH, 0.1f, 100f)
        Matrix.multiplyMM(vpM,  0, projM, 0, viewM,  0)
        Matrix.multiplyMM(mvpM, 0, vpM,   0, modelM, 0)

        // Re-upload LUT on context loss recovery (must happen on GL thread)
        if (lutEnabled && currentLutData != null && lutTextureId == 0) {
            uploadLutTexture(currentLutData!!)
        }
        val lutApplies = lutEnabled && lutTextureId != 0 &&
            (activePainter == Painter.SKIN || activePainter == Painter.IMAGE)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, painterTexture)

        // LUT 3D texture on unit 1 — applied in CYCLONE_FRAG after all palette ops
        if (lutApplies) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        }

        cProg.use()
        cProg.setMat4("u_mvp", mvpM)
        cProg.setInt("u_painterTexture", 0)
        cProg.setInt("u_invert_colors",    if (invertColors)    1 else 0)
        cProg.setInt("u_colorize_enabled",    if (colorizeEnabled)    1 else 0)
        cProg.setFloat("u_colorize_hue",      colorizeHue)
        cProg.setInt("u_distortion_enabled",  if (distortionEnabled)  1 else 0)
        cProg.setFloat("u_distortion_amplitude", distortionAmplitude)
        cProg.setFloat("u_distortion_frequency", distortionFrequency)
        if (warpStartSec <= -2f) warpStartSec = timeSec
        if (warpStartSec >= 0f && timeSec < warpStartSec) warpStartSec = timeSec
        val warpProgress = if (warpStartSec >= 0f && warpDurationSec > 0f)
            ((timeSec - warpStartSec) / warpDurationSec) else -1f
        cProg.setFloat("u_warp_progress",   warpProgress)
        cProg.setFloat("u_warp_amplitude",  warpAmplitude)
        cProg.setFloat("u_warp_seed",       warpSeed)
        if (warpProgress > 1f) warpStartSec = -1f
        cProg.setFloat("u_contrast",          contrast)
        cProg.setInt("u_contrast_passes",     contrastPasses)
        cProg.setFloat("u_saturation",        saturation)
        cProg.setInt("u_palette_mode",       paletteMode.ordinal)
        cProg.setFloat("u_palette_tint",     paletteTint)
        cProg.setFloat("u_palette_mono_hue", paletteMonoHue)
        if (lutApplies) {
            cProg.setInt("u_lut", 1)
            cProg.setFloat("u_lut_strength", lutStrength)
            cProg.setInt("u_lut_enabled", 1)
            lutDiagPending = false
        } else {
            // u_lut must always point to unit 1, not the default 0.
            // CYCLONE_FRAG declares sampler3D u_lut — leaving it at unit 0 (where the
            // 2D painterTexture is bound) triggers GL_INVALID_OPERATION at draw time in ES 3.0.
            cProg.setInt("u_lut", 1)
            cProg.setInt("u_lut_enabled", 0)
            lutDiagPending = false
        }
        if (paletteMode == PaletteMode.Harmony) {
            val offsets = harmonyType.hueOffsets()
            val absOffsets = FloatArray(8) { i ->
                if (i < offsets.size) wrapHue(harmonyAnchorHue + offsets[i]) else 0f
            }
            cProg.setInt("u_harmony_num_offsets", offsets.size)
            cProg.setFloatArray("u_harmony_offsets", absOffsets)
            cProg.setFloat("u_harmony_saturation", harmonySaturation)
            cProg.setFloat("u_harmony_value",      harmonyValue)
            cProg.setFloat("u_harmony_strength",   harmonyStrength)
        }
        cProg.setFloat("u_time",              timeSec)

        loadShapeMeshIfNeeded()
        GLES30.glBindVertexArray(shapeVao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, shapeIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        if (lutApplies) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }

        // Depth + cull off — kaleido is a fullscreen quad, ignores depth.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // ── Pass 2.5: Distortion Plus → distortionPlusFbo (optional) ─────────
        val finalShapeTex = if (distortionPlusEnabled) {
            val dpProg = distortionPlusProgram
            if (dpProg != null) {
                if (distortionPlusFbo == 0 ||
                    distortionPlusFboW != surfaceWidth ||
                    distortionPlusFboH != surfaceHeight) {
                    destroyDistortionPlusFBO()
                    createDistortionPlusFBO(surfaceWidth, surfaceHeight)
                }
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, distortionPlusFbo)
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shapeColorTex)
                dpProg.use()
                dpProg.setInt("u_content", 0)
                dpProg.setFloat("u_yaw",   Math.toRadians(distortionPlusYaw.toDouble()).toFloat())
                dpProg.setFloat("u_pitch", Math.toRadians(distortionPlusPitch.toDouble()).toFloat())
                dpProg.setFloat("u_roll",  Math.toRadians(distortionPlusRoll.toDouble()).toFloat())
                drawQuad()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                distortionPlusTex
            } else shapeColorTex
        } else shapeColorTex

        // ── Pass 3: Kaleido → intermediate FBO ───────────────────────────────
        val kProg = kaleidoProgram
        if (kProg != null) {
            if (kaleidoFBO == 0 || kaleidoTexW != surfaceWidth || kaleidoTexH != surfaceHeight) {
                destroyKaleidoFBO()
                createKaleidoFBO(surfaceWidth, surfaceHeight)
            }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, kaleidoFBO)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, finalShapeTex)
            kProg.use()
            kProg.setInt("u_content", 0)
            kProg.setVec2("u_resolution", wW, wH)
            kProg.setFloat("u_kaleido_rotation", kaleidoRotationRad)
            kProg.setFloat("u_kaleido_folds", foldCount.toFloat())
            val rotOffset = if (foldCount == 4 && squareRotationLocked) (Math.PI / 4.0).toFloat() else 0f
            kProg.setFloat("u_kaleido_rotation_offset", rotOffset)
            // Bass pulse: smoothedBass drives a zoom-in on kick hits.
            val bassMod = 1.0f + bassZoomIntensity * smoothedBass
            val effectiveZoom = currentShape.kaleidoZoom() / zoomMultiplier * bassMod
            kProg.setFloat("u_kaleido_zoom", effectiveZoom)
            drawQuad()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // Capture one tile per frame for Capture Mosaic (before frame overlay).
            captureController.captureTileIfActive(kaleidoFBO, kaleidoTexW, kaleidoTexH)

            // ── Pass 3.5: Chromatic Aberration → chromaAberrationFbo (optional) ──
            val postChromaTex = if (chromaAberrationEnabled) {
                val caProg = chromaAberrationProgram
                if (caProg != null) {
                    if (chromaAberrationFbo == 0 ||
                        chromaAberrationFboW != surfaceWidth ||
                        chromaAberrationFboH != surfaceHeight) {
                        destroyChromaAberrationFBO()
                        createChromaAberrationFBO(surfaceWidth, surfaceHeight)
                    }
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, chromaAberrationFbo)
                    GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                    GLES30.glDisable(GLES30.GL_BLEND)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, kaleidoTex)
                    caProg.use()
                    caProg.setInt("u_tex", 0)
                    caProg.setFloat("u_offset", chromaAberrationIntensity)
                    caProg.setFloat("u_beat", beatDecay)
                    caProg.setFloat("u_audio_scale", if (chromaAberrationAudioReact) 0.5f else 0f)
                    drawQuad()
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                    chromaAberrationTex
                } else kaleidoTex
            } else kaleidoTex

            // ── Pass 3.6: Blackhole video-feedback tunnel (optional) ─────────
            val finalKaleidoTex = if (blackholeEnabled) {
                val bhProg = blackholeProgram
                if (bhProg != null) {
                    if (blackholeFboA == 0 ||
                        blackholeFboW != surfaceWidth ||
                        blackholeFboH != surfaceHeight) {
                        destroyBlackholeFeedbackFBOs()
                        createBlackholeFeedbackFBOs(surfaceWidth, surfaceHeight)
                        blackholeReadIsA    = true
                        blackholeWasEnabled = false
                    }
                    if (!blackholeWasEnabled) {
                        for (fbo in intArrayOf(blackholeFboA, blackholeFboB)) {
                            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
                            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                            GLES30.glClearColor(0f, 0f, 0f, 1f)
                            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                        }
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                        blackholeWasEnabled = true
                    }
                    val readTex  = if (blackholeReadIsA) blackholeTexA else blackholeTexB
                    val writeFbo = if (blackholeReadIsA) blackholeFboB else blackholeFboA
                    val writeTex = if (blackholeReadIsA) blackholeTexB else blackholeTexA
                    val cx = 0.5f + blackholeWanderAmount * Math.sin(timeSec * 0.7 + 1.3).toFloat()
                    val cy = 0.5f + blackholeWanderAmount * Math.cos(timeSec * 0.5).toFloat()
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, writeFbo)
                    GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                    GLES30.glDisable(GLES30.GL_BLEND)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, readTex)       // u_prev
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, postChromaTex) // u_scene
                    bhProg.use()
                    bhProg.setInt("u_prev",  0)
                    bhProg.setInt("u_scene", 1)
                    bhProg.setFloat("u_center_x",     cx)
                    bhProg.setFloat("u_center_y",     cy)
                    bhProg.setFloat("u_shrink_rate",  blackholeShrinkRate)
                    bhProg.setFloat("u_strength",     blackholeStrength)
                    bhProg.setFloat("u_alpha_radius", blackholeAlphaRadius)
                    drawQuad()
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                    blackholeReadIsA = !blackholeReadIsA
                    writeTex
                } else postChromaTex
            } else {
                blackholeWasEnabled = false
                postChromaTex
            }

            // ── Pass 4: Frame overlay → lightningFbo (if lightning) else outputFbo ──
            val lightningReady = lightningEnabled && lightningProgram != null && lightningCompositeProgram != null
            if (lightningReady) {
                if (lightningFbo == 0 || lightningFboW != surfaceWidth || lightningFboH != surfaceHeight) {
                    destroyLightningFBO()
                    createLightningFBO(surfaceWidth, surfaceHeight)
                }
            }
            val pass4Target = if (lightningReady && lightningFbo != 0) lightningFbo else outputFbo
            val foProg = frameOverlayProgram
            if (foProg != null) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, pass4Target)
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, finalKaleidoTex)
                foProg.use()
                foProg.setInt("u_kaleido_tex", 0)
                foProg.setVec2("u_resolution", wW, wH)
                val argb = frameColorArgb
                val fA = ((argb shr 24) and 0xFFL).toFloat() / 255f
                val fR = ((argb shr 16) and 0xFFL).toFloat() / 255f
                val fG = ((argb shr  8) and 0xFFL).toFloat() / 255f
                val fB = (argb           and 0xFFL).toFloat() / 255f
                foProg.setInt("u_frame_shape", frameShape.ordinal)
                foProg.setVec4("u_frame_color", fR, fG, fB, fA)
                foProg.setFloat("u_frame_size", 0.52f)
                drawQuad()
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                if (pass4Target != 0) GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            }

            // ── Pass 5: SNES lightning — 5A bolt at 1/3 res, 5B upscale+composite ─
            if (lightningReady && lightningFbo != 0 &&
                lightningFboW == surfaceWidth && lightningFboH == surfaceHeight) {

                val lowW = (surfaceWidth  / 3).coerceAtLeast(1)
                val lowH = (surfaceHeight / 3).coerceAtLeast(1)
                if (boltLowFbo == 0 || boltLowFboW != lowW || boltLowFboH != lowH) {
                    destroyBoltLowFBO()
                    createBoltLowFBO(lowW, lowH)
                }

                lightningSystem.update(timeSec, lightningTouchX, lightningTouchY, lightningTouching)

                // 5A: Bolt-only at low resolution (GL_NEAREST gives chunky pixels on upscale)
                val bProg = lightningProgram!!
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, boltLowFbo)
                GLES30.glViewport(0, 0, lowW, lowH)
                GLES30.glClearColor(0f, 0f, 0f, 0f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                GLES30.glDisable(GLES30.GL_BLEND)
                bProg.use()
                bProg.setVec3("u_color", 0.3f, 0.6f, 1.0f)
                bProg.setFloat("u_aspect", wW / wH)
                bProg.setInt("u_seg_count", lightningSystem.segCount)
                bProg.setVec2Array("u_seg_a", lightningSystem.segA)
                bProg.setVec2Array("u_seg_b", lightningSystem.segB)
                bProg.setFloatArray("u_seg_w", lightningSystem.segW)
                drawQuad()

                // 5B: Upscale bolt + additive composite over scene
                val cProg = lightningCompositeProgram!!
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFbo)
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lightningTex)   // scene from Pass 4
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, boltLowTex)     // chunky bolt
                cProg.use()
                cProg.setInt("u_scene", 0)
                cProg.setInt("u_bolt",  1)
                drawQuad()
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
                if (outputFbo != 0) GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            }
        }

        GLES30.glClearColor(0f, 0f, 0f, 1f)
    }

    // Resets per-session animation state after a procedural warmup, preserving the painter
    // FBO content (that's the whole point of warmup) and lastStampedMode.
    fun resetAnimationState() {
        shapeAngleRad      = 0f
        kaleidoRotationRad = 0f
        collapseState.fill(0f)
        collapsePhase.fill(0f)
        collapseTimer.fill(0f)
        beatDecay          = 0f
        ribbonReadIsA      = true
        if (ribbonFboA != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ribbonFboA)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        if (ribbonFboB != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ribbonFboB)
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        Log.d(TAG, "resetAnimationState: encode-session state reset (painterFBO preserved)")
    }

    // ── Release all GL resources — call from the same thread that owns the context. ──

    fun release() {
        testProgram?.delete();    testProgram    = null
        warpProgram?.delete();    warpProgram    = null
        driftProgram?.delete();   driftProgram   = null
        cycloneProgram?.delete(); cycloneProgram = null
        kaleidoProgram?.delete(); kaleidoProgram = null
        painterPrograms.values.forEach { it.delete() }
        painterPrograms.clear()
        ribbonProgram?.delete();          ribbonProgram          = null
        blitProgram?.delete();            blitProgram            = null
        frameOverlayProgram?.delete();    frameOverlayProgram    = null
        distortionPlusProgram?.delete();   distortionPlusProgram   = null
        chromaAberrationProgram?.delete(); chromaAberrationProgram = null
        blackholeProgram?.delete();        blackholeProgram        = null
        lightningProgram?.delete();          lightningProgram          = null
        lightningCompositeProgram?.delete(); lightningCompositeProgram = null

        if (vaoId            != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vaoId),            0); vaoId            = 0 }
        if (vboId            != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(vboId),            0); vboId            = 0 }
        if (shapeVao         != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(shapeVao),         0); shapeVao         = 0 }
        if (shapePositionVbo != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapePositionVbo), 0); shapePositionVbo = 0 }
        if (shapeUvVbo       != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapeUvVbo),       0); shapeUvVbo       = 0 }
        if (shapeIbo         != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapeIbo),         0); shapeIbo         = 0 }
        shapeMeshLoaded = false; loadedShapeName = null

        if (painterFBO     != 0) { GLES30.glDeleteFramebuffers(1, intArrayOf(painterFBO),     0); painterFBO     = 0 }
        if (painterTexture != 0) { GLES30.glDeleteTextures(1,     intArrayOf(painterTexture), 0); painterTexture = 0 }
        if (imageTexture   != 0) { GLES30.glDeleteTextures(1,     intArrayOf(imageTexture),   0); imageTexture   = 0 }

        val nonZeroSkinTex = skinTextures.filter { it != 0 }
        if (nonZeroSkinTex.isNotEmpty()) {
            GLES30.glDeleteTextures(nonZeroSkinTex.size, nonZeroSkinTex.toIntArray(), 0)
            skinTextures.fill(0)
        }
        val nonZeroUserTex = userSkinTextureCache.values.filter { it != 0 }
        if (nonZeroUserTex.isNotEmpty()) {
            GLES30.glDeleteTextures(nonZeroUserTex.size, nonZeroUserTex.toIntArray(), 0)
        }
        userSkinTextureCache.clear()

        if (lutTextureId != 0) { GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0); lutTextureId = 0 }

        destroyKaleidoFBO()
        destroyShapeFBO()
        destroyDistortionPlusFBO()
        destroyChromaAberrationFBO()
        destroyBlackholeFeedbackFBOs()
        destroyLightningFBO()
        destroyBoltLowFBO()
        destroyRibbonFBOs()
        destroyFBO()

        Log.d(TAG, "AbstraktRenderer: released all GL resources")
    }

    fun triggerSuddenWarp() {
        if (!suddenWarpEnabled) return
        warpDurationSec = 1.0f + warpRandom.nextFloat() * 2.0f
        warpSeed        = warpRandom.nextFloat()
        warpStartSec    = -2f
    }

    fun applyLut(lut: CubeLut?) {
        // Called on GL thread via queueEvent
        Log.d(TAG, "applyLut: ${lut?.name ?: "null"} prevTexId=$lutTextureId")
        if (lutTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            lutTextureId = 0
        }
        currentLutData = lut
        if (lut != null) {
            uploadLutTexture(lut)
            lutDiagPending = true
        }
    }

    private fun uploadLutTexture(lut: CubeLut) {
        val n = lut.size
        val buf = ByteBuffer.allocateDirect(n * n * n * 3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(lut.data); buf.position(0)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        lutTextureId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTextureId)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB16F,
            n, n, n, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
    }

    private fun debugPainterStats() {
        val w = PAINTER_TEX_W
        val h = PAINTER_TEX_H
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, painterFBO)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
        var sumR = 0L; var sumG = 0L; var sumB = 0L
        var nonzeroPixels = 0
        val totalPixels = w * h
        buf.position(0)
        for (i in 0 until totalPixels) {
            val r = buf.get().toInt() and 0xFF
            val g = buf.get().toInt() and 0xFF
            val b = buf.get().toInt() and 0xFF
            buf.get()
            if (r > 1 || g > 1 || b > 1) nonzeroPixels++
            sumR += r; sumG += g; sumB += b
        }
        Log.d(TAG, "Painter stats: avg=(${sumR/totalPixels},${sumG/totalPixels},${sumB/totalPixels}) " +
            "nonzero=${nonzeroPixels}/${totalPixels} (${(nonzeroPixels * 100.0 / totalPixels).toInt()}%)")
    }

    private fun loadShapeMeshIfNeeded() {
        if (shapeMeshLoaded && loadedShapeName == currentShape.name) return

        if (shapeVao         != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(shapeVao),         0); shapeVao         = 0 }
        if (shapePositionVbo != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapePositionVbo), 0); shapePositionVbo = 0 }
        if (shapeUvVbo       != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapeUvVbo),       0); shapeUvVbo       = 0 }
        if (shapeIbo         != 0) { GLES30.glDeleteBuffers(1,      intArrayOf(shapeIbo),         0); shapeIbo         = 0 }

        val mesh = currentShape.buildMesh()

        val posVbos = IntArray(1)
        GLES30.glGenBuffers(1, posVbos, 0)
        shapePositionVbo = posVbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shapePositionVbo)
        val posBuf = ByteBuffer.allocateDirect(mesh.positions.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        posBuf.put(mesh.positions).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.positions.size * Float.SIZE_BYTES, posBuf, GLES30.GL_STATIC_DRAW)

        val uvVbos = IntArray(1)
        GLES30.glGenBuffers(1, uvVbos, 0)
        shapeUvVbo = uvVbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shapeUvVbo)
        val uvBuf = ByteBuffer.allocateDirect(mesh.uvs.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        uvBuf.put(mesh.uvs).position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, mesh.uvs.size * Float.SIZE_BYTES, uvBuf, GLES30.GL_STATIC_DRAW)

        val ibos = IntArray(1)
        GLES30.glGenBuffers(1, ibos, 0)
        shapeIbo = ibos[0]
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, shapeIbo)
        val idxBuf = ByteBuffer.allocateDirect(mesh.indices.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        idxBuf.put(mesh.indices).position(0)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * Short.SIZE_BYTES, idxBuf, GLES30.GL_STATIC_DRAW)

        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        shapeVao = vaos[0]
        GLES30.glBindVertexArray(shapeVao)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shapePositionVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shapeUvVbo)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, shapeIbo)

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        shapeIndexCount = mesh.indices.size
        shapeMeshLoaded = true
        loadedShapeName = currentShape.name
        Log.d(TAG, "Shape mesh loaded: ${currentShape.name} vertices=${mesh.positions.size / 3} indices=$shapeIndexCount")
    }

    private fun drawQuad() {
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    // Blits the current mode's skin texture over the entire painter FBO.
    // Returns false (and leaves FBO untouched) if the texture isn't available yet.
    private fun stampFullSkinIntoPainterFbo(mode: Mode): Boolean {
        val bProg = blitProgram ?: return false
        val sTex: Int = when (mode) {
            is Mode.Builtin  -> {
                val t = skinTextures.getOrElse(skinIndex) { 0 }
                if (t != 0) t else loadBuiltinSkinTexture(skinIndex)
            }
            is Mode.UserSlot -> {
                val path = userSkinFilePath ?: return false
                userSkinTextureCache[path] ?: return false  // don't block GL thread
            }
            else -> return false
        }
        if (sTex == 0) return false

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sTex)
        bProg.use()
        bProg.setInt("u_tex", 0)
        drawQuad()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Log.d(TAG, "stampFullSkin: $mode texId=$sTex")
        return true
    }

    // Fills the entire painterFBO by blitting imageTexture directly — same blit path as
    // stampFullSkinIntoPainterFbo, avoids IMAGE shader/uniform issues on cold start.
    private fun stampFullImageIntoPainterFbo(): Boolean {
        val bProg = blitProgram ?: run { Log.w(TAG, "stampFullImage SKIP: blitProgram=null"); return false }
        if (imageTexture == 0) { Log.w(TAG, "stampFullImage SKIP: imageTexture=0"); return false }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTexture)
        bProg.use()
        bProg.setInt("u_tex", 0)
        drawQuad()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return true
    }

    private fun clearPainterFbo() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, painterFBO)
        GLES30.glViewport(0, 0, PAINTER_TEX_W, PAINTER_TEX_H)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        Log.d(TAG, "clearPainterFbo: entering $currentMode")
    }

    // Loads skin(idx+1) from drawable on the GL thread and caches it in skinTextures.
    // Safe to call from renderFrame / stampFullSkinIntoPainterFbo on first access.
    private fun loadBuiltinSkinTexture(idx: Int): Int {
        val n = idx + 1
        return try {
            val opts  = BitmapFactory.Options().also { it.inScaled = false }
            val resId = context.resources.getIdentifier("skin$n", "drawable", context.packageName)
            val bmp   = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return 0
            val tIds  = IntArray(1)
            GLES30.glGenTextures(1, tIds, 0)
            skinTextures[idx] = tIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, skinTextures[idx])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,     GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,     GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            Log.d(TAG, "skinTexture[$n] loaded on-demand id=${skinTextures[idx]}")
            skinTextures[idx]
        } catch (e: Exception) {
            Log.w(TAG, "skin$n on-demand load failed: ${e.message}")
            0
        }
    }

    fun invalidateUserSkinTexture(path: String) {
        val texId = userSkinTextureCache.remove(path) ?: return
        GLES30.glDeleteTextures(1, intArrayOf(texId), 0)
        Log.d(TAG, "invalidateUserSkinTexture: deleted texId=$texId path=$path")
    }

    private fun loadUserSkinTexture(path: String): Int {
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false }
            val bmp  = android.graphics.BitmapFactory.decodeFile(path, opts) ?: return 0
            val tIds = IntArray(1)
            GLES30.glGenTextures(1, tIds, 0)
            val texId = tIds[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,     GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,     GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,     GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            Log.d(TAG, "userSkin loaded: $path id=$texId")
            texId
        } catch (e: Exception) {
            Log.w(TAG, "userSkin load failed: ${e.message}")
            0
        }
    }
}
