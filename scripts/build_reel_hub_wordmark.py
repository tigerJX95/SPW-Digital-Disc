"""Render the reel's circular hub inscription. Requires Pillow and DejaVu Sans Bold."""

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ASSET = Path(__file__).resolve().parents[1] / "plugin" / "reel-wordmark" / "hub-arc.png"
SIDE = 768
CENTER = SIDE / 2
RADIUS = SIDE * 0.137 / 0.34
INK = (239, 228, 214, 224)


def inscription(image, font, text, span, lower=False):
    widths = [font.getlength(letter) for letter in text]
    spacing = (RADIUS * math.radians(span) - sum(widths)) / (len(text) - 1)
    position = -math.radians(span) / 2
    for letter, width in zip(text, widths):
        angle = position + width / (2 * RADIUS)
        position += (width + spacing) / RADIUS
        if letter == " ":
            continue
        glyph = Image.new("RGBA", (128, 128))
        ImageDraw.Draw(glyph).text((64, 64), letter, font=font, fill=INK, anchor="mm")
        rotation = math.degrees(angle) * (1 if lower else -1)
        glyph = glyph.rotate(rotation, Image.Resampling.BICUBIC)
        x = CENTER + RADIUS * math.sin(angle)
        y = CENTER + (1 if lower else -1) * RADIUS * math.cos(angle)
        image.alpha_composite(glyph, (round(x - 64), round(y - 64)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", default="DejaVuSans-Bold.ttf")
    font = ImageFont.truetype(parser.parse_args().font, 74)
    image = Image.new("RGBA", (SIDE, SIDE))
    inscription(image, font, "SALT PLAYER", 122)
    inscription(image, font, "SPW DIGITAL DISC", 144, lower=True)
    draw = ImageDraw.Draw(image)
    for x in (CENTER - RADIUS, CENTER + RADIUS):
        draw.ellipse((x - 8, CENTER - 8, x + 8, CENTER + 8), fill=INK)
    image.resize((384, 384), Image.Resampling.LANCZOS).save(ASSET, optimize=True)


if __name__ == "__main__":
    main()
