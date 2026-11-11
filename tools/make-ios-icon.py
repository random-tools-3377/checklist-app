"""Draws the iPhone app icon (iosApp/Sources/Assets.xcassets/AppIcon.appiconset/icon-1024.png).

The same design as the Android launcher icon (app/src/main/res/drawable/ic_launcher_foreground.xml):
a white ring and tick on the garden green, drawn large and scaled down so the edges are smooth.
Run: python3 tools/make-ios-icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "iosApp" / "Sources" / "Assets.xcassets" / "AppIcon.appiconset" / "icon-1024.png"
SIZE = 1024
BIG = SIZE * 4
GREEN = (0x2E, 0x6B, 0x5E)
WHITE = (255, 255, 255)


def main() -> None:
    # The Android drawing is on a 108 x 108 grid; the iPhone shows the whole square, so the
    # design is drawn on a 72 x 72 window around its middle, as Android's mask shows it.
    unit = BIG / 72.0
    off = 18.0

    def p(x: float, y: float) -> tuple[float, float]:
        return ((x - off) * unit, (y - off) * unit)

    image = Image.new("RGB", (BIG, BIG), GREEN)
    draw = ImageDraw.Draw(image)
    # The ring: centre (54, 54), radius 24, stroke 5.
    r_out, r_in = 24 + 2.5, 24 - 2.5
    cx, cy = p(54, 54)
    draw.ellipse([cx - r_out * unit, cy - r_out * unit, cx + r_out * unit, cy + r_out * unit], fill=WHITE)
    draw.ellipse([cx - r_in * unit, cy - r_in * unit, cx + r_in * unit, cy + r_in * unit], fill=GREEN)
    # The tick: (43, 55) to (51, 63) to (66, 46), stroke 6, round ends.
    points = [p(43, 55), p(51, 63), p(66, 46)]
    width = int(6 * unit)
    draw.line(points, fill=WHITE, width=width, joint="curve")
    for x, y in (points[0], points[-1]):
        draw.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=WHITE)
    image.resize((SIZE, SIZE), Image.LANCZOS).save(OUT, optimize=True)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
