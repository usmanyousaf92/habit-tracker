#!/usr/bin/env python3
"""
Generates the legacy (pre-API-26) launcher PNGs from the app's own sprout mark.

Android 8+ uses the adaptive icon in res/mipmap-anydpi-v26/, which is pure
vector. These PNGs only ever show on Android 7.x, but minSdk is 24 so they have
to exist. The bezier control points below are the exact path data from the Home
banner watermark in index.html, so the raster and the vector agree.

Usage:  python3 tools/make_icons.py
Requires: Pillow
"""

import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(HERE, "app", "src", "main", "res")

# --- brand ---------------------------------------------------------------
BG_START = (0x16, 0x30, 0x1F)   # --hero gradient start
BG_END = (0x2F, 0x6B, 0x3E)     # --hero gradient end
LEAF_BACK = (0x7C, 0x94, 0x70)  # lower-left leaf
LEAF_FRONT = (0xA8, 0xC7, 0x9A) # upper-right leaf
STEM = (0x4B, 0x5C, 0x40)

SS = 4  # supersample factor

# Cubic bezier chains in the artwork's native 40x40 space, straight out of the
# SVG path data: M20,24 c-5,-1 -8,-5 -8,-10  5,0 9,3 10,7 Z  (and the mirror)
LEAF_LEFT = [
    ((20, 24), (15, 23), (12, 19), (12, 14)),
    ((12, 14), (17, 14), (21, 17), (22, 21)),
]
LEAF_RIGHT = [
    ((20, 20), (25, 19), (28, 15), (28, 10)),
    ((28, 10), (23, 10), (19, 14), (18, 18)),
]

ART_BBOX = (9.0, 9.0, 31.0, 37.0)  # x0, y0, x1, y1 of the drawn artwork
ART_FRACTION = 0.62                # share of the icon the mark occupies


def bezier(p0, p1, p2, p3, steps=48):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = (u ** 3) * p0[0] + 3 * (u ** 2) * t * p1[0] + 3 * u * (t ** 2) * p2[0] + (t ** 3) * p3[0]
        y = (u ** 3) * p0[1] + 3 * (u ** 2) * t * p1[1] + 3 * u * (t ** 2) * p2[1] + (t ** 3) * p3[1]
        pts.append((x, y))
    return pts


def chain(segments):
    out = []
    for seg in segments:
        pts = bezier(*seg)
        out.extend(pts if not out else pts[1:])
    return out


def gradient(size):
    """Diagonal linear gradient, top-left dark to bottom-right light."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    denom = max(1, 2 * (size - 1))
    for y in range(size):
        for x in range(size):
            t = (x + y) / denom
            px[x, y] = (
                round(BG_START[0] + (BG_END[0] - BG_START[0]) * t),
                round(BG_START[1] + (BG_END[1] - BG_START[1]) * t),
                round(BG_START[2] + (BG_END[2] - BG_START[2]) * t),
            )
    return img


def render(px_size, round_icon):
    size = px_size * SS
    base = gradient(size).convert("RGBA")

    # shape mask
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if round_icon:
        md.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)

    art = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ad = ImageDraw.Draw(art)

    x0, y0, x1, y1 = ART_BBOX
    k = ART_FRACTION * size / max(x1 - x0, y1 - y0)
    tx = size / 2 - k * (x0 + x1) / 2
    ty = size / 2 - k * (y0 + y1) / 2
    T = lambda p: (tx + k * p[0], ty + k * p[1])

    # ground shadow — kept faint so it reads as ground, not as a second shape
    ad.ellipse([T((11, 31.6)), T((29, 36.4))], fill=(255, 255, 255, 26))

    # stem
    ad.line([T((20, 34)), T((20, 19))], fill=STEM + (255,),
            width=max(1, int(2.4 * k)), joint="curve")
    r = 1.2 * k
    cx, cy = T((20, 19))
    ad.ellipse([cx - r, cy - r, cx + r, cy + r], fill=STEM + (255,))

    ad.polygon([T(p) for p in chain(LEAF_LEFT)], fill=LEAF_BACK + (255,))
    ad.polygon([T(p) for p in chain(LEAF_RIGHT)], fill=LEAF_FRONT + (255,))

    base.alpha_composite(art)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out.resize((px_size, px_size), Image.LANCZOS)


DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

if __name__ == "__main__":
    for folder, px in DENSITIES.items():
        d = os.path.join(RES, folder)
        os.makedirs(d, exist_ok=True)
        render(px, False).save(os.path.join(d, "ic_launcher.png"))
        render(px, True).save(os.path.join(d, "ic_launcher_round.png"))
        print(f"{folder:22} {px}x{px}")
    print("done")
