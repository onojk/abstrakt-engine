# abstrakt-engine

Native Android rendering engine for the Abstrakt audio-reactive visualizer family.
Sister project to [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline).

---

> **Week 2, Day 1 — May 6, 2026.** Learning project in active development. Not a finished product.
> The core visualizer is working and iterated; the skin system is complete; the audio pipeline is
> offline-only (file picker, no microphone). Development log is in `notes/journal.md`.

---

## What works right now

**Cyclone visualizer**

A rotating 3D cylinder whose surface is painted by a fragment shader writing into a 4096×256 FBO,
which the cylinder mesh samples as a texture. Each frame, one 16-pixel stripe at the hidden rear
face is repainted — over one revolution (~30 seconds) the entire surface refreshes. On top of the
cylinder, a 12-fold kaleidoscope fold composites as a circular mandala overlay (white frame outside,
kaleido inside).

- Beat-driven cylinder shake: on every detected beat, the cylinder translates on both X and Y axes
  (~0.35 of its height, ~7 Hz, 200ms decay). The kaleido frame stays fixed; the shake is readable
  because the cylinder moves inside it.
- Ribbon overlay: four horizontal ribbons driven by bass, mid, treble, and overall band energy.
  On beat, ribbons collapse toward center and trail back out over ~2 seconds.

**Six visualizer modes**

Swipe left/right to navigate. A dots row shows position; an empty-ring dot marks the Add Skin slot.

- Cyclone: procedural Hue Stripe, Audio Paint, Print Head, and Image painter options. Each is a
  separate fragment shader; the cylinder chassis and FBO are shared.
- Skin 1–5: five built-in image-wrapped variants. Each has its own kaleido fold count (6–16),
  ribbon color, and beat threshold tuned for that skin's content.
- User Slots: up to 40 photo-based skins. The Add Skin entry appears at the end of the carousel
  until all 40 slots are filled.

**User skin system**

Photos come from the system photo picker (gallery, no permissions required on API 33+) or camera
capture. After picking, a crop screen opens:

- Source photo displays at natural aspect ratio, capped at 50% of screen height.
- A cyan rectangle shows the exact 16:1 strip that will be saved, updating live as you drag the
  slider.
- A 16:1 preview strip below the source photo shows the cropped content at full resolution.
- Tapping "Use This" processes the crop: decodes at up to 8192px long edge, extracts the 16:1
  strip, resizes to 256px height, mirrors horizontally (original | flipped) for seamless GL_REPEAT
  wrapping at the cylinder seam, compresses to JPEG at 92 quality, and saves to `filesDir/user_skins/`.

Saved skins persist across restarts via a JSON registry. Long-pressing a user skin opens a sheet
with options to replace the photo or clear the slot. Clearing evicts the GL texture and collapses
the carousel.

On switching to any skin mode (built-in or user), the full skin texture is blitted into the painter
FBO in one draw call — the cylinder shows the new skin immediately rather than painting in
stripe-by-stripe over 30 seconds.

**Photo validation**

Photos are rejected before the crop screen opens if they fail any of these checks (cheapest first):

- File size: maximum 50 MB. Checked via `openAssetFileDescriptor` — no pixel decode.
- Resolution minimum: 1024×256 px. Checked via `inJustDecodeBounds` — header only, sub-millisecond.
- Resolution maximum: 8000×6000 px.
- Aspect ratio: must be able to yield a valid 16:1 strip after crop.

On rejection, a toast explains the specific reason in plain language. If a camera temp file was
created, it is deleted.

**Audio pipeline**

Offline only — no microphone. Pick any audio file the system can decode (MP3, WAV, M4A, etc.).
The app decodes the file to PCM off the main thread, computes RMS amplitude in ~50ms windows,
extracts 8 pseudo-bands via multiple window sizes, and detects beats by comparing the current
window against a 60-frame moving average. Beat detection sensitivity is configurable per skin mode.

The 8 bands are currently computed from amplitude-envelope windowing, not a true FFT. Bands 0–1,
2–3, 4–5, 6–7 are effectively pairs. This is a known limitation; real FFT is on the list.

---

## Architecture

