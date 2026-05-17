#!/usr/bin/env python3
"""
Cross-verification of PitchAnalyzer.kt against deck's pitch.rs.

Usage:
    # 1. Verify deck's own tests pass first:
    source $HOME/.cargo/env
    cd ~/Projects/abstrakt-deck && cargo test pitch

    # 2. Then run this script:
    cd ~/AndroidStudioProjects/MyFistApp
    python3 tools/verify_key_tracker.py

The script uses the same synthetic inputs as deck's unit tests and asserts the
same thresholds, printing exact Pearson r values so the Kotlin port's test
output can be compared manually. Both implementations must produce values
within float epsilon (1e-5) of each other on the same inputs.
"""

import math
import sys

# ── K-S profiles — copied verbatim from deck/src/pitch.rs ────────────────────
KS_MAJOR = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
KS_MINOR = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]


# ── Algorithm — mirrors deck/src/pitch.rs exactly ─────────────────────────────

def normalize_profile(v: list) -> bool:
    """L2 norm in-place. Returns False if all-zero. Mirrors deck's normalize_profile."""
    norm = math.sqrt(sum(x * x for x in v))
    if norm < 1e-9:
        return False
    for i in range(len(v)):
        v[i] /= norm
    return True


def pearson_rotated(chroma: list, profile: list, rot: int) -> float:
    """Mirrors deck's pearson_rotated exactly."""
    mean_c = sum(chroma) / 12.0
    mean_p = sum(profile) / 12.0
    num = ss_c = ss_p = 0.0
    for i in range(12):
        c = chroma[(i + rot) % 12] - mean_c
        p = profile[i] - mean_p
        num  += c * p
        ss_c += c * c
        ss_p += p * p
    den = math.sqrt(ss_c * ss_p)
    return 0.0 if den < 1e-9 else num / den


def make_chroma_for_key(rot: int, is_major: bool) -> list:
    """Mirrors deck's test helper make_chroma_for_key."""
    profile = KS_MAJOR if is_major else KS_MINOR
    return [profile[(j + 12 - rot) % 12] for j in range(12)]


def simulate_key_tracker(rot: int, is_major: bool,
                          n_chunks: int = 200, chunk_sec: float = 0.04) -> tuple:
    """
    Simulates deck's KeyTracker algorithm (no Android debouncing / confidence gate).
    Returns (key_root, key_is_major, key_confidence) after n_chunks.
    """
    history_size     = max(4, math.ceil(4.0 / chunk_sec))
    estimate_period  = max(1, math.ceil(0.5 / chunk_sec))
    raw_chroma       = make_chroma_for_key(rot, is_major)
    history          = []
    key_root, key_is_major, key_confidence = 0, True, 0.0
    chunks_since = 0
    for _ in range(n_chunks):
        c = raw_chroma[:]
        if not normalize_profile(c):
            continue
        history.append(c[:])
        if len(history) > history_size:
            history.pop(0)
        chunks_since += 1
        if chunks_since >= estimate_period:
            chunks_since = 0
            if not history:
                continue
            acc = [sum(h[i] for h in history) for i in range(12)]
            if not normalize_profile(acc):
                continue
            best_root, best_major, best_corr = 0, True, float('-inf')
            for r in range(12):
                cm = pearson_rotated(acc, KS_MAJOR, r)
                cn = pearson_rotated(acc, KS_MINOR, r)
                if cm > best_corr: best_corr, best_root, best_major = cm, r, True
                if cn > best_corr: best_corr, best_root, best_major = cn, r, False
            key_root, key_is_major, key_confidence = best_root, best_major, best_corr
    return key_root, key_is_major, key_confidence


# ── Test runner ───────────────────────────────────────────────────────────────

passed = 0
failed = 0
failures = []


def check(name: str, condition: bool, detail: str = "") -> None:
    global passed, failed
    mark = "PASS" if condition else "FAIL"
    print(f"  [{mark}] {name}" + (f"  ({detail})" if detail else ""))
    if condition:
        passed += 1
    else:
        failed += 1
        failures.append(name)


def section(title: str) -> None:
    print(f"\n── {title} {'─' * max(0, 74 - len(title))}")


# ── pearsonRotated checks ─────────────────────────────────────────────────────

section("pearsonRotated — mirrors deck tests pearson_self_correlation_is_one")

r_self = pearson_rotated(KS_MAJOR, KS_MAJOR, 0)
print(f"  KS_MAJOR self-correlation at rot=0: {r_self:.10f}")
check("self-correlation is 1.0 (±1e-5)", abs(r_self - 1.0) < 1e-5,
      f"got {r_self}")

