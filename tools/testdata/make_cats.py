"""Draws simple test cat images (sitting cat on a pavement background) for the sighting flow."""
import random
import sys
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1]


def background(d, w, h, rnd):
    # Sandy pavement with tiles, like a Dubai sidewalk.
    for y in range(0, h, 60):
        for x in range(0, w, 60):
            c = 200 + rnd.randint(-12, 12)
            d.rectangle([x, y, x + 58, y + 58], fill=(c, c - 12, c - 35))


def cat(path, body, stripes=None, white_patch=None, seed=1, shift=0):
    rnd = random.Random(seed)
    w, h = 900, 1200
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    background(d, w, h, rnd)
    cx = 450 + shift
    # tail, body, head, ears
    d.ellipse([cx + 150, 820, cx + 330, 900], fill=body)
    d.ellipse([cx - 230, 520, cx + 230, 1060], fill=body)
    d.ellipse([cx - 170, 250, cx + 170, 560], fill=body)
    d.polygon([(cx - 160, 330), (cx - 140, 160), (cx - 50, 270)], fill=body)
    d.polygon([(cx + 160, 330), (cx + 140, 160), (cx + 50, 270)], fill=body)
    if white_patch:
        d.ellipse([cx - 110, 600, cx + 110, 1040], fill=white_patch)
        d.ellipse([cx - 70, 420, cx + 70, 560], fill=white_patch)
        d.ellipse([cx - 200, 1000, cx - 90, 1070], fill=white_patch)
        d.ellipse([cx + 90, 1000, cx + 200, 1070], fill=white_patch)
    if stripes:
        for i in range(9):
            y = 560 + i * 55
            d.arc([cx - 240, y - 40, cx + 240, y + 60], 200, 340, fill=stripes, width=18)
        for i in range(3):
            d.line([(cx - 40 + i * 40, 280), (cx - 40 + i * 40, 350)], fill=stripes, width=14)
    # eyes, nose
    for ex in (-65, 65):
        d.ellipse([cx + ex - 28, 360, cx + ex + 28, 410], fill=(120, 170, 60))
        d.ellipse([cx + ex - 8, 365, cx + ex + 8, 405], fill=(20, 20, 20))
    d.polygon([(cx - 15, 440), (cx + 15, 440), (cx, 460)], fill=(230, 140, 140))
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    img.save(path, quality=92)


cat(f"{out}/orange_tabby_1.jpg", (226, 132, 48), stripes=(170, 85, 25), seed=1)
cat(f"{out}/orange_tabby_2.jpg", (222, 128, 45), stripes=(165, 82, 22), seed=2, shift=-40)
cat(f"{out}/tuxedo_1.jpg", (28, 28, 30), white_patch=(240, 238, 232), seed=3)
print("ok")
