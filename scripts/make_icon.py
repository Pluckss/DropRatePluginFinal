"""Generate the Plugin Hub icon (icon.png, 48x72) for the Drop Rate plugin.

A stacked "1/x" on a scroll. The icon is shown at 48x72 in a long list, so
whatever it says has to survive at that size: a fraction is what the plugin
actually prints, and it stays readable when scaled down, where fine detail such
as a gloss highlight or a loot beam turns to mush.

Every vertical position is derived from the glyphs' real ink boxes rather than
from percentages of the frame. The first version placed them by eye and the "1"
overlapped the fraction bar by 0.3 px, which at 48 px reads as one fused shape.
Equal measured gaps, with the whole fraction optically centred between the
parchment's inner edges, is what keeps that from coming back if SIZE is changed.

Plugin Hub limits enforced by the packager (plugin-hub-tooling, Plugin.java):
width * height must not exceed 50 * 100 px, and the file must be under 256 KiB.
48x72 = 3456 px2, well inside both.

Run it with the interpreter noted in CLAUDE.md:  <python> scripts/make_icon.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

W, H, S = 48, 72, 8
INK = (26, 6, 42, 255)
PARCHMENT = (233, 214, 175, 255)
ROLLER = (146, 101, 56, 255)
FONT = "C:/Windows/Fonts/ariblk.ttf"


def build(size=22, gap=4.0, barw=0.48, barh=2.0, report=False):
    im = Image.new('RGBA', (W * S, H * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    px, py, border = 3 * S, 8.5 * S, 2.4 * S
    d.rounded_rectangle([px, py, (W * S) - px, (H * S) - py], radius=1.5 * S,
                        fill=PARCHMENT, outline=INK, width=int(border))
    for cy in (py, (H * S) - py):
        d.rounded_rectangle([px - 2.6 * S, cy - 4.6 * S, (W * S) - px + 2.6 * S, cy + 4.6 * S],
                            radius=4.6 * S, fill=ROLLER, outline=INK, width=int(border))

    f = ImageFont.truetype(FONT, int(size * S))
    cx = W * S / 2
    b1 = d.textbbox((0, 0), '1', font=f, anchor='mm')
    bx = d.textbbox((0, 0), 'x', font=f, anchor='mm')

    # Lay the fraction out around a bar at y=0, then move the whole block so its
    # ink is centred between the parchment's inner edges.
    bar_top, bar_bot = -barh * S, barh * S
    y1 = bar_top - gap * S - b1[3]
    yx = bar_bot + gap * S - bx[1]
    ink_top, ink_bot = y1 + b1[1], yx + bx[3]

    inner_top, inner_bot = py + border, (H * S) - py - border
    shift = (inner_top + inner_bot) / 2 - (ink_top + ink_bot) / 2

    d.rounded_rectangle([cx - W * S * barw / 2, bar_top + shift,
                         cx + W * S * barw / 2, bar_bot + shift], radius=barh * S, fill=INK)
    d.text((cx, y1 + shift), '1', font=f, fill=INK, anchor='mm')
    d.text((cx, yx + shift), 'x', font=f, fill=INK, anchor='mm')

    if report:
        print(f"  size={size} gap={gap}")
        print(f"    luft over '1'   : {(ink_top + shift - inner_top) / S:5.2f} px")
        print(f"    '1' til streg   : {gap:5.2f} px")
        print(f"    streg til 'x'   : {gap:5.2f} px")
        print(f"    luft under 'x'  : {(inner_bot - (ink_bot + shift)) / S:5.2f} px")

    return im.resize((W, H), Image.LANCZOS)


SIZE = 22   # glyph size; 23 reads bolder at 1:1, 21 leaves more air
GAP = 4.0   # measured ink gap above and below the fraction bar

if __name__ == "__main__":
    from pathlib import Path

    repo = Path(__file__).resolve().parent.parent
    icon = build(size=SIZE, gap=GAP, report=True)
    icon.save(repo / "icon.png")
    icon.resize((W * 4, H * 4), Image.LANCZOS).save(repo / "scripts" / "icon_preview.png")
    print(f"wrote {repo / 'icon.png'} ({W}x{H}) and scripts/icon_preview.png")
