"""Generate the Plugin Hub icon (icon.png, 48x72) for the Drop Rate plugin.

A glossy purple "rare drop" waterdrop with a white gloss highlight and a sparkle.
Purple = a rare/"purple" loot drop in OSRS; the droplet = a "drop". Drawn at 8x
and downscaled with LANCZOS for clean anti-aliased edges.

Run: py -3 scripts/make_icon.py
"""
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SS = 8                     # supersample factor
W, H = 48 * SS, 72 * SS    # working canvas

# Purple palette (light top -> deep bottom), dark outline
TOP_COL = (206, 152, 255)
BOT_COL = (88, 18, 138)
OUTLINE = (34, 8, 54)

cx = W / 2
R = W * 0.36               # bulb radius
cy = H * 0.62              # bulb centre
ty = H * 0.085             # tip (top point)


def qbez(p0, p1, p2, n=48):
    out = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        out.append((x, y))
    return out


# Single closed droplet outline: tip -> concave left side -> lower semicircle -> concave right side -> tip
left = qbez((cx, ty), (cx - R * 0.60, ty + (cy - ty) * 0.60), (cx - R, cy))
arc = []
for i in range(73):
    a = math.pi * (1 - i / 72)                 # pi -> 0, i.e. left -> bottom -> right
    arc.append((cx + R * math.cos(a), cy + R * math.sin(a)))
right = qbez((cx + R, cy), (cx + R * 0.60, ty + (cy - ty) * 0.60), (cx, ty))
outline = left + arc[1:] + right[1:]

# Vertical purple gradient
grad = Image.new("RGB", (W, H))
gp = grad.load()
y0, y1 = ty, cy + R
for y in range(H):
    t = min(1.0, max(0.0, (y - y0) / (y1 - y0)))
    gp_row = (
        int(TOP_COL[0] + (BOT_COL[0] - TOP_COL[0]) * t),
        int(TOP_COL[1] + (BOT_COL[1] - TOP_COL[1]) * t),
        int(TOP_COL[2] + (BOT_COL[2] - TOP_COL[2]) * t),
    )
    for x in range(W):
        gp[x, y] = gp_row

# Mask + fill
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).polygon(outline, fill=255)
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
img.paste(grad, (0, 0), mask)

# Outline stroke
ImageDraw.Draw(img).line(outline + [outline[0]], fill=OUTLINE, width=int(SS * 2.2), joint="curve")

# Gloss highlight (clipped to droplet)
gloss = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(gloss)
hx, hy = cx - R * 0.34, cy - R * 0.28
gd.ellipse([hx - R * 0.24, hy - R * 0.52, hx + R * 0.24, hy + R * 0.52], fill=(255, 255, 255, 165))
gd.ellipse([cx - R * 0.10, cy + R * 0.30, cx + R * 0.16, cy + R * 0.60], fill=(255, 255, 255, 60))
clip = ImageChops.multiply(gloss.getchannel("A"), mask)
gloss.putalpha(clip)
img = Image.alpha_composite(img, gloss)

# Sparkle (4-point star) on the droplet's upper-right so it reads white-on-purple
# on any background. A soft glow sits behind it.
sx, sy = cx + R * 0.40, cy - R * 0.52
s = R * 0.34
k = 0.15
star = [
    (sx, sy - s), (sx + s * k, sy - s * k), (sx + s, sy), (sx + s * k, sy + s * k),
    (sx, sy + s), (sx - s * k, sy + s * k), (sx - s, sy), (sx - s * k, sy - s * k),
]
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gdr = ImageDraw.Draw(glow)
gdr.ellipse([sx - s * 1.15, sy - s * 1.15, sx + s * 1.15, sy + s * 1.15], fill=(255, 255, 255, 70))
glow = glow.filter(ImageFilter.GaussianBlur(SS * 1.5))
img = Image.alpha_composite(img, glow)
ImageDraw.Draw(img).polygon(star, fill=(255, 255, 255, 245))

# Downscale: final icon + a larger preview for review
repo = Path(__file__).resolve().parent.parent
icon = img.resize((48, 72), Image.LANCZOS)
icon.save(repo / "icon.png")
img.resize((192, 288), Image.LANCZOS).save(repo / "scripts" / "icon_preview.png")
print(f"wrote {repo / 'icon.png'} (48x72) and scripts/icon_preview.png (192x288)")
