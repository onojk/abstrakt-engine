# Abstrakt Android — Project Journal

## Week 1 — Day 1 (May 4, 2026)

### Done
- Installed Android Studio Panda 4 on Ubuntu 25.10
- Configured KVM hardware acceleration (kvm-ok confirmed)
- Created project: MyFistApp (typo, kept) at ~/AndroidStudioProjects/MyFistApp
- Package: com.example.myfistapp
- Min SDK: API 24, Build config: Kotlin DSL
- Configured Pixel 8 emulator with API 35 (Google Play Intel x86_64 Atom)
- "Hello Android!" runs on emulator
- Modified default text → "Jonathan Kendall"
- Pasted in RippleField visualizer:
  - Tap-to-ripple gesture (detectTapGestures)
  - Animated concentric circles (Canvas + Animatable)
  - Hue-cycling background (LaunchedEffect)
  - Tap counter (mutableStateOf + remember)
  - Random hue per ripple
- Initialized Git repo (branch: main)
- Commit 1de1f36: week 1 state locked in

### Learnings
- Compose UI is "Kotlin with annotations" — feels alien without Kotlin fluency
- Emulator first-boot is slow (5-10 min); subsequent boots ~30 sec
- Yellow warnings (unused imports, mutableStateOf vs mutableIntStateOf, typos) don't block builds
- BUILD SUCCESSFUL in 1s after first sync

### Notes
- Emulator is functional but slow — animations stutter
- Real phone testing will be 5-10x faster (defer until weeks 4+)
- 30 GB RAM laptop handles Android Studio + emulator fine
- Code feels like magic right now — Kotlin Koans next week will fix this

### Next (Week 2)
- Kotlin Koans: https://play.kotlinlang.org/koans/
- Target: ~14-28 hours over 2 weeks
- Sections: Introduction → Conventions → Collections → Properties → Builders
- DO NOT touch Android Studio code yet
- Just learn Kotlin syntax in the browser playground

### Standing rules
- Every shipped Android visualizer must have a paired feature/UI
- One platform first (Android), evaluate iOS after MVP
- Pace: ~1 hour per session, 4-5 sessions per week


## Week 1 — Day 2 (May 5, 2026)

### Architecture correction
- Project is an **offline renderer**, not real-time — same model as the Python parent
- User picks an audio file → app decodes offline → (eventually) produces MP4
- No microphone, no real-time processing, no streaming

### Done — Tier 1: audio ingestion + waveform display
- Created `audio/AudioFile.kt`:
  - `data class AudioFile(uri, durationMs, sampleRate, channelCount, amplitudeEnvelope)`
  - `suspend fun loadAndAnalyze(context, uri)`: full MediaExtractor + MediaCodec PCM decode loop
  - RMS amplitude computed per ~50ms window, normalized to 0..1 FloatArray
  - Handles anything MediaExtractor supports (mp3, wav, m4a, …)
- Created `visualizers/WaveformView.kt`:
  - SAF file picker via `OpenDocument` (no storage permission needed — SAF handles access)
  - `loadAndAnalyze` runs on `Dispatchers.IO`, spinner shown during decode
  - Mirror waveform: vertical bars symmetric top/bottom from centerline, one bar per canvas pixel
  - MediaPlayer playback with play/pause button
  - White cursor sweeps left→right at ~60fps via `LaunchedEffect` + `delay(16)`
  - Visual style: `#0A0A0F` background, `#00FFFF` neon cyan waveform, hot pink pause button
- Updated `AndroidManifest.xml`: `READ_MEDIA_AUDIO` (no `RECORD_AUDIO`)
- Replaced `RippleField` with `WaveformView` in `MainActivity.kt`
- Added `dev.sh`: terminal workflow script (build / install / run / log)
- Commit dbbbfa9, pushed to origin/main

### Tested
- Voltage-Delayed.wav: 289s, 48000 Hz, stereo — decodes, renders, plays back
- Cursor tracks playback position correctly
- Play/pause, file repick, error handling all working

### Learnings
- MediaCodec is verbose but predictable: extractor feeds compressed frames, codec drains PCM16-LE
- SAF (`OpenDocument`) grants URI access without any runtime permission request
- `LaunchedEffect(key)` cancels and restarts cleanly — good pattern for playback cursor
- `rememberCoroutineScope()` is the right hook for launching IO work from a button/callback
- Coroutines available transitively via `lifecycle-runtime-ktx` — no extra dep needed

### Next (Tier 2 — not this week)
- Week 2 is still Kotlin Koans only — do not touch Android code
- After Koans: frame-by-frame visualizer rendering (warpfield aesthetic on Canvas)
- Then: MediaCodec + MediaMuxer encoding to MP4
- Full offline render pipeline: file → decode → visualize → encode → share

### Standing rules (updated)
- This is offline, not real-time. Never add RECORD_AUDIO or streaming audio capture.
- Every shipped Android visualizer must have a paired feature/UI
- One platform first (Android), evaluate iOS after MVP
- Pace: ~1 hour per session, 4-5 sessions per week

---

## Week 1 — Day 3 (May 5, 2026)

