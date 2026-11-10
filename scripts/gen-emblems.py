#!/usr/bin/env python3
"""
Generate background-free, full-color compass emblems for the library top-left
mark. Each source icon has its compass dead-centre on a fairly flat, dark (or
flat-purple) background; we colour-key that background to transparent so the
emblem floats on the app chrome like the free tinted masks do — just in colour.

Output: app/src/main/res/drawable-nodpi/emblem_<name>.png  (RGBA, 128x128)
"""
import os
import numpy as np
from PIL import Image

SRC = os.path.expanduser("~/Downloads/New Folder With Items")
DRAW = "app/src/main/res/drawable-nodpi"

# name, source file, centre-crop size (px, in the 1498x704 normalized space).
# Bigger crop = more of the wreath sneaks in; smaller = tighter on the compass.
# name, source, crop px, optional circular keep-radius (fraction of half-crop).
# The keep-radius isolates a central compass from an outer wreath that a plain
# square crop can't exclude (adventure's laurel dips close to the compass).
ROWS = [
    ("gold",      "gold.jpg",      240, None),
    ("cyber",     "cyber.jpg",     240, None),
    ("demonic",   "demonic.jpg",   270, None),
    ("adventure", "adventure.jpg", 400, 0.58),
    ("noire",     "noire.jpg",     240, None),
]

# Alpha ramp on distance-from-background (Euclidean in RGB, 0..441).
LO, HI = 45.0, 100.0
OUT_SIZE = 128
MARGIN = 0.06  # fraction of the trimmed emblem to leave as breathing room


def cutout(name, src_file, crop, keep_r=None):
    img = Image.open(os.path.join(SRC, src_file)).convert("RGB").resize((1498, 704), Image.LANCZOS)
    cx, cy = 1498 // 2, 704 // 2
    half = crop // 2
    img = img.crop((cx - half, cy - half, cx + half, cy + half))
    a = np.asarray(img, dtype=np.float32)
    h, w = a.shape[:2]

    # Background colour = median of the four corner patches (the flat field
    # surrounding the compass), which is what we want to knock out.
    patches = np.concatenate([
        a[:16, :16].reshape(-1, 3), a[:16, -16:].reshape(-1, 3),
        a[-16:, :16].reshape(-1, 3), a[-16:, -16:].reshape(-1, 3),
    ])
    bg = np.median(patches, axis=0)

    dist = np.sqrt(((a - bg) ** 2).sum(axis=2))
    alpha = np.clip((dist - LO) / (HI - LO), 0.0, 1.0)

    # Radial vignette: the compass is dead-centre; the wreath (if any crept into
    # the crop) sits further out. Fade alpha to 0 past ~half-radius so only the
    # central compass survives — keeps the bbox trim honest.
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    rr = np.sqrt((xx - w / 2) ** 2 + (yy - h / 2) ** 2) / (min(h, w) / 2)
    vignette = np.clip((1.05 - rr) / (1.05 - 0.90), 0.0, 1.0)
    alpha = alpha * vignette

    # Optional circular keep-mask: hard-ish cut past keep_r to drop an outer wreath.
    if keep_r is not None:
        keep = np.clip((keep_r + 0.06 - rr) / 0.12, 0.0, 1.0)
        alpha = alpha * keep

    # Trim to the alpha bounding box (+margin) then scale up, so the compass
    # fills the emblem like the default mask does instead of floating small.
    ys, xs = np.where(alpha > 0.15)
    if len(xs) > 0:
        x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
        side = max(x1 - x0, y1 - y0)
        m = int(side * MARGIN)
        ccx, ccy = (x0 + x1) // 2, (y0 + y1) // 2
        half2 = side // 2 + m
        x0, x1 = max(0, ccx - half2), min(w, ccx + half2)
        y0, y1 = max(0, ccy - half2), min(h, ccy + half2)
        a = a[y0:y1, x0:x1]
        alpha = alpha[y0:y1, x0:x1]

    rgba = np.dstack([a, alpha * 255.0]).astype(np.uint8)
    out = Image.fromarray(rgba).resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)
    dst = os.path.join(DRAW, f"emblem_{name}.png")
    out.save(dst)
    op = int((alpha > 0.5).mean() * 100)
    print(f"{name:10s} bg={tuple(int(x) for x in bg)} fill~{op}%  -> {dst}")


if __name__ == "__main__":
    os.makedirs(DRAW, exist_ok=True)
    for row in ROWS:
        cutout(*row)
    print("done")
