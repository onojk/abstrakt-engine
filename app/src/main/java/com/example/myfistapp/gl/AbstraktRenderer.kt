package com.example.myfistapp.gl

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
import com.example.myfistapp.FrameShape
import com.example.myfistapp.Mode
import com.example.myfistapp.ShapeKind
import com.example.myfistapp.audio.AudioFile
import com.example.myfistapp.audio.AudioSnapshot
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
    private var kaleidoTexW = 0
    private var kaleidoTexH = 0
    private var shapeFbo         = 0
    private var shapeColorTex    = 0
    private var shapeDepthBuffer = 0
    private var shapeFboW        = 0
    private var shapeFboH        = 0
    @Volatile var zoomMultiplier  = 1.0f
    @Volatile var invertColors    = false
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

    private val _currentShapeName = MutableStateFlow("Cylinder")
    val currentShapeName: StateFlow<String> = _currentShapeName.asStateFlow()

    fun setShapeKind(kind: ShapeKind) {
        currentShape = shapeFor(kind)
        _currentShapeName.value = currentShape.name
        Log.d(TAG, "setShapeKind → ${currentShape.name}")
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
        lastTimeLogMs   = 0L
        lastStampedMode   = null   // force re-stamp after context loss
        allResourcesReady = false
        audioUniforms.uniformsLogged = false

        GLES30.glClearColor(0f, 0f, 0f, 1f)

        testProgram    = ShaderProgram(Shaders.TEST_VERT,    Shaders.TEST_FRAG)
        warpProgram    = ShaderProgram(Shaders.WARP_VERT,    Shaders.WARP_FRAG)
        driftProgram   = ShaderProgram(Shaders.TEST_VERT,    Shaders.DRIFT_POLAR_FRAG)
        cycloneProgram = ShaderProgram(Shaders.CYCLONE_VERT, Shaders.CYCLONE_FRAG)
        kaleidoProgram = ShaderProgram(Shaders.TEST_VERT,    Shaders.CYCLONE_KALEIDO_FRAG)
        allPainters().forEach { entry ->
            painterPrograms[entry.painter] = ShaderProgram(entry.vert, entry.frag)
        }

        ribbonProgram       = ShaderProgram(Shaders.TEST_VERT, Shaders.RIBBON_FRAG)
        blitProgram         = ShaderProgram(Shaders.TEST_VERT, Shaders.BLIT_FRAG)
        frameOverlayProgram = ShaderProgram(Shaders.TEST_VERT, Shaders.FRAME_OVERLAY_FRAG)

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
                beatDecay = if (snap.isBeat) 1.0f
                            else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                                .coerceAtLeast(0f)
                driftState.update(timeSec, snap.bands)
                prog.use()
                audioUniforms.applyToProgram(prog, timeSec)
                driftState.applyToProgram(prog)
                prog.setFloat("u_beat_decay", beatDecay)
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
    ) {
        val wW   = surfaceWidth.toFloat()
        val wH   = surfaceHeight.toFloat()
        val snap = audioSnapshot ?: AudioSnapshot(FloatArray(8), 0f, false)

        // ── Mode-change: stamp full skin or clear FBO on mode switch ──────────
        if (mode != lastStampedMode) {
            if (mode is Mode.Builtin || mode is Mode.UserSlot) {
                if (stampFullSkinIntoPainterFbo(mode)) lastStampedMode = mode
            } else {
                clearPainterFbo()
                lastStampedMode = mode
            }
        }

        beatDecay = if (snap.isBeat) 1.0f
                    else (beatDecay * Math.exp((-dt * 5.0).toDouble()).toFloat())
                        .coerceAtLeast(0f)
        if (snap.isBeat) Log.d(TAG, "BEAT! peak=${snap.peak} beatDecay=$beatDecay")

        val cProg = cycloneProgram ?: return
        val pProg = painterPrograms[audioUniforms.activePainter] ?: return
        val rProg = ribbonProgram
        val bProg = blitProgram

        // Advance rotation. Keep angles in [0, 2π) so mediump shaders never see
        // large values where float ULP exceeds one frame's increment.
        val twoPi = (2.0 * Math.PI).toFloat()
        shapeAngleRad      = (shapeAngleRad + dt * currentShape.rotationSpeedRadPerSec()).rem(twoPi)
        kaleidoRotationRad = (kaleidoRotationRad + dt * 0.05f).rem(twoPi)

        // Shake values hoisted here so Pass 3 (kaleido) can reuse the same frame values.
        val shakeAmp = beatDecay * 0.35f
        val shakeX   = (shakeAmp * Math.sin(timeSec * 53.0)).toFloat()
        val shakeY   = (shakeAmp * Math.sin(timeSec * 45.0 + 1.3)).toFloat()

        // ── Collapse animation update ─────────────────────────────────────────
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
        // Full painter contract — unused uniforms are optimized out (loc=-1, no-op).
        pProg.setFloat("u_time", timeSec)
        pProg.setFloat("u_peak", snap.peak)
        pProg.setFloat("u_beat", if (snap.isBeat) 1f else 0f)
        pProg.setFloat("u_beat_decay", beatDecay)
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

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, painterTexture)

        cProg.use()
        cProg.setMat4("u_mvp", mvpM)
        cProg.setInt("u_painterTexture", 0)
        cProg.setInt("u_invert_colors", if (invertColors) 1 else 0)

        loadShapeMeshIfNeeded()
        GLES30.glBindVertexArray(shapeVao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, shapeIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        // Depth + cull off — kaleido is a fullscreen quad, ignores depth.
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

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
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shapeColorTex)
            kProg.use()
            kProg.setInt("u_content", 0)
            kProg.setVec2("u_resolution", wW, wH)
            kProg.setFloat("u_kaleido_rotation", kaleidoRotationRad)
            kProg.setFloat("u_kaleido_folds", foldCount.toFloat())
            val rotOffset = if (foldCount == 4 && squareRotationLocked) (Math.PI / 4.0).toFloat() else 0f
            kProg.setFloat("u_kaleido_rotation_offset", rotOffset)
            // Invert: higher user % = more zoomed in = smaller sampling radius.
            val effectiveZoom = currentShape.kaleidoZoom() / zoomMultiplier
            kProg.setFloat("u_kaleido_zoom", effectiveZoom)
            drawQuad()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

            // ── Pass 4: Frame overlay → screen / encoder surface ──────────────
            val foProg = frameOverlayProgram
            if (foProg != null) {
                GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, kaleidoTex)
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
        ribbonProgram?.delete();       ribbonProgram       = null
        blitProgram?.delete();         blitProgram         = null
        frameOverlayProgram?.delete(); frameOverlayProgram = null

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

        destroyKaleidoFBO()
        destroyShapeFBO()
        destroyRibbonFBOs()
        destroyFBO()

        Log.d(TAG, "AbstraktRenderer: released all GL resources")
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
