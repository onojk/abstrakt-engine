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


## Naming decision (May 4, 2026, end of day)

- Local folder stays as `MyFistApp` (typo kept; Android Studio project structure references it)
- GitHub repo published as `abstrakt-engine`
- Framing: this is the rendering engine for the Abstrakt visualizer family
- Future: visualizers plug into the engine; UI shell wraps the engine
- Sister project: [onojk/abstrakt](https://github.com/onojk/abstrakt) (Python pipeline)
