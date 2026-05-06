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

## Week 1 — Day 4 (May 5, 2026 — late evening session)

### Overview

Session 3 of May 5. Day 3 shipped the GL renderer, Drift, and a working audio-reactive visualizer stack. Day 4 went deeper: built the 3D Cyclone visualizer (slice 7a), then immediately refactored it into a painter chassis (slice 7b) — an architecture that reframes how every future visualizer in this engine gets built.

Session started at 3pm wake-up. "Late evening" by the clock; subjectively a normal working window. Ended with two commits, one revert, and an architectural insight that changes the engine's direction.

### Done — Slice 7a: Cyclone scaffold + painter mechanic

3D rotating cylinder with a rolling painter FBO. Two-pass render: first pass runs a painter fragment shader into a 1024×256 framebuffer at a scissored stripe at the "rear angle" of the cylinder (the face pointing directly away from the camera, guaranteed hidden by back-face culling). Second pass renders the cylinder geometry sampling from that texture.

Key engineering:
- `CylinderGeometry.kt`: 64-segment triangle strip (130 vertices), interleaved [x,y,z,u,v], seam duplicated at u=0/u=1 with `GL_REPEAT` on S axis for seamless wrap
- Camera at (0,0,3), perspective 45°, model rotates Y-axis at 2π/30s (one revolution per 30s)
- `setEGLConfigChooser(8,8,8,8,16,0)`: 16-bit depth buffer allocated at context creation, not at `onSurfaceChanged`
- Rear angle: `cycloneAngleRad + 3π/2`, not `+ π` (which is the side tangent, not the rear). Wrong value paints the visible face.
- Scissor stripe is 16px wide at the rear column. When the stripe crosses x=1024, two separate scissored draws handle the wrap.
- One-time black clear of the FBO in `onSurfaceCreated`. Never cleared in `onDrawFrame` — content persists and accumulates; old paint decays naturally as the cylinder overwrites it after ~30s.
- FBO texture: `GL_REPEAT` (S axis), `GL_CLAMP_TO_EDGE` (T axis). Wraps horizontally for seamless cylinder seam; clamps vertically to keep top/bottom clean.

Four timed screenshots (t=0, t=10, t=30, t=60s) confirmed rolling and discard cycle working. Committed as `ebf7a74`.

### Done — Slice 7b: Painter chassis

Immediately after shipping 7a, the painter was refactored into a swappable component system.

- `Painter.kt`: `enum class Painter { HUE_STRIPE, AUDIO_PAINT }` plus `PainterEntry` (painter, vert, frag) and `allPainters()` registry. Adding a future painter = one fragment shader file + one entry in `allPainters()`.
- `PAINTER_HUESTRIPE_FRAG`: renamed from 7a's `PAINTER_FRAG`. Time-driven HSV cycle, vertical brightness gradient, identity-identical behavior to 7a.
- `PAINTER_AUDIOPAINT_FRAG`: new. `v_uv.x` maps to band index (0–7); hue from band index + slow time drift; brightness from `u_bands[i]` + `u_beat_decay`; saturation at 0.80 base + band amplitude. 8 vertical columns on the cylinder surface, each independent.
- `AbstraktRenderer`: `painterPrograms: MutableMap<Painter, ShaderProgram>` replaces single `painterProgram`. All painters compiled at `onSurfaceCreated`. Per-frame, `audioUniforms.activePainter` selects which program paints.
- Full painter contract pushed every frame to every painter: `u_time`, `u_peak`, `u_beat`, `u_beat_decay`, `u_bands[8]`, `u_playback_fraction`. Unused uniforms silently no-op at `loc=-1`. No per-painter branching in the renderer.
- `AudioUniforms.activePainter`: `@Volatile var activePainter: Painter = Painter.HUE_STRIPE`. Main thread writes on button tap; GL thread reads each frame.
- `MainActivity`: painter selection row rendered only when `currentViz == Viz.GL_CYCLONE`. Cyan highlight tracks active painter. Hidden for all other modes.

Verified with audio playing: 5–7 distinct columns visibly brightening and shifting across the cylinder front face when audio drives `u_bands`. Committed as `bae17ba`.

### Notable moments / debugging

**Eyes-on discipline failure pattern — three instances.**

This is the behavioral pattern that needs honest documentation because it happened three separate times tonight.

1. After Drift's anchor layer (Day 3 close): the GL pipeline was healthy, ring buffer data differentiated, no errors. The assistant moved toward summary, the user typed "commit," the screenshots got captured anyway only after explicit prompting from one party or the other. Anchor layer was shipped visually unverified, acknowledged by note in the commit message. Emulator screenshots confirmed structure; beat-pulse timing remains untested under live conditions.

2. After Cyclone 7a: screenshots were captured but the assistant moved toward summary rather than presenting them for explicit review. The four timed screenshots were the actual confirmation the rolling mechanic worked — but this required user prompting to execute.

3. After AudioPaint 7b: the assistant described the Audio painter screenshot as showing "8 banded hue columns" with reactivity. User caught this and required actual eyes-on with audio playing before accepting. The confirmed result — 5–7 visible columns shifting with audio content — could not have been determined from a description. It required the user watching the device with audio running.

Day 3's rule was "logcat green ≠ visual works." Day 4 adds: **Claude's screenshot description ≠ visual works either.** A description of what should be visible is not confirmation. The only valid confirmation for a new visualizer is the user looking at it. The only valid confirmation for an audio-reactive feature is the user looking at it with audio playing.

**Painter contract generalization.** The initial 7b plan had painters scissor-locked to "paint only at rear angle." Discussion of PrintHead and Scanline ideas revealed this was too restrictive — those patterns need to paint across arbitrary x positions per frame. The contract was generalized: painter receives the full uniform set and handles its own region-of-interest logic. The scissor at the renderer level remains as a guard but is not the painter's only degree of freedom. This required no refactor — a broader contract and intent documentation were sufficient.

