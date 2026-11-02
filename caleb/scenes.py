"""Fetch the pinned shows' pictures into theme/scenes/: each pin's scene (scene_url, the shelf's background,
usually an episode still) and card (card_url, the jump row's key art), from TVmaze.

Usage: python -I caleb/scenes.py            (then: TV_HOST=<ip> tools/theme.sh caleb/theme)
Each picture is center-cropped to 16:9 (after an optional scene_crop or card_crop [left, top, right, bottom] in the
original's pixels, e.g. to cut a logo out) and saved 1280x720. The pictures are kept out of git: they are
the shows' artwork.
"""
import io
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request

from PIL import Image

THEME = pathlib.Path(__file__).parent / "theme"


def crop169(im):
    w, h = im.size
    if w / h > 16 / 9:
        nw = round(h * 16 / 9)
        return im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    nh = round(w * 9 / 16)
    return im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))


def safe(name):
    assert "/" not in name and "\\" not in name and not name.startswith("."), name


def fetch(url, tries=4):
    req = urllib.request.Request(url, headers={"User-Agent": "projector-scenes/1.0 (personal)"})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return Image.open(io.BytesIO(r.read()))
        except urllib.error.HTTPError as e:
            if e.code != 429 or i == tries - 1:
                raise
            time.sleep(5 * (i + 1))   # rate limited (Wikimedia): wait and try again


def white_logo(im, key=None, tol=90):
    """White letters on transparency, cropped tight, at most 540 px wide."""
    alpha = im.getchannel("A")
    if key:
        px, w, h = im.load(), im.width, im.height
        mask = Image.new("L", (w, h))
        m = mask.load()
        for y in range(h):
            for x in range(w):
                pr, pg, pb, pa = px[x, y]
                d = ((pr - key[0]) ** 2 + (pg - key[1]) ** 2 + (pb - key[2]) ** 2) ** 0.5
                m[x, y] = int(pa * max(0.0, min(1.0, (tol - d) / (tol * 0.4))))
        alpha = mask
    out = Image.new("RGBA", im.size, (255, 255, 255, 0))
    out.putalpha(alpha)
    out = out.crop(out.getbbox())
    if out.width > 540:
        out = out.resize((540, round(out.height * 540 / out.width)), Image.LANCZOS)
    return out


def main():
    pins = json.loads((THEME / "pins.json").read_text(encoding="utf-8"))["pins"]
    out = THEME / "scenes"
    out.mkdir(exist_ok=True)
    for pin in pins:
        # the shelf's background (scene) and the jump row's card (card), each from its own picture
        for kind in ("scene", "card"):
            name, url = pin.get(kind), pin.get(f"{kind}_url")
            if not name or not url:
                continue
            safe(name)
            im = fetch(url).convert("RGB")
            if pin.get(f"{kind}_crop"):
                im = im.crop(tuple(pin[f"{kind}_crop"]))
            crop169(im).resize((1280, 720), Image.LANCZOS).save(out / name, "JPEG", quality=82, optimize=True)
            print(f"{name}: {pin['show']} {kind}")
        # the card's logo, in white: logo_key keeps only the letters' colour (a logo on a coloured badge)
        if pin.get("logo") and pin.get("logo_url"):
            safe(pin["logo"])
            white_logo(fetch(pin["logo_url"]).convert("RGBA"), pin.get("logo_key")).save(out / pin["logo"], "PNG", optimize=True)
            print(f"{pin['logo']}: {pin['show']} logo")

if __name__ == "__main__":
    sys.exit(main())