### Overview

Marathon session. Started with a Compose-Canvas + AGSL hybrid pipeline (three visualizers, no GL). Ended with a full OpenGL ES 3.0 renderer coexisting alongside the Compose stack — 5 visualizer modes across 7 slices committed and pushed. Scope expanded well past what was planned, and the architecture evolved twice mid-session in ways that weren't obvious at the start.

### Done — Slices 1–3: Compose Canvas visualizers

- Slice 1: `WarpfieldCanvas` — animated dot grid (40×70 = 2800 dots) driven by `AudioSnapshot`
  - `AudioAnalyzer` / `snapshotAt()` extracts 8 pseudo-bands from the pre-decoded amplitude envelope: two window sizes for low/mid, RMS derivative for treble proxy, beat detection via 60-frame moving average threshold
  - 5 influencer points orbit in screen space, repel nearby dots with inverse-square falloff; orbits are phase-staggered 72° apart so coverage is dense and distributed
  - Playback fraction maps to envelope frame — offline render model maintained throughout
- Slice 2: `KaleidoscopeCanvas` ("Echo") — applies a 12-fold radial mirror to whatever renders beneath it
  - AGSL `RuntimeShader` (`KaleidoscopeShader.kt`) performs the fold on GPU via `graphicsLayer { renderEffect }`
  - Sampling: folds angle into [0°, 15°] wedge, mirrors back; corners (r > 0.5 inscribed circle) clamped to avoid wrap-around artifacts
  - User-facing label is "Echo"; internal class names kept as "Kaleidoscope" for stability
- Slice 3: `GuitarStringsLayer` composited inside Echo — 6 radial strings, each mapped to one audio band, amplitude-driven spike height with 4-frame painted trail, mirrored by kaleido fold

### Done — Slices 4a–4c: OpenGL ES 3.0 renderer

The Compose pipeline was hitting CPU-side limits. Moved core rendering to GLES 3.0.

