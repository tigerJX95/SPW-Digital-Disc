"""Regenerate the shared cover-CD hub texture (requires Pillow)."""
from math import exp, hypot, sqrt
from pathlib import Path

from PIL import Image


def smooth(start, end, value):
    t = max(0, min(1, (value - start) / (end - start)))
    return t * t * (3 - 2 * t)


def gaussian(x):
    return exp(-0.5 * x * x)


def pixel(x, y):
    r = hypot(x, y)
    if r <= 0.125 or r >= 0.205:
        return (0, 0, 0, 0)
    u, v = x / r, y / r
    # Reflect the view vector about the rounded annulus normal.
    t = max(0, min(1, (r - 0.138) / 0.067))
    slope = 0.43 * (1 - 2 * t)
    scale = -2 * slope / (1 + slope * slope)
    rx, ry = scale * u, scale * v
    room = gaussian((rx + 0.41) / 0.30) * gaussian((ry + 0.46) / 0.225)
    fill = gaussian((rx - 0.34) / 0.41) * gaussian((ry - 0.29) / 0.34)
    mask = smooth(0.138, 0.146, r) * (1 - smooth(0.197, 0.205, r))
    light = mask * (0.43 * room + 0.095 * fill)
    shade = mask * (0.065 * smooth(-0.05, 0.65, rx) + 0.055 * (1 - room))

    # Opposite faces of the shallow molded ridge catch light and shade.
    q = (r - 0.135) / 0.0042
    slope = -0.94 * q * gaussian(q)
    facing = (0.66 * u + 0.75 * v) * slope / sqrt(1 + slope * slope)
    relief = smooth(0.125, 0.127, r) * (1 - smooth(0.144, 0.150, r))
    light += relief * 0.62 * max(0, facing)
    shade += relief * (0.46 * max(0, -facing) + 0.13 * gaussian((r - 0.142) / 0.0026))
    alpha = 1 - (1 - shade) * (1 - light)
    if round(255 * alpha) == 0:
        return (0, 0, 0, 0)
    return (*[round(c * light / alpha) for c in (251, 253, 255)], round(255 * alpha))


if __name__ == "__main__":
    size = 384
    coords = [(2 * (i + 0.5) / size - 1) * 0.21 for i in range(size)]
    image = Image.new("RGBA", (size, size))
    image.putdata([pixel(x, y) for y in coords for x in coords])
    image.save(Path(__file__).resolve().parents[1] / "plugin/cd-material/hub.png", optimize=True)
