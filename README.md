# abstrakt-engine

A native Android kaleidoscope visualizer that wraps 3D geometry in audio-reactive painter textures, folds it into mandalas in real time, and exports the result as H.264 video. Part of a trilogy with [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python, the original) and [onojk/abstrakt-deck](https://github.com/onojk/abstrakt-deck) (Rust desktop).

**🎬 Coming to Google Play soon.** ⭐ Star this repo to be notified when it ships.

---

## See it in motion

Two demo videos showing the visualizer running live on a Galaxy S25, with original Suno-generated music:

- [**Nimbus Window**](https://www.youtube.com/watch?v=Mv3p7AFTOwg) — 6:18, ambient
- [**Pressure Becomes Groove**](https://www.youtube.com/watch?v=xTa7JNNjmf0) — 4:11, beat-driven

Every frame is the actual app responding to audio in real time. No post-processing.

---

## What it does

Pick an audio file (or tap the mic), choose a skin, and watch a rotating 3D shape — cylinder, sphere, more coming — painted with shader-generated color or a photo texture you supply. A configurable kaleidoscope fold wraps the shape output into a radially symmetric mandala. On every detected beat the shape shakes, ribbons collapse, and the whole thing pulses. When you like what you see, export it as an MP4 with the audio track already muxed in.

---

## Visualizer

- **3D geometry** — cylinder and sphere, each with its own tilt axis, rotation speed, and kaleido zoom factor tuned so they fill the frame consistently
- **Painter textures** — a 4096×256 FBO painted stripe-by-stripe per revolution; painters include hue stripe, audio paint, print head, static image, and skin photo
- **Audio-reactive** — beat-driven shake on X/Y axes, four-ribbon collapse animation driven by bass/mid/treble/overall; beat sensitivity tunable
- **Live mic** — tap the mic button to use the microphone instead of a file; pauses playback automatically, resumes on stop
- **Skin modes** — 5 built-in skins + up to 10 user-created mosaic skins; swipe left/right to navigate

**User skin pipeline:** capture a mosaic from any moment of the visualizer → preview the 8×8 grid → keep or try again. Each captured mosaic becomes a reusable audio-reactive skin.

---

## Kaleido Instrument

All settings live in a bottom sheet (⚙ top-right). Everything persists in DataStore and survives rotation.

- **Fold count** — 2 to 24; labeled hints (Mirror, Cross, Hex, Octagonal feel, Clock-face…)
- **4-fold orientation** — Diamond or Square rotation lock for cross symmetry
- **Frame shape** — None, Circle, Square, Rounded, Hexagon, Octagon, Star (SDF-rendered, anti-aliased)
- **Frame color** — 3-stage picker: hue/saturation wheel → lightness → alpha, with live preview
- **Zoom multiplier** — 0.5×–1.5× applied on top of per-shape defaults; 150% = more zoomed in, 50% = more zoomed out; Reset snaps to 1.0×
- **Beat reactivity** — master sensitivity knob (0–100%) scales all beat-driven animations uniformly
- **Semi-transparent sheet** — visualizer stays visible behind the settings panel so you can see changes live while dragging sliders

---

## MP4 Export

Tap ↓ → Export Wizard. Renders offline at the same framerate as the live view, then muxes your audio track.

- **Resolutions** — 720p, 1080p, 4K
- **Codec** — H.264 (High profile, hardware encoder) + AAC 192 kbps
- **Audio** — any format the device can decode (AAC, MP3, WAV, FLAC); decode → reencode to AAC
- **Bitrates** — 8 / 16 / 80 Mbps tuned for kaleidoscope content; VBR, short I-frame interval
- **Quality** — pre-warmup pass eliminates black frames at the start
- **Rotation-safe** — export continues through screen rotation
- **Output** — MediaStore registration; file appears in the system Gallery immediately
- **Speed** — hardware H.264 runs 4K at 4–5× real-time on Galaxy S25

Kaleido settings (fold count, frame shape, zoom) apply to the export. Per-export overrides available in the wizard.

---

## Privacy

The app runs entirely on your device. No internet required for visualization or export. No accounts, no analytics, no tracking, no ads.

The only outbound network traffic is anonymous crash reports via [Firebase Crashlytics](https://firebase.google.com/products/crashlytics), which you can disable at any time in Settings → Privacy. Crash reports include stack trace, device model, OS version, and anonymous installation ID — no user content, no audio, no skins, no exports.

Full policy: [onojk.github.io/abstrakt-engine/privacy.html](https://onojk.github.io/abstrakt-engine/privacy.html)

---

## Tech

|  |  |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Rendering | OpenGL ES 3.0, GLSL ES 3.0 |
| Persistence | DataStore Preferences |
| Video | MediaCodec + MediaMuxer |
| Crash reporting | Firebase Crashlytics (opt-out) |
| Target | minSdk 33, tested on Galaxy S25 (120 Hz) |

---

## Built with Claude Code

This project is a collaboration between Jonathan Kendall and [Claude Code](https://claude.ai/code) (Anthropic). Prompts drive each feature slice; Claude writes the code; Jonathan tests on-device and approves. Claude is credited as co-author in every commit.

See also the [vibecoding lessons post on r/VibeCodingAIHub](https://www.reddit.com/r/VibeCodingAIHub/) describing what worked and what didn't across all three abstrakt projects.

---

## License

MIT. See [LICENSE](LICENSE).
