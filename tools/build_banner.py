"""Compose the README banner: generated backdrop + wordmark + real screenshots, pixel-intact.

Run with no arguments to rebuild docs/assets/banner.webp from tools/banner-src/. The wordmark and
tagline are drawn here, so changing them is a code change rather than a new backdrop. It uses the
macOS system font; on another OS pass a font with --font.

The screenshots are pasted, never redrawn — an image model rewrites small UI text
(URLs, JSON, timestamps), which in a debugging tool's banner reads as fabricated.
Phones bleed past the bottom edge, so each screenshot is cropped to the part that
actually carries content instead of showing empty space.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

BANNER = (2752, 1376)  # 2:1, cropped from the 2752x1536 backdrop
BLEED = 48  # how far the phones run past the bottom edge
BEZEL, PHONE_RADIUS, WINDOW_RADIUS = 13, 0.09, 24

# Left of x=1360 sits the wordmark and tagline — the cluster must not reach it.
# The phones sit low and tightly overlapped so the window keeps its top strip and
# its whole detail panel clear; a buried window reads as clutter, not as depth.
WINDOW = dict(name="04-web-viewer.webp", width=1120, left=1600, top=130, angle=-1.5)
PHONES = [
    dict(name="01-timeline.webp", width=450, left=1428, top=395),
    dict(name="02-event-detail.webp", width=432, left=1762, top=448),
    dict(name="03b-dark-timeline.webp", width=414, left=2092, top=500),
]

# Right of the logo the backdrop carries, in banner coordinates.
FONT = "/System/Library/Fonts/SFNS.ttf"
WORDMARK = dict(text="Verivent", left=605, baseline=600, max_right=1390)
TAGLINE = dict(lines=["Your mobile analytics,", "verified on the device."], left=227, baseline=790, size=96, leading=104)
CHIPS = dict(labels=["Tracking plan", "On device", "Android · iOS"], left=227, top=990, size=46)
PURPLE = (124, 108, 240)


def rounded(img: Image.Image, radius: int, square_bottom: bool = False) -> Image.Image:
    mask = Image.new("L", img.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (img.width - 1, img.height - 1)], radius, fill=255)
    if square_bottom:
        draw.rectangle([(0, img.height - radius - 1), (img.width - 1, img.height - 1)], fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def phone(shot: Image.Image, width: int, frame_h: int) -> Image.Image:
    """Frame `shot` at `width`, cropped from the top to exactly `frame_h` tall."""
    screen_w = width - 2 * BEZEL
    scaled_h = round(screen_w * shot.height / shot.width)
    screen = shot.resize((screen_w, scaled_h), Image.LANCZOS).crop((0, 0, screen_w, min(frame_h - BEZEL, scaled_h)))
    radius = round(width * PHONE_RADIUS)
    frame = Image.new("RGBA", (width, screen.height + BEZEL), (0, 0, 0, 0))
    frame.paste(rounded(Image.new("RGBA", frame.size, (24, 24, 30, 255)), radius, square_bottom=True), (0, 0))
    frame.paste(rounded(screen, max(radius - BEZEL, 6), square_bottom=True), (BEZEL, BEZEL))
    return frame


def with_shadow(img: Image.Image, blur: int = 30, dy: int = 16, alpha: int = 165) -> Image.Image:
    pad = blur * 3
    out = Image.new("RGBA", (img.width + 2 * pad, img.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste((0, 0, 0, alpha), (pad, pad + dy), img.split()[3])
    out = Image.alpha_composite(out, shadow.filter(ImageFilter.GaussianBlur(blur)))
    out.paste(img, (pad, pad), img)
    return out


def font(path: str, size: int, weight: str) -> ImageFont.FreeTypeFont:
    face = ImageFont.truetype(path, size)
    try:
        face.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass  # not a variable font: take it as it comes
    return face


def wordmark(canvas: Image.Image, font_path: str) -> None:
    draw = ImageDraw.Draw(canvas)
    # The largest size that keeps the wordmark clear of the screenshots.
    size = 260
    while size > 40 and draw.textlength(WORDMARK["text"], font=font(font_path, size, "Bold")) > WORDMARK["max_right"] - WORDMARK["left"]:
        size -= 4
    draw.text((WORDMARK["left"], WORDMARK["baseline"]), WORDMARK["text"], font=font(font_path, size, "Bold"),
              fill=(255, 255, 255), anchor="ls")

    regular = font(font_path, TAGLINE["size"], "Regular")
    for i, line in enumerate(TAGLINE["lines"]):
        draw.text((TAGLINE["left"], TAGLINE["baseline"] + i * TAGLINE["leading"]), line, font=regular,
                  fill=(178, 176, 190), anchor="ls")

    # Chips go on a layer of their own: drawing straight onto the canvas would replace its pixels
    # with the translucent fill instead of blending it.
    chips = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer = ImageDraw.Draw(chips)
    label = font(font_path, CHIPS["size"], "Medium")
    x, pad_x, height = CHIPS["left"], 34, round(CHIPS["size"] * 1.9)
    for text in CHIPS["labels"]:
        width = round(layer.textlength(text, font=label)) + 2 * pad_x
        layer.rounded_rectangle([(x, CHIPS["top"]), (x + width, CHIPS["top"] + height)], height // 2,
                                fill=(*PURPLE, 46), outline=(*PURPLE, 255), width=3)
        layer.text((x + width / 2, CHIPS["top"] + height / 2), text, font=label, fill=(226, 222, 255, 255), anchor="mm")
        x += width + 26
    canvas.alpha_composite(chips)


def main(backdrop: Path, shots: Path, out: Path, font_path: str = FONT) -> None:
    bg = Image.open(backdrop).convert("RGBA")
    top = (bg.height - round(bg.width / 2)) // 2
    canvas = bg.crop((0, top, bg.width, top + round(bg.width / 2))).resize(BANNER, Image.LANCZOS)
    wordmark(canvas, font_path)

    shot = Image.open(shots / WINDOW["name"]).convert("RGBA")
    height = round(WINDOW["width"] * shot.height / shot.width)
    piece = rounded(shot.resize((WINDOW["width"], height), Image.LANCZOS), WINDOW_RADIUS)
    piece = with_shadow(piece, blur=40, dy=24).rotate(WINDOW["angle"], Image.BICUBIC, expand=True)
    canvas.alpha_composite(piece, (WINDOW["left"] - 120, WINDOW["top"] - 120))

    for spec in PHONES:
        piece = phone(
            Image.open(shots / spec["name"]).convert("RGBA"),
            spec["width"],
            BANNER[1] + BLEED - spec["top"],
        )
        pad = 90
        piece = with_shadow(piece, blur=pad // 3)
        canvas.alpha_composite(piece, (spec["left"] - pad, spec["top"] - pad))

    canvas.convert("RGB").resize((2400, 1200), Image.LANCZOS).save(out, "WEBP", quality=88, method=6)
    print(f"{out}  2400x1200  {out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    args = sys.argv[1:]
    font_path = FONT
    if "--font" in args:
        at = args.index("--font")
        if at + 1 == len(args):
            sys.exit("--font needs a path to a .ttf or .otf file")
        font_path = args[at + 1]
        del args[at:at + 2]
    root = Path(__file__).resolve().parent
    src = root / "banner-src"
    out = Path(args[2]) if len(args) > 2 else root.parent / "docs/assets/banner.webp"
    out.parent.mkdir(parents=True, exist_ok=True)
    main(
        Path(args[0]) if len(args) > 0 else src / "backdrop.webp",
        Path(args[1]) if len(args) > 1 else src,
        out,
        font_path,
    )
