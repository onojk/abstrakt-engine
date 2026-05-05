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

## Naming decision (May 4, 2026, end of day)

- Local folder stays as `MyFistApp` (typo kept; Android Studio project structure references it)
- GitHub repo published as `abstrakt-engine`
- Framing: this is the rendering engine for the Abstrakt visualizer family
- Future: visualizers plug into the engine; UI shell wraps the engine
- Sister project: [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline)
