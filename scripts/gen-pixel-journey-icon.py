#!/usr/bin/env python3
"""
Generates the pixel-art journey glyph for the library's top bar, at every density.

The bar's pixel font mode swaps each Material icon for a hand-drawn pixel one
(ic_pixel_search, ic_pixel_add, ...). The journey button had no glyph of its own and
borrowed the settings gear, so in pixel mode it was indistinguishable from the Settings
button sitting right beside it.

The art is a 24x24 grid below, drawn once and scaled with NEAREST NEIGHBOUR so the
pixels stay square. That is also why the grid is the source rather than an SVG: this
icon set is pixel art, and a vector rasterised at five densities would soften exactly
the edges the style is made of.

Shape: three milestone nodes climbing left to right, joined by rising segments - the
journey's own chart in miniature, which is what Icons.Default.Timeline says in the
non-pixel font. White on transparent, like its siblings; the bar tints it at draw time.

Re-runnable. Requires Pillow (pip install pillow).
"""

from PIL import Image

# 24x24. '#' is opaque white, '.' is transparent.
ART = """
........................
........................
........................
........................
.................#####..
.................#####..
.................#####..
................##......
...............##.......
..............##........
.........#####..........
.........#####..........
.........#####..........
........##..............
.......##...............
......##................
..#####.................
..#####.................
..#####.................
........................
........................
........................
........................
........................
"""

# density bucket -> pixel size, matching the existing ic_pixel_* set exactly.
SIZES = {
    "mdpi": 24,
    "hdpi": 36,
    "xhdpi": 48,
    "xxhdpi": 72,
    "xxxhdpi": 96,
}

RES = "app/src/main/res"
NAME = "ic_pixel_journey"


def build_base() -> Image.Image:
    rows = [r for r in ART.strip("\n").split("\n")]
    assert len(rows) == 24, f"expected 24 rows, got {len(rows)}"
    for i, r in enumerate(rows):
        assert len(r) == 24, f"row {i} is {len(r)} wide, expected 24"
    img = Image.new("RGBA", (24, 24), (255, 255, 255, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                px[x, y] = (255, 255, 255, 255)
    return img


def main() -> None:
    base = build_base()
    for bucket, size in SIZES.items():
        out = base.resize((size, size), Image.NEAREST)
        path = f"{RES}/drawable-{bucket}/{NAME}.png"
        out.save(path)
        print(f"wrote {path} ({size}x{size})")


if __name__ == "__main__":
    main()
