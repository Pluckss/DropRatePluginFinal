"""Generate the Plugin Hub icon (icon.png, 48x72) for the Drop Rate plugin.

A stacked "1/x" fraction in white on a rounded purple plate.

Why a fraction and not a droplet: the icon is shown at 48x72 in a long list, so
whatever it says has to survive at that size. A fraction is what the plugin
actually prints, and it stays readable when scaled down; fine details such as a
gloss highlight turn to mush. The plate is filled so the icon keeps its contrast
on the client's dark plugin panel and on the white plugin-hub website alike.

The purple is the plugin's own ultra-rare tier colour, so the icon and the
rarest chat messages match.

Plugin Hub limits enforced by the packager (plugin-hub-tooling, Plugin.java):
width * height must not exceed 50 * 100 px, and the file must be under 256 KiB.
48x72 = 3456 px2, well inside both.

Run it with the interpreter noted in CLAUDE.md:  <python> scripts/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 48, 72
SS = 8                          # supersample factor, downscaled with LANCZOS at the end
PLATE = (140, 32, 164, 255)     # plate fill, a shade off the ultra-rare purple
INK = (26, 6, 42, 255)          # outline
FONT = "C:/Windows/Fonts/ariblk.ttf"
SIZE = 28                       # glyph size in final px
GAP = 0.215                     # vertical offset of each glyph from centre, as a fraction of H
BAR_W = 0.60                    # fraction bar width, as a fraction of W


def outlined(img, text, font, xy, fill, radius):
    """Draw text with a solid outline, so it reads on any plate colour."""
    draw = ImageDraw.Draw(img)
    for dx in range(-radius, radius + 1):
        for dy in range(-radius, radius + 1):
            if dx * dx + dy * dy <= radius * radius:
                draw.text((xy[0] + dx, xy[1] + dy), text, font=font, fill=INK, anchor="mm")
    draw.text(xy, text, font=font, fill=fill, anchor="mm")


img = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)
draw.rounded_rectangle(
    [1.5 * SS, 1.5 * SS, (W - 1.5) * SS, (H - 1.5) * SS], radius=9 * SS, fill=PLATE
)

font = ImageFont.truetype(FONT, int(SIZE * SS))
cy = H * SS / 2
outlined(img, "1", font, (W * SS / 2, cy - H * SS * GAP), (255, 255, 255, 255), int(2.1 * SS))
outlined(img, "x", font, (W * SS / 2, cy + H * SS * GAP), (255, 255, 255, 255), int(2.1 * SS))

bar_w, bar_h, edge = W * SS * BAR_W, 2.7 * SS, 2.1 * SS
draw.rounded_rectangle(
    [W * SS / 2 - bar_w / 2 - edge, cy - bar_h - edge,
     W * SS / 2 + bar_w / 2 + edge, cy + bar_h + edge], radius=4 * SS, fill=INK
)
draw.rounded_rectangle(
    [W * SS / 2 - bar_w / 2, cy - bar_h, W * SS / 2 + bar_w / 2, cy + bar_h],
    radius=bar_h, fill=(255, 255, 255, 255)
)

repo = Path(__file__).resolve().parent.parent
img.resize((W, H), Image.LANCZOS).save(repo / "icon.png")
img.resize((W * 4, H * 4), Image.LANCZOS).save(repo / "scripts" / "icon_preview.png")
print(f"wrote {repo / 'icon.png'} ({W}x{H}) and scripts/icon_preview.png ({W * 4}x{H * 4})")