- Slice 4a: renderer scaffold
  - `AbstraktGLSurfaceView`: `GLSurfaceView` subclass, GLES 3.0 context, `RENDERMODE_CONTINUOUSLY`
  - `AbstraktRenderer`: implements `GLSurfaceView.Renderer`; fullscreen quad (triangle strip), single VAO/VBO, shared vertex shader for all modes
  - `GlCanvas` composable: wraps `AbstraktGLSurfaceView` in `AndroidView`, manages lifecycle via `DisposableEffect` + `LifecycleEventObserver` (kicks `onResume()` manually on first composition since observer won't fire retroactively for already-resumed lifecycle)
  - `ShaderProgram`: compile + link helper, uniform location cache
- Slice 4b: audio→GL bridge
  - `AudioUniforms`: holds `@Volatile audioFile` and `@Volatile playbackFraction` (main thread writes, GL thread reads); `getSnapshot()` / `applyToProgram()` push `u_time`, `u_peak`, `u_beat`, `u_bands[8]`, `u_playback_fraction` each frame
  - `AudioSnapshot`: `data class` shared between Compose and GL paths
- Slice 4c: GL Warpfield port
  - `WARP_FRAG`: procedural dot grid entirely in the fragment shader — cell-snapped Voronoi, 3×3 neighbor search per fragment, nearest-dot distance drives `smoothstep` glow; displacement computed from the same 5-influencer orbit logic as the Compose version (`Influencers.kt`)
  - `WARP_VERT` / `KALEIDO_VERT` aliased to the shared passthrough vertex shader
  - UI consolidation: 5-button row would have overflowed 1080px; collapsed to 4 buttons (Wave / Warp / Echo / Drift); GL_TEST mode retired from the UI; Compose `WarpfieldCanvas` kept as Echo's internal rendering layer

### Done — Slice 5: Drift visualizer

Second equalizer. Design goal: 32 scrambled-position bars in a 12-fold radial mandala, with layered ghost history and a solid beat-pulsed foundation.

**First attempt: cartesian FBO + kaleido post-pass**
Built the bars as vertical fills in a framebuffer object, then sampled that FBO through the kaleido fold shader. This rendered consistently black. Spent significant time diagnosing — ring buffer logcat looked correct, GL pipeline reported no errors, FBO reported `COMPLETE`.

Root cause was geometric: the kaleido fold maps angles to [0°, 15°] in the source image, which constrains both cos and sin to be positive. `sampleUV = 0.5 + r·(cos θ, sin θ)` always ≥ 0.5 in both axes — the kaleido only ever samples the top-right quadrant of the FBO. Vertical bars fill bottom-upward; they live in the lower portion of the FBO. The kaleido never sees them. The two representations are geometrically incompatible.

**Fix: single-pass polar shader (`DRIFT_POLAR_FRAG`)**
Scrapped the FBO. Bars computed directly in polar coordinates: `r = length(uv − 0.5) × 2`, angle folded to [0°, 15°] for 12-fold symmetry, folded angle mapped to column index, bar membership test is `r ≤ h` (radial, not vertical). The kaleido fold and the bar geometry are now the same operation — no two-pass mismatch possible.

- `DriftState`: ring buffer `bandRing[16 × 8]`, writes at 80ms intervals; exports `bandRingExport[12 × 8]` newest-first for the shader; 4 independent Fisher-Yates scrambles (seeds 42, 137, 293, 511) → `scrambleF[128]`
- 4 ghost layers: read offsets [0, 1, 3, 6] ticks (lag 0/80/240/480ms), quadratic alpha falloff `t² × 0.55 × LAYER_ALPHA[l]`, 6 history frames per layer, independent hue shifts [0, 0.12, 0.25, 0.38]
- Anchor layer: solid opaque base rendered first; same column → band mapping as ghost layer 0; beat-pulsed via CPU-side `beatDecay` field in `AbstraktRenderer` (spikes to 1.0 on `isBeat`, decays `× exp(−5dt)` each frame, τ ≈ 200ms); passed as `u_beat_decay`; anchor color deep navy `vec3(0.04, 0.06, 0.55)` → electric blue flash at beat peak
- FBO helpers (`createFBO` / `destroyFBO`) kept as private methods on `AbstraktRenderer` for future visualizers with cartesian structure that genuinely want the two-pass approach

### Notable debugging moments

- **Logcat misread**: ring buffer data confirmed working (4 layers showed genuinely different band values); this ruled out `DriftState` as the bug source but cost a debugging round
- **Wrong instruction**: mid-diagnosis, a logcat snippet showed the current GL program not matching `driftProgram` immediately before a `use()` call. Interpreted this as swapped program order and requested a program swap. The assistant correctly refused — those diagnostic checks fired *before* the respective `use()` calls, so "match=false" was expected behavior, not evidence of a swap. The swap would have introduced a real bug. Geometric analysis was the correct path.
- **Logcat green ≠ visual works**: reinforced several times. "No GL errors, FBO COMPLETE, uniforms located" tells you the pipeline is healthy. It doesn't tell you whether you're sampling the right region of the framebuffer.

### Known issues (not fixed)

- `AudioAnalyzer` produces 8 band values but they come as 4 identical pairs (0=1, 2=3, 4=5, 6=7). The comment in `AudioAnalyzer.kt` acknowledges this — it's amplitude-envelope windowing, not real FFT. Deferred.
- Anchor layer visual quality not eyes-on verified before commit. Emulator screenshots show the polar structure working; beat-pulse timing requires live device to evaluate. Flagged in commit message; tuning deferred.

### Learnings

- `@Volatile` is necessary but not sufficient for cross-thread GL state — it guarantees visibility, not atomicity. Fine for float/reference writes that are inherently atomic on JVM; would need locks for compound state.
- `DisposableEffect` with a lifecycle observer won't fire for states already past — if the composable enters during `RESUMED`, `ON_RESUME` never fires. Manual kick required on first composition.
- Polar coordinate shaders sidestep an entire class of sampling-compatibility bugs. If the output geometry is radial, compute it radially from the start — don't render cartesian then fold.
- GLSL ES 3.0 supports non-constant array indexing in uniform arrays. This matters for the ring buffer lookup (`u_band_ring[tick * NUM_BANDS + bandIdx]`) — wasn't guaranteed in ES 2.0.
- `smoothstep(a, b, x)` requires `a < b`. Verified per spec before using it for dot glow falloff.

### Architecture state

- Compose path: Wave (waveform + cursor), Echo (kaleido + warpfield + strings)
- GL path: Warp (procedural dot grid), Drift (polar EQ mandala)
- Echo is the only mode still on Compose Canvas; everything else is GL or static Canvas
- `AudioFile` / `AudioSnapshot` / `snapshotAt()` are shared between both paths — single source of truth for audio state

### Next candidates

- Eyes-on the anchor layer on real hardware; tune brightness or ghost alpha if anchor reads as invisible
- Fix `AudioAnalyzer` 4-pairs-in-8-slots — needs real FFT or at least more distinct window sizes
- Port Echo to GL (would unify everything on the GL renderer and open the door to FBO + kaleido done correctly on a visualizer that actually has cartesian structure worth preserving)
- MP4 export — the stated goal from day one; the frame-by-frame GL pipeline is now in place to support it
- Swipe-gesture visualizer picker — toggle row will overflow when slice 6+ adds more modes
- Design third equalizer or Drift variant; anchor layer needs its own tuning slice

### Standing rules (updated)

- This is offline, not real-time. Never add `RECORD_AUDIO` or streaming audio capture.
- Every shipped Android visualizer must have a paired feature/UI
- Logcat green is necessary but not sufficient — always eyes-on a new visualizer before committing
- One platform first (Android), evaluate iOS after MVP
- Pace: ~1 hour per session, 4-5 sessions per week

---

## Naming decision (May 4, 2026, end of day)

- Local folder stays as `MyFistApp` (typo kept; Android Studio project structure references it)
- GitHub repo published as `abstrakt-engine`
- Framing: this is the rendering engine for the Abstrakt visualizer family
- Future: visualizers plug into the engine; UI shell wraps the engine
- Sister project: [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline)
