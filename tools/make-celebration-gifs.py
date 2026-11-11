"""Draws the celebration GIFs that come with the app (shared/src/commonMain/composeResources/files/celebrations).

Run: python3 tools/make-celebration-gifs.py shared/src/commonMain/composeResources/files/celebrations
Needs Pillow. The drawings are made here from simple shapes, so they belong to this project.
"""
import math, os, random, sys
from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W = H = 240
FRAMES = 28
COLORS = [(244, 67, 54), (255, 193, 7), (76, 175, 80), (33, 150, 243), (156, 39, 176), (255, 112, 67), (0, 188, 212)]
KEY = (1, 2, 3)  # transparent colour

def save(name, frames, ms=50):
    colours = [KEY] + COLORS + [(255, 255, 255), (255, 235, 59), (129, 199, 132)]
    flat = [v for c in colours for v in c]
    flat += flat[-3:] * (256 - len(colours))
    pal = Image.new("P", (1, 1))
    pal.putpalette(flat)
    out = [f.convert("RGB").quantize(palette=pal, dither=Image.Dither.NONE) for f in frames]
    out[0].save(os.path.join(OUT, name), save_all=True, append_images=out[1:], duration=ms, loop=0, transparency=0, disposal=2, optimize=False)

def canvas():
    im = Image.new("RGB", (W, H), KEY)
    return im, ImageDraw.Draw(im)

def confetti():
    rnd = random.Random(7)
    bits = [(rnd.uniform(0, W), rnd.uniform(-H, 0), rnd.uniform(3, 7), rnd.uniform(0, 6.28), rnd.uniform(-0.4, 0.4), rnd.choice(COLORS), rnd.uniform(-1.5, 1.5)) for _ in range(70)]
    frames = []
    for i in range(FRAMES):
        im, d = canvas()
        t = i / FRAMES
        for x, y, v, a, spin, c, drift in bits:
            yy = (y + t * (H * 1.0 + v * 12)) % (H + 20) - 10
            xx = x + math.sin(a + t * 6.28) * 8 + drift * i
            ang = a + spin * i
            w, h = 9, 5
            pts = [(xx + math.cos(ang) * dx - math.sin(ang) * dy, yy + math.sin(ang) * dx + math.cos(ang) * dy)
                   for dx, dy in [(-w/2, -h/2), (w/2, -h/2), (w/2, h/2), (-w/2, h/2)]]
            d.polygon(pts, fill=c)
        frames.append(im)
    save("confetti.gif", frames)

def star(d, cx, cy, r1, r2, rot, fill):
    pts = []
    for k in range(10):
        r = r1 if k % 2 == 0 else r2
        a = rot + k * math.pi / 5 - math.pi / 2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=fill)

def starburst():
    frames = []
    for i in range(FRAMES):
        im, d = canvas()
        t = i / FRAMES
        for k in range(16):
            a = k * math.pi / 8 + t * 0.8
            r0, r1 = 40 + 30 * t, 70 + 45 * t
            c = COLORS[k % len(COLORS)]
            d.line([(120 + r0 * math.cos(a), 120 + r0 * math.sin(a)), (120 + r1 * math.cos(a), 120 + r1 * math.sin(a))], fill=c, width=5)
        s = 1 + 0.12 * math.sin(t * 6.28 * 2)
        star(d, 120, 120, 52 * s, 22 * s, t * 0.6, (255, 193, 7))
        star(d, 120, 120, 30 * s, 13 * s, t * 0.6, (255, 235, 59))
        frames.append(im)
    save("star.gif", frames)

def fireworks():
    rnd = random.Random(3)
    shells = [(rnd.uniform(60, 180), rnd.uniform(60, 150), rnd.choice(COLORS), off) for off in (0, 9, 18)]
    frames = []
    for i in range(FRAMES):
        im, d = canvas()
        for cx, cy, c, off in shells:
            t = ((i - off) % FRAMES) / FRAMES
            if t > 0.75:
                continue
            r = 10 + 100 * t
            for k in range(18):
                a = k * 2 * math.pi / 18
                x, y = cx + r * math.cos(a), cy + r * math.sin(a) + 30 * t * t
                size = max(3, 8 * (1 - t))
                d.ellipse([x - size, y - size, x + size, y + size], fill=c)
                x2, y2 = cx + r * 0.7 * math.cos(a), cy + r * 0.7 * math.sin(a) + 20 * t * t
                d.ellipse([x2 - size / 2, y2 - size / 2, x2 + size / 2, y2 + size / 2], fill=(255, 235, 59))
        frames.append(im)
    save("fireworks.gif", frames, ms=60)

def check():
    frames = []
    for i in range(FRAMES):
        im, d = canvas()
        t = min(1.0, i / (FRAMES * 0.6))
        pulse = 1 + 0.06 * math.sin(i / FRAMES * 6.28 * 2) if t >= 1 else 1
        r = 80 * pulse
        d.ellipse([120 - r, 120 - r, 120 + r, 120 + r], fill=(76, 175, 80))
        ring = 80 + 30 * ((i / FRAMES) % 1)
        d.ellipse([120 - ring, 120 - ring, 120 + ring, 120 + ring], outline=(129, 199, 132), width=4)
        pts = [(80, 124), (108, 152), (162, 96)]
        if t < 0.4:
            u = t / 0.4
            seg = [pts[0], (pts[0][0] + (pts[1][0] - pts[0][0]) * u, pts[0][1] + (pts[1][1] - pts[0][1]) * u)]
        else:
            u = (t - 0.4) / 0.6
            seg = [pts[0], pts[1], (pts[1][0] + (pts[2][0] - pts[1][0]) * u, pts[1][1] + (pts[2][1] - pts[1][1]) * u)]
        d.line(seg, fill=(255, 255, 255), width=16, joint="curve")
        frames.append(im)
    save("check.gif", frames)

confetti(); starburst(); fireworks(); check()
