"""Fetch the pinned shows' scene pictures into theme/scenes/, from each pin's scene_url (TVmaze).

Usage: python -I caleb/scenes.py            (then: TV_HOST=<ip> tools/theme.sh caleb/theme)
Each picture is center-cropped to 16:9 (after an optional scene_crop [left, top, right, bottom] in the
original's pixels, e.g. to cut a logo out) and saved 1280x720. The pictures are kept out of git: they are
the shows' artwork.
"""
import io
import json
import pathlib
import sys
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


def main():
    pins = json.loads((THEME / "pins.json").read_text(encoding="utf-8"))["pins"]
    out = THEME / "scenes"
    out.mkdir(exist_ok=True)
    for pin in pins:
        name, url = pin.get("scene"), pin.get("scene_url")
        if not name or not url:
            print(f"skip {pin.get('show')}: no scene or scene_url")
            continue
        assert "/" not in name and "\\" not in name and not name.startswith("."), name
        with urllib.request.urlopen(url, timeout=30) as r:
            im = Image.open(io.BytesIO(r.read())).convert("RGB")
        if pin.get("scene_crop"):
            im = im.crop(tuple(pin["scene_crop"]))
        crop169(im).resize((1280, 720), Image.LANCZOS).save(out / name, "JPEG", quality=82, optimize=True)
        print(f"{name}: {pin['show']}")


if __name__ == "__main__":
    sys.exit(main())