```
Audio file
    └─ AudioAnalyzer: PCM decode → 8-band envelope → beat detection
           └─ AudioUniforms (@Volatile, main thread write / GL thread read)
                  └─ onDrawFrame (GL thread, RENDERMODE_CONTINUOUSLY)
                         ├─ Pass 0: Ribbon scratch — update 4096×256 ribbon FBO
                         ├─ Pass 1: Painter — scissored 16px stripe into painter FBO (4096×256)
                         │          Skin painter: blit full texture on mode change (sub-ms)
                         ├─ Pass 2: Cylinder — 64-segment triangle strip samples painter FBO
                         └─ Pass 3: Kaleido — fullscreen quad, 12-fold radial fold, alpha-blended
```

Each painter is a standalone fragment shader registered in `allPainters()`. Adding a new painter
requires one GLSL file and one line in the registry. The cylinder chassis, FBO, and passes do not
change.

Any new visualizer concept can be expressed as a painter plugged into the same socket.

---

## What's planned

- More built-in skins from a curated source (DeviantArt, own renders)
- Per-user-skin tuning: fold count, ribbon color, beat threshold set in-app rather than hardcoded
- Distortion variants applied to skins: mirror, hue-shift, rotation
- Real FFT for the audio analyzer — 8 genuinely independent bands
- Additional visualizer concepts on the same chassis (BeatStrobe, Scanline, MP4Painter)
- MP4 export via MediaCodec surface encode
- First-launch onboarding polish

---

## Tech stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Rendering | OpenGL ES 3.0, GLSL |
| Build | Gradle Kotlin DSL |
| Min SDK | API 33 (Android 13) |
| Target SDK | API 36 |
| Camera | `ActivityResultContracts.TakePicture` + FileProvider |
| Photo picker | `ActivityResultContracts.PickVisualMedia` (no permissions on API 33+) |
| Emulator | Pixel 8, API 35, Google Play, x86_64 |

---

## How development works

This is a collaborative project between Jonathan Kendall and Claude Code (Anthropic). Each slice
of work is driven by a focused prompt; Claude writes the code and the human reviews, tests on the
emulator or device, and approves before committing. The commit history reads as a development log
— slice numbers in commit messages correspond to journal entries. Claude is credited as
co-author in commit messages.

`notes/journal.md` contains session-by-session write-ups including what was built, what was
learned, debugging false starts, and standing rules accumulated over the project.

---

## Building and running

Requires Android Studio (any recent version) and a Pixel 8 emulator at API 35, or a physical
device at API 33+.

```bash
git clone https://github.com/onojk/abstrakt-engine
# Open in Android Studio: File → Open → abstrakt-engine/
# Run on emulator: Build → Run 'app'  (or Shift+F10)
```

To install a pre-built APK directly:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.myfistapp/.MainActivity
```

Once running: tap "Pick audio file" to load any audio file the system can decode, then swipe
between modes. User skins are added via the "+" button in the top-right corner.

KVM acceleration is required for the emulator on Linux:

```bash
sudo apt-get install qemu-kvm
kvm-ok   # should print "KVM acceleration can be used"
```

---

## Repository layout

```
abstrakt-engine/
├── app/
│   ├── src/main/
│   │   ├── assets/                  # Bundled images (cyclone_image.jpg)
│   │   ├── java/com/example/myfistapp/
│   │   │   ├── audio/               # PCM decode, 8-band analyzer, beat detection
│   │   │   ├── gl/                  # OpenGL renderer, shaders, cylinder geometry, painters
│   │   │   ├── AbstraktApp.kt       # Application subclass; registry init, temp-file cleanup
│   │   │   ├── CropSliderScreen.kt  # 16:1 crop UI with live preview
│   │   │   ├── MainActivity.kt      # Compose UI shell, mode carousel, skin management
│   │   │   ├── SkinProcessor.kt     # Crop, resize, mirror, JPEG save pipeline
│   │   │   ├── SkinSlotRegistry.kt  # 40-slot singleton, JSON persistence
│   │   │   └── SkinValidation.kt    # Photo validation gates (size, resolution, aspect)
│   │   └── res/drawable/            # skin1.jpg – skin5.jpg (built-in skins)
│   └── build.gradle.kts
├── notes/
│   └── journal.md                   # Session-by-session development log
├── gradle/                          # Gradle wrapper and version catalog
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
└── README.md
```

---

## License

MIT. See [LICENSE](LICENSE).

---

## Acknowledgments

The visual aesthetic — rotating cylinder, kaleidoscope fold, audio-reactive color — derives from
[onojk/abstrakt](https://github.com/onojk/abstrakt), a Python pipeline that renders the same
ideas as offline MP4 files. This project brings them to Android in real time.

[Claude](https://anthropic.com) (Anthropic) is the AI pair-programmer credited as co-author in
commits throughout this project.
