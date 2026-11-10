#!/usr/bin/env python3
"""
Adaptive-icon backgrounds (ic_<name>_full) for the premium launcher icons.

The framed icon art sits a touch right-of-centre in the source and, filled
edge-to-edge, the launcher mask clips its right edge. This composites the framed
icon at a tunable scale with a small leftward nudge onto a bg-colour canvas, so
the whole frame stays inside the mask and reads centred.

Tune ICON_PX (size within the 432 canvas) and SHIFT_X (negative = left).
"""
import os
from PIL import Image

SRC = os.path.expanduser("~/Downloads/New Folder With Items")
DRAW = "app/src/main/res/drawable-nodpi"
CANVAS = 432
ICON_PX = 392   # default size within the 432 canvas → margin so the mask can't clip it
SHIFT_X = -7    # default nudge left to correct most sources' rightward bias

# name, source, bg hex, icon_px override|None, shift_x override|None
# (cyber's source sits left, so it needs a rightward shift + a touch smaller.)
ROWS = [
    ("pixel", "pixel.jpg", "360966", None, None),
    ("gold", "gold.jpg", "12051D", None, None),
    ("cyber", "cyber.jpg", "060721", 366, 1),
    ("demonic", "demonic.jpg", "080708", None, None),
    ("adventure", "adventure.jpg", "0B0217", None, None),
    ("noire", "noire.jpg", "060606", None, None),
]


def bg_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


for name, f, bg, icon_ovr, shift_ovr in ROWS:
    icon_px = icon_ovr or ICON_PX
    shift_x = SHIFT_X if shift_ovr is None else shift_ovr
    im = Image.open(os.path.join(SRC, f)).convert("RGB").resize((1498, 704), Image.LANCZOS)
    cx = 1498 // 2
    sq = im.crop((cx - 352, 0, cx + 352, 704)).resize((icon_px, icon_px), Image.LANCZOS)
    canvas = Image.new("RGB", (CANVAS, CANVAS), bg_rgb(bg))
    ox = (CANVAS - icon_px) // 2 + shift_x
    oy = (CANVAS - icon_px) // 2
    canvas.paste(sq, (ox, oy))
    dst = os.path.join(DRAW, f"ic_{name}_full.png")
    canvas.save(dst)
    print(f"{name:10s} icon={icon_px}px shiftX={shift_x} -> {dst}")
print("done")