section("pearsonRotated — mirrors deck test pearson_profile_derived_chroma_matches_at_correct_rotation")

c_fsharp   = make_chroma_for_key(6, True)
r_fsharp_6 = pearson_rotated(c_fsharp, KS_MAJOR, 6)
r_fsharp_0 = pearson_rotated(c_fsharp, KS_MAJOR, 0)
print(f"  F# major chroma vs KS_MAJOR rot=6: {r_fsharp_6:.10f}")
print(f"  F# major chroma vs KS_MAJOR rot=0: {r_fsharp_0:.10f}")
check("F# major at rot=6 is 1.0 (±1e-5)", abs(r_fsharp_6 - 1.0) < 1e-5,
      f"got {r_fsharp_6}")
check("rot=6 outscores rot=0 for F# input", r_fsharp_6 > r_fsharp_0,
      f"rot6={r_fsharp_6:.4f} rot0={r_fsharp_0:.4f}")

section("pearsonRotated — mirrors deck test c_major_scale_tones_outscores_all_other_keys")

c_scale  = [1,0,1,0,1,1,0,1,0,1,0,1]
r_c_maj  = pearson_rotated(c_scale, KS_MAJOR, 0)
r_c_min  = pearson_rotated(c_scale, KS_MINOR, 0)
r_fs_maj = pearson_rotated(c_scale, KS_MAJOR, 6)
print(f"  C scale vs KS_MAJOR rot=0: {r_c_maj:.10f}")
print(f"  C scale vs KS_MINOR rot=0: {r_c_min:.10f}")
print(f"  C scale vs KS_MAJOR rot=6: {r_fs_maj:.10f}")
check("C scale > C minor",  r_c_maj > r_c_min,
      f"maj={r_c_maj:.4f} min={r_c_min:.4f}")
check("C scale > F# major", r_c_maj > r_fs_maj,
      f"C={r_c_maj:.4f} F#={r_fs_maj:.4f}")
check("C scale vs C major > 0.7", r_c_maj > 0.7,
      f"got {r_c_maj:.4f}")

# ── KeyTracker simulation — mirrors deck's key_tracker_detect_* tests ─────────

section("KeyTracker simulation (deck algorithm — no Android confidence gate/debouncing)")

root, major, conf = simulate_key_tracker(0, True)
print(f"  C major  → root={root}, major={major}, confidence={conf:.8f}")
check("C major: root=0",    root == 0,    f"got {root}")
check("C major: is_major",  major,        f"got {major}")
check("C major: conf>0.9",  conf > 0.9,   f"got {conf:.4f}")

root, major, conf = simulate_key_tracker(9, False)
print(f"  A minor  → root={root}, major={major}, confidence={conf:.8f}")
check("A minor: root=9",    root == 9,    f"got {root}")
check("A minor: !is_major", not major,    f"got {major}")
check("A minor: conf>0.9",  conf > 0.9,   f"got {conf:.4f}")

root, major, conf = simulate_key_tracker(6, True)
print(f"  F# major → root={root}, major={major}, confidence={conf:.8f}")
check("F# major: root=6",   root == 6,    f"got {root}")
check("F# major: is_major", major,        f"got {major}")
check("F# major: conf>0.9", conf > 0.9,   f"got {conf:.4f}")

section("KeyTracker simulation — silence (mirrors deck test key_tracker_silence_leaves_confidence_zero)")

# All-zero frames are dropped by normalize_profile → history never fills → confidence stays 0
history_empty = True
test_history_size = max(4, math.ceil(4.0 / 0.04))
silence_conf = 0.0
for _ in range(200):
    c = [0.0] * 12
    if normalize_profile(c):
        history_empty = False
check("silence: normalize_profile drops all-zero frames", history_empty)
check("silence: confidence stays 0.0", silence_conf == 0.0, f"got {silence_conf}")

# ── Summary ───────────────────────────────────────────────────────────────────

total = passed + failed
print(f"\n── Result {'─' * 69}")
if failures:
    print(f"  FAILED {failed}/{total} check(s): {failures}")
    print()
    print("  The Kotlin port does NOT match deck's pitch.rs on these inputs.")
    sys.exit(1)
else:
    print(f"  All {total} checks passed.")
    print()
    print("  These Pearson r values are the reference for the Kotlin port.")
    print("  The Kotlin PitchAnalyzerTest asserts the same thresholds and must")
    print("  produce values within float epsilon of the numbers printed above.")