**Drift v2 chartreuse + violet experiment.** Mid-session, Drift's multi-layer ghost stack hit a visual ceiling — translucent layers with scrambled hue assignments average toward gray mush as layers stack. A 2-layer redesign (chartreuse base + violet accent, high saturation, no random hue) was implemented and screencapped. Result was visually clean — no mush — but geometrically too simple; the form didn't change with audio content. Shelved. Shaders.kt reverted via git checkout to last committed state. Lesson: color theory problems and geometric complexity are separate problems. Solving the former doesn't address the latter.

### Architectural insight: painter chassis as engine foundation

The realization from tonight: **every future visualizer in this engine can be driven through the cylinder chassis.**

Cyclone started as "one visualizer." It is now something different. The cylinder is a display surface. Painters are the visualizers. The cylinder (geometry, rotation, projection, FBO, two-pass renderer) stays fixed. Only the painter changes. As stated in the session: "all future visualizers can be driven through this can driver system."

What this unlocks:
- **PrintHead**: marks down columns at specific x positions on beat timing. Typewriter rolling across the surface.
- **Scanline**: horizontal sweep across the full texture height, brightness modulated per-band. Oscilloscope on a can.
- **BeatStrobe**: full-texture white flash on beat, decays to black. No hue, no columns. Pure rhythm.
- **SpectrumPainter**: spectral waterfall, bands stacked as color rows scrolling upward.
- **MP4Painter**: `SurfaceTexture` + `GL_TEXTURE_EXTERNAL_OES`. Android renders a playing video into the painter FBO; the cylinder displays it. This connects the Android engine directly to the desktop Abstrakt project's MP4 output — a video rendered in Python runs on the cylinder surface on Android. One entry in `allPainters()`. This bridges two projects that have never shared code. Documented and deferred to slice 8+.

The chassis shipped with 2 painters. The architecture supports unlimited painters without modifying `AbstraktRenderer`.

### Known issues (deferred)

- `AudioAnalyzer` 4-pair-bands bug: still unfixed. Bands 0=1, 2=3, 4=5, 6=7 are identical pairs. AudioPaint's 8-column visual is partially duplicated as a result. Requires real FFT or more distinct windowing to fix.
- Toggle row UI broken at 5 buttons: "Cyclone" text wraps vertically due to overflow. Painter row below it compounds the top-of-screen crowding. Swipe-gesture visualizer picker is the right replacement.
- Drift anchor layer unconfirmed on real hardware under audio. Polar structure renders correctly in emulator; beat-pulse brightness and timing require live device with audio.
- AudioPaint at zero-audio is very dim (val ≈ 0.09–0.25 with no file loaded). Correct behavior, but reads as "broken" before a file is picked.

### Learnings

- **Rear-angle math**: for Y-rotation by α, the rear face is at α + 3π/2, not α + π. α + π is the side tangent (z′ = 0), not the rear (min z′). Wrong formula paints the visible face.
- **FBO clear discipline**: `glClear` the painter FBO exactly once in `onSurfaceCreated`. Never in `onDrawFrame`. Content must accumulate; clearing on every frame produces an empty cylinder.
- **Scissor wrap at texture boundary**: when a stripe crosses x=1024, one scissor call is insufficient — it clips at the boundary. Two draws required: one for the right fragment, one for the wrapped-left fragment.
- **`@Volatile` for painter enum selection**: sufficient because enum reference reads are atomic on JVM. Compound-state changes (painter + associated parameter reset together) would require a lock.
- **`hsv2rgb` is a compile-unit problem**: GLSL ES has no `#include`. Every painter shader defines its own copy. This is correct; DRY doesn't apply to separate compile units.
- **Eyes-on with audio playing is the only valid confirmation of audio-reactive behavior.** Screenshots without audio confirm geometry. Screenshots with audio confirm reactivity. Both are required before commit.

### Next candidates

- Add 2–3 more painters: PrintHead, Scanline, BeatStrobe — each estimated ~30 min, single shader file + one `allPainters()` entry
- MP4Painter: `SurfaceTexture` bridge to desktop Abstrakt MP4 output — larger slice, probably 8+
- Fix AudioAnalyzer 4-pair-bands bug: real FFT or more distinct windowing, required before AudioPaint's columns mean anything
- Swipe-gesture visualizer picker: toggle row is visibly broken at 5 buttons, swipe replaces it entirely and scales to unlimited modes
- Port Echo to GL: unifies the rendering path; enables proper kaleido FBO + cartesian source that genuinely benefits from two-pass
- MP4 export: original stated goal from Day 1; frame-by-frame GL pipeline is now in place to support it

### Standing rules (updated)

- This is offline, not real-time. Never add `RECORD_AUDIO` or streaming audio capture.
- Every shipped Android visualizer must have a paired feature/UI.
- Logcat green is necessary but not sufficient — always eyes-on a new visualizer before committing.
- **Claude's screenshot description is not confirmation. The only valid confirmation is the user looking at it. For audio-reactive features, audio must be playing.**
- One platform first (Android), evaluate iOS after MVP.
- Pace varies. Marathon sessions are fine when intentional. Three sessions in one calendar day is allowed; forcing energy that isn't there is not.

---

## Naming decision (May 4, 2026, end of day)

- Local folder stays as `MyFistApp` (typo kept; Android Studio project structure references it)
- GitHub repo published as `abstrakt-engine`
- Framing: this is the rendering engine for the Abstrakt visualizer family
- Future: visualizers plug into the engine; UI shell wraps the engine
- Sister project: [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline)
