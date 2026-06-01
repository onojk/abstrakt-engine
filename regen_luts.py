#!/usr/bin/env python3
"""Regenerate all bundled .cube LUT files into app/src/main/assets/luts/.

Math mirrors BuiltinLuts.generate() exactly so PartyEngine (in-memory)
and the button-picker (file-loaded) produce identical looks.

Layout: R-fastest, index = r + g*N + b*N*N.
"""

import os
import math

OUT_DIR = os.path.join(os.path.dirname(__file__),
                       "app", "src", "main", "assets", "luts")
N = 17


def clip(x):
    return max(0.0, min(1.0, x))


def generate(lut_id, n=N):
    rows = []
    for bi in range(n):
        for gi in range(n):
            for ri in range(n):
                r = ri / (n - 1)
                g = gi / (n - 1)
                b = bi / (n - 1)

                if lut_id == "identity":
                    ro, go, bo = r, g, b

                elif lut_id == "warm":
                    ro = clip(r * 1.18 + 0.08)
                    go = g
                    bo = clip(b * 0.72)

                elif lut_id == "cool":
                    luma = r * 0.299 + g * 0.587 + b * 0.114
                    rr = clip(r * 0.72)
                    gg = g
                    bb = clip(b * 1.18 + 0.08)
                    ro = rr + (luma - rr) * 0.15
                    go = gg + (luma - gg) * 0.15
                    bo = bb + (luma - bb) * 0.15

                elif lut_id == "vivid":
                    luma = r * 0.299 + g * 0.587 + b * 0.114
                    ro = clip(luma + (r - luma) * 1.6)
                    go = clip(luma + (g - luma) * 1.6)
                    bo = clip(luma + (b - luma) * 1.6)

                elif lut_id == "bw":
                    luma = r * 0.299 + g * 0.587 + b * 0.114
                    ro = go = bo = luma

                elif lut_id == "sepia":
                    luma = r * 0.299 + g * 0.587 + b * 0.114
                    ro = clip(luma * 1.10 + 0.12)
                    go = clip(luma * 0.82 + 0.02)
                    bo = clip(luma * 0.55)

                elif lut_id == "teal_orange":
                    luma = r * 0.299 + g * 0.587 + b * 0.114
                    sr = 0.0 + (0.65 - 0.0) * luma
                    sg = 0.45 + (0.42 - 0.45) * luma
                    sb = 0.55 + (0.00 - 0.55) * luma
                    ro = clip(r + (sr - r) * 0.55)
                    go = clip(g + (sg - g) * 0.55)
                    bo = clip(b + (sb - b) * 0.55)

                elif lut_id == "contrast":
                    def boost(x):
                        return clip((x - 0.5) * 1.5 + 0.5)
                    ro, go, bo = boost(r), boost(g), boost(b)

                else:
                    ro, go, bo = r, g, b

                rows.append((ro, go, bo))
    return rows


LUTS = [
    ("identity",    "Original"),
    ("warm",        "Warm"),
    ("cool",        "Cool"),
    ("vivid",       "Vivid"),
    ("bw",          "B&W"),
    ("sepia",       "Sepia"),
    ("teal_orange", "Teal Orange"),
    ("contrast",    "Contrast"),
]

os.makedirs(OUT_DIR, exist_ok=True)

for lut_id, title in LUTS:
    rows = generate(lut_id)
    path = os.path.join(OUT_DIR, f"{lut_id}.cube")
    with open(path, "w") as f:
        f.write(f'TITLE "{title}"\n')
        f.write(f"LUT_3D_SIZE {N}\n")
        f.write("\n")
        for ro, go, bo in rows:
            f.write(f"{ro:.6f} {go:.6f} {bo:.6f}\n")
    line_count = 3 + len(rows)
    print(f"{lut_id}.cube  {line_count} lines")

print("Done.")
