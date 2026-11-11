"""Draws the Windows app icon (desktop/icons/checklist.ico), used by the installer, the Start menu
and the desktop shortcut.

The same design as the Android launcher icon (app/src/main/res/drawable/ic_launcher_foreground.xml)
and the iPhone icon (tools/make-ios-icon.py): a white ring and tick on the garden green, here on
a rounded square, drawn large and scaled down to each size Windows uses so the edges are smooth.
The window and the notification area draw the same picture in code (checklistIcon in
shared/src/jvmMain).
Run: python3 tools/make-windows-icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "desktop" / "icons" / "checklist.ico"
SIZES = [16, 20, 24, 32, 40, 48, 64, 128, 256]
BIG = 1024
GREEN = (0x2E, 0x6B, 0x5E, 255)
WHITE = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


def draw() -> Image.Image:
    # The Android drawing is on a 108 x 108 grid; the icon shows a 72 x 72 window around its
    # middle, as Android's mask shows it.
    unit = BIG / 72.0
    off = 18.0

    def p(x: float, y: float) -> tuple[float, float]:
        return ((x - off) * unit, (y - off) * unit)

    image = Image.new("RGBA", (BIG, BIG), CLEAR)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle([0, 0, BIG - 1, BIG - 1], radius=int(BIG * 0.18), fill=GREEN)
    # The ring: centre (54, 54), radius 24, stroke 5.
    r_out, r_in = 24 + 2.5, 24 - 2.5
    cx, cy = p(54, 54)
    d.ellipse([cx - r_out * unit, cy - r_out * unit, cx + r_out * unit, cy + r_out * unit], fill=WHITE)
    d.ellipse([cx - r_in * unit, cy - r_in * unit, cx + r_in * unit, cy + r_in * unit], fill=GREEN)
    # The tick: (43, 55) to (51, 63) to (66, 46), stroke 6, round ends.
    points = [p(43, 55), p(51, 63), p(66, 46)]
    width = int(6 * unit)
    d.line(points, fill=WHITE, width=width, joint="curve")
    for x, y in points:
        d.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=WHITE)
    return image


def main() -> None:
    big = draw()
    frames = [big.resize((size, size), Image.LANCZOS) for size in SIZES]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    frames[-1].save(OUT, format="ICO", sizes=[(s, s) for s in SIZES], append_images=frames[:-1])
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
