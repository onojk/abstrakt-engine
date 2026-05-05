# abstrakt-engine

Native Android rendering engine for the Abstrakt audio-reactive visualizer family.
Sister project to [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline).

> **Week 1, Day 1.** This is a learning project in active development — not a finished product.
> The rendering engine and audio pipeline are planned; the first visualizer (`RippleField`) is running on a Pixel 8 emulator.

---

## What this is

The Python `abstrakt` project generates audio-reactive music videos as offline renders on a desktop. This project brings the same visualizer aesthetic to Android as a real-time, interactive application.

The goal is a native Android engine that:
- reads audio from microphone or file in real time
- runs FFT analysis per frame to extract bass, mid, treble, onset, and energy
- feeds those values into Compose Canvas visualizers
- applies kaleidoscope symmetry transforms on-device

The first visualizer (`RippleField`) is tap-driven rather than audio-driven — it exists to prove the Canvas/Compose animation loop works before wiring in the audio pipeline.

---

## Current state (Week 1)

**Working:**
- Jetpack Compose project running on emulator (Pixel 8, API 35)
- `RippleField` visualizer: tap anywhere → animated concentric rings, random hue per tap, slow hue-cycling background
- Git initialized, first commit locked in

**Not yet built:**
- Audio pipeline (mic input, FFT analysis, beat detection)
- Visualizer picker UI
- Any audio-reactive visualizer
- The engine abstraction layer

---

## Planned architecture

```
abstrakt-engine/
├── engine/          # Rendering primitives: Canvas helpers, animation loop, frame timing
├── visualizers/     # Individual visualizers (each a @Composable that takes AudioFeatures)
│   └── RippleField  # Tap-driven prototype (currently in MainActivity.kt)
├── audio/           # Mic input, WAV reader, FFT, onset detection, AudioFeatures data class
└── ui/              # Compose shell: visualizer picker, settings, permission handling
```

`AudioFeatures` is the contract between the audio pipeline and the visualizers:

```kotlin
data class AudioFeatures(
    val bass: Float,    // 0..1
    val mid: Float,     // 0..1
    val treble: Float,  // 0..1
    val onset: Boolean,
    val energy: Float,
)
```

Each visualizer is a `@Composable` that accepts `AudioFeatures` and renders to `Canvas`. The engine handles timing; visualizers handle drawing.

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Build | Gradle Kotlin DSL |
| Min SDK | API 24 (Android 7.0) |
| Target SDK | API 36 |
| IDE | Android Studio Panda 4 |
| Emulator | Pixel 8, API 35, Google Play, x86_64 |

---

## Roadmap

**Week 2:** Kotlin Koans — learn the language before touching Android code again.
Sections: Introduction → Conventions → Collections → Properties → Builders.

**Week 3-4:** Audio pipeline — mic input via `AudioRecord`, per-frame FFT (numpy-equivalent in Kotlin), `AudioFeatures` data class.

**Week 5-6:** First audio-reactive visualizer — port `warpfield` or `kaleido_qbist` aesthetic to Compose Canvas.

**Week 7+:** Visualizer picker, kaleidoscope symmetry pass, file input (WAV/MP3).

**Standing rule:** every shipped Android visualizer must have a paired feature/UI. No visualizer ships without a way to trigger it from the UI.

---

## Local setup

```bash
# Clone
git clone https://github.com/onojk/abstrakt-engine

# Open in Android Studio: File → Open → abstrakt-engine/
# (Local folder is MyFistApp; Android Studio project structure uses that name)

# Run on emulator
# Build → Run 'app' (or Shift+F10)
```

KVM acceleration required for the emulator:
```bash
sudo apt-get install qemu-kvm
kvm-ok  # should print "KVM acceleration can be used"
```

---

## Relationship to onojk/abstrakt

| | [onojk/abstrakt](https://github.com/onojk/abstrakt) | abstrakt-engine |
|---|---|---|
| Platform | Linux desktop | Android |
| Runtime | Python + pygame | Kotlin + Compose |
| Audio | WAV file, offline | Mic + file, real-time |
| Output | MP4 file | Live screen |
| Kaleido | frei0r (ffmpeg) | Planned on-device |
| Status | Production | Week 1 |
