# abstrakt-engine

A native Android kaleidoscope visualizer that wraps 3D geometry in audio-reactive painter textures, folds it into mandalas in real time, and exports the result as H.264 video. Sister project to [onojk/abstrakt](https://github.com/onojk/abstrakt) — same aesthetic, native Android, 120 Hz.

---

## What it does

Pick an audio file (or tap the mic), choose a skin, and watch a rotating 3D shape — cylinder, sphere, more coming — painted with shader-generated color or a photo texture you supply. A configurable kaleidoscope fold wraps the shape output into a radially symmetric mandala. On every detected beat the shape shakes, ribbons collapse, and the whole thing pulses. When you like what you see, export it as an MP4 with the audio track already muxed in.

---

## Visualizer

- **3D geometry** — cylinder and sphere, each with its own tilt axis, rotation speed, and kaleido zoom factor tuned so they fill the frame consistently
- **Painter textures** — a 4096×256 FBO painted stripe-by-stripe per revolution; painters include hue stripe, audio paint, print head, static image, and skin photo
- **Audio-reactive** — beat-driven shake on X/Y axes, four-ribbon collapse animation driven by bass/mid/treble/overall; beat sensitivity tunable per skin
- **Live mic** — tap the mic button to use the microphone instead of a file; pauses playback automatically, resumes on stop
- **Skin modes** — 5 built-in skins + up to 40 user-uploaded photo slots; swipe left/right to navigate, long-press to replace or clear
- **Skin Shuffle** — tap the ⇌ button next to the skin picker dots to jump to a random skin from your available set

**User skin pipeline:** pick from gallery or camera → 16:1 crop screen with live preview → validation (size, resolution, aspect) → mirror-seam JPEG saved to `filesDir`. Texture updates instantly; no stripe-by-stripe repaint delay.

---

## Kaleido Instrument

All settings live in a bottom sheet (⚙ top-right). Everything persists in DataStore and survives rotation.

- **Fold count** — 2 to 24; labeled hints (Mirror, Cross, Hex, Octagonal feel, Clock-face…)
- **4-fold orientation** — Diamond or Square rotation lock for cross symmetry
- **Frame shape** — None, Circle, Square, Rounded, Hexagon, Octagon, Star (SDF-rendered, anti-aliased)
- **Frame color** — 3-stage picker: hue/saturation wheel → lightness → alpha, with live preview
- **Zoom multiplier** — 0.5×–1.5× applied on top of per-shape defaults; 150% = more zoomed in, 50% = more zoomed out; Reset snaps to 1.0×
- **Contrast** — 0–2.0 post-process pass; values above 1.0 crunch blacks and clip whites
- **Saturation** — 0–2.0; above 1.0 oversaturates, 0 = monochrome
- **Contrast passes** — 1–6 iterations of the contrast curve applied in sequence; higher counts produce a posterization effect
- **Distortion Plus** — spherical warp injected before the kaleido fold; three independent axes (yaw, pitch, roll) bend the geometry into asymmetric forms without breaking the fold symmetry
- **Semi-transparent sheet** — visualizer stays visible behind the settings panel so you can see changes live while dragging sliders

---

## Immersive mode

On launch the visualizer fills the entire screen with zero chrome — no title, no buttons, no skin picker, no system bars. Tap the screen to reveal the controls; they slide down from the top with a dark scrim and auto-hide after 3 seconds of no interaction. Works in portrait and landscape. On first launch a tooltip explains the gesture once, then never shows again.

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

Kaleido settings (fold count, frame shape, zoom, contrast, saturation, Distortion Plus) apply to the export. Per-export overrides available in the wizard.

---

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Rendering | OpenGL ES 3.0, GLSL ES 3.0 |
| Persistence | DataStore Preferences |
| Video | MediaCodec + MediaMuxer |
| Target | API 34+, tested on Galaxy S25 (120 Hz) |
| Repo | [github.com/onojk/abstrakt-engine](https://github.com/onojk/abstrakt-engine) |

---

## Built with Claude Code

This project is a collaboration between Jonathan Kendall and [Claude Code](https://claude.ai/code) (Anthropic). Prompts drive each feature slice; Claude writes the code; Jonathan tests on-device and approves. Claude is credited as co-author in every commit. The visualizer output shows up on YouTube — search for abstrakt to find it.

---

## License

MIT. See [LICENSE](LICENSE).
