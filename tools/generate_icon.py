"""Builds the launcher PNGs for Android 7 (no adaptive icons) and the Play Store icon.

Same drawing as res/drawable/ic_launcher_foreground.xml: a sliding puzzle with its last tile
missing. Run from the project root after changing the design: python3 tools/generate_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

PINK = (234, 76, 137, 255)
WHITE = (255, 255, 255, 255)
ORANGE = (255, 166, 62, 255)
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SUPERSAMPLE = 4


def artwork(size):
    """Full-bleed square. Tiles fill 60%: adaptive icons have a mask to leave room for, these do not."""
    image = Image.new("RGBA", (size, size), PINK)
    draw = ImageDraw.Draw(image)
    unit = size * 0.60 / 48  # the grid is 48 units wide: three tiles of 14 and two gaps of 3
    origin = size * 0.20

    for row in range(3):
        for col in range(3):
            if (row, col) == (2, 2):
                continue
            x = origin + col * 17 * unit
            y = origin + row * 17 * unit
            colour = ORANGE if (row, col) == (2, 1) else WHITE
            draw.rounded_rectangle((x, y, x + 14 * unit, y + 14 * unit), radius=3 * unit, fill=colour)

    return image


def masked(image, shape):
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    box = (0, 0, image.width - 1, image.height - 1)
    if shape == "round":
        draw.ellipse(box, fill=255)
    else:
        draw.rounded_rectangle(box, radius=image.width * 0.22, fill=255)

    result = Image.new("RGBA", image.size, (0, 0, 0, 0))
    result.paste(image, (0, 0), mask)
    return result


res = Path("app/src/main/res")
for density, size in DENSITIES.items():
    big = artwork(size * SUPERSAMPLE)
    folder = res / f"mipmap-{density}"
    masked(big, "square").resize((size, size), Image.LANCZOS).save(folder / "ic_launcher.png")
    masked(big, "round").resize((size, size), Image.LANCZOS).save(folder / "ic_launcher_round.png")

# Google Play applies its own mask, and asks for a full square with no transparency.
artwork(512 * SUPERSAMPLE).resize((512, 512), Image.LANCZOS).convert("RGB").save("docs/store/icon-512.png")
