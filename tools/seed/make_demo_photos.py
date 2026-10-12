"""Draws simple illustrated animal photos for the demo posts (dog, parrot, rabbit, kittens), plus thumbnails.

Usage: python make_demo_photos.py <output folder>
"""
import os
import random
import sys
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
W, H = 900, 1200


def ground(d, rnd, top, tile):
    for y in range(0, H, 60):
        for x in range(0, W, 60):
            c = rnd.randint(-10, 10)
            d.rectangle([x, y, x + 58, y + 58], fill=tuple(max(0, min(255, v + c)) for v in tile))
    d.rectangle([0, 0, W, 330], fill=top)


def save(img, name):
    img = img.filter(ImageFilter.GaussianBlur(1.1))
    img.save(os.path.join(out, name + ".jpg"), quality=86)
    thumb = img.copy()
    thumb.thumbnail((360, 360))
    thumb.save(os.path.join(out, name + "_t.jpg"), quality=72)


def eyes(d, cx, y, gap, color=(40, 30, 20), size=24):
    for ex in (-gap, gap):
        d.ellipse([cx + ex - size, y - size, cx + ex + size, y + size], fill=(250, 250, 250))
        d.ellipse([cx + ex - size // 2, y - size // 2, cx + ex + size // 2, y + size // 2], fill=color)


def dog(name, fur, ear, collar, seed):
    rnd = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    ground(d, rnd, (150, 200, 235), (196, 186, 160))
    cx = 450
    d.ellipse([cx + 160, 760, cx + 360, 820], fill=fur)               # tail
    d.ellipse([cx - 250, 560, cx + 250, 1080], fill=fur)              # body
    d.rectangle([cx - 200, 950, cx - 120, 1130], fill=fur)            # legs
    d.rectangle([cx + 120, 950, cx + 200, 1130], fill=fur)
    d.ellipse([cx - 190, 250, cx + 190, 610], fill=fur)               # head
    d.ellipse([cx - 250, 270, cx - 130, 560], fill=ear)               # floppy ears
    d.ellipse([cx + 130, 270, cx + 250, 560], fill=ear)
    d.ellipse([cx - 95, 450, cx + 95, 600], fill=tuple(min(255, v + 40) for v in fur))  # muzzle
    d.ellipse([cx - 35, 455, cx + 35, 505], fill=(30, 25, 25))        # nose
    eyes(d, cx, 390, 75)
    d.rectangle([cx - 150, 600, cx + 150, 640], fill=collar)          # collar
    d.ellipse([cx - 22, 625, cx + 22, 670], fill=(250, 200, 40))      # tag
    save(img, name)


def parrot(name, seed):
    rnd = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    ground(d, rnd, (170, 220, 245), (120, 170, 110))
    cx = 450
    d.rectangle([150, 900, 750, 930], fill=(120, 80, 40))             # branch
    d.polygon([(cx - 60, 860), (cx + 60, 860), (cx + 20, 1150), (cx - 20, 1150)], fill=(30, 120, 220))  # tail
    d.ellipse([cx - 170, 450, cx + 170, 920], fill=(40, 180, 70))     # body
    d.ellipse([cx - 150, 520, cx - 20, 860], fill=(30, 140, 60))      # wing
    d.ellipse([cx - 140, 220, cx + 140, 500], fill=(40, 180, 70))     # head
    d.ellipse([cx - 80, 300, cx + 120, 470], fill=(250, 220, 60))     # face patch
    d.polygon([(cx + 100, 330), (cx + 200, 380), (cx + 110, 440)], fill=(60, 60, 60))  # beak
    eyes(d, cx + 30, 350, 0, size=26)
    save(img, name)


def rabbit(name, fur, seed):
    rnd = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    ground(d, rnd, (190, 225, 240), (110, 175, 90))
    cx = 450
    d.ellipse([cx - 230, 600, cx + 230, 1080], fill=fur)              # body
    d.ellipse([cx + 190, 840, cx + 290, 940], fill=(250, 250, 250))   # tail
    d.ellipse([cx - 160, 360, cx + 160, 680], fill=fur)               # head
    d.ellipse([cx - 120, 60, cx - 40, 420], fill=fur)                 # ears
    d.ellipse([cx + 40, 60, cx + 120, 420], fill=fur)
    d.ellipse([cx - 100, 100, cx - 60, 380], fill=(240, 180, 190))
    d.ellipse([cx + 60, 100, cx + 100, 380], fill=(240, 180, 190))
    eyes(d, cx, 500, 70, color=(60, 30, 30))
    d.polygon([(cx - 18, 560), (cx + 18, 560), (cx, 585)], fill=(230, 130, 150))
    save(img, name)


def kitten(name, fur, seed, patch=None):
    rnd = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    ground(d, rnd, (245, 225, 200), (205, 195, 170))
    cx = 450
    d.ellipse([cx - 200, 620, cx + 200, 1060], fill=fur)
    d.ellipse([cx - 180, 300, cx + 180, 660], fill=fur)
    d.polygon([(cx - 170, 400), (cx - 150, 200), (cx - 50, 330)], fill=fur)
    d.polygon([(cx + 170, 400), (cx + 150, 200), (cx + 50, 330)], fill=fur)
    if patch:
        d.ellipse([cx - 90, 480, cx + 90, 650], fill=patch)
        d.ellipse([cx - 100, 700, cx + 100, 1040], fill=patch)
    eyes(d, cx, 460, 70, color=(60, 120, 60), size=30)
    d.polygon([(cx - 14, 540), (cx + 14, 540), (cx, 558)], fill=(230, 140, 140))
    save(img, name)


dog("dog_saluki", (196, 150, 100), (150, 105, 65), (220, 40, 40), 11)
dog("dog_husky", (150, 155, 165), (90, 95, 105), (40, 90, 220), 12)
parrot("bird_parrot", 13)
rabbit("rabbit_white", (245, 245, 245), 14)
kitten("kitten_grey", (140, 145, 155), 15)
kitten("kitten_calico", (230, 150, 70), 16, patch=(250, 250, 245))
print("ok")
