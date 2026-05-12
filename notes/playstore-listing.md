# abstrakt engine — Play Store Listing Draft

Copy-paste source for Google Play Console submission.
Keep this file up-to-date with app changes before each release.

Last updated: 2026-05-11

---

## App name (max 30 chars)

abstrakt engine

---

## Short description (max 80 chars)

Music visualizer · mandala kaleidoscope · export as MP4

---

## Full description (max 4000 chars)

Turn any song into a living mandala and export it as video.

abstrakt engine is a real-time audio visualizer that wraps 3D geometry in audio-reactive textures, folds them into kaleidoscope mandalas, and lets you capture the result as an H.264 MP4 with your audio already muxed in — hardware-accelerated, up to 4K.

✦ HOW IT WORKS ✦

→ Pick an audio file or tap the mic for live input
→ Swipe through 5 built-in skins + up to 10 skins you create yourself
→ Dial in the fold count, frame shape, zoom, and beat sensitivity
→ When you like what you see, tap Export to capture it as MP4

Every frame is the live app responding to audio in real time. No post-processing, no canned animations.

✦ VISUALIZER ✦

→ 3D shapes (cylinder, sphere) with configurable tilt and rotation speed
→ Painter textures: hue stripe, audio paint, print head, and your own photos
→ Beat-driven shake on X and Y axes with tunable sensitivity
→ Four-ribbon collapse animation driven by bass / mid / treble / overall level
→ Kaleidoscope fold: 2 to 24 folds with labeled presets (Mirror, Hex, Star…)
→ 4-fold Diamond / Square orientation lock for cross symmetry
→ Frame shapes: Circle, Square, Rounded, Hexagon, Octagon, Star (SDF anti-aliased)
→ Frame color: 3-stage hue/saturation/lightness/alpha picker with live preview
→ Zoom multiplier 0.5×–1.5× on top of per-shape defaults

✦ USER SKIN PIPELINE ✦

Capture a mosaic from any moment of the live visualizer → preview the 8×8 tile grid → keep or try again. Each captured mosaic becomes a full audio-reactive skin you can swipe to at any time. Up to 10 custom skins per device.

✦ MP4 EXPORT ✦

→ Resolutions: 720p, 1080p, 4K
→ Codec: H.264 (High profile, hardware-accelerated) + AAC 192 kbps
→ Bitrates: 8 / 16 / 80 Mbps tuned for kaleidoscope content
→ Audio: any format your device can decode (AAC, MP3, WAV, FLAC)
→ Pre-warmup pass eliminates black frames at the start
→ Export continues through screen rotation
→ Files appear in the system Gallery immediately via MediaStore

On a Galaxy S25, 4K export runs at 4–5× real-time speed.

✦ FAIR MONETIZATION ✦

→ Free version supported by ads (banner + post-export interstitial)
→ One-time $2.99 purchase removes all ads forever
→ No subscriptions, no recurring charges, no upsells
→ Anonymous crash reports only (opt-out in settings)

✦ STILL PRIVATE BY DESIGN ✦

→ Runs entirely on your device (no internet required for the visualizer or export)
→ No accounts, no sign-in, no profile
→ No tracking beyond Crashlytics + AdMob (both standard SDKs)
→ Your audio, photos, skins, and exports never leave your device

---

## Category

Music & Audio

## Tags (up to 5)

music visualizer, kaleidoscope, audio visualizer, MP4 export, mandala

---

## Content rating

Everyone (ESRB) / PEGI 3

---

## Privacy policy URL

https://onojk.github.io/abstrakt-engine/privacy.html

---

## Screenshots needed (before submission)

- [ ] Main visualizer (portrait, 1080p device)
- [ ] Settings sheet open showing fold / frame controls
- [ ] Custom skin picker grid
- [ ] Export wizard step (resolution selector)
- [ ] Completed export with "Saved to Gallery" confirmation
- [ ] "Support the developer" settings section (shows $2.99 button)

Recommended: 3–6 phone screenshots (1080×1920 or 1080×2340), plus a 1024×500 feature graphic.

---

## Release notes template (for each version)

    What's new in X.Y:
    • [bullet 1]
    • [bullet 2]

---

## Internal notes

- Package: com.onojk.abstrakt
- AdMob App ID: ca-app-pub-7313844831247942~8760882330
- IAP product ID: remove_ads_pro (one-time, $2.99)
- Min SDK: 33 (Android 13)
- Target SDK: 36
- Test device: Galaxy S25 (SM-S931U)
