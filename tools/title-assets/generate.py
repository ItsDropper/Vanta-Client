#!/usr/bin/env python3
"""Generates the title-screen textures: the Vanta mark, monochrome icons and edge scrims.
Icons are white on transparent so the client can tint them. Run: python3 generate.py OUTDIR"""
import sys, os, math
from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else 'out'
os.makedirs(OUT, exist_ok=True)
SS = 8

def canvas(n):
    s = n * SS
    return Image.new('RGBA', (s, s), (0, 0, 0, 0)), s

def save(img, name, n):
    img = img.resize((n, n), Image.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    with open(path + '.mcmeta', 'w') as f:
        f.write('{"texture":{"blur":true,"clamp":true}}\n')

def line(d, pts, w, fill):
    d.line(pts, fill=fill, width=int(w), joint='curve')
    r = w / 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=fill)

def mark(n=256):
    img, s = canvas(n)
    mask = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(mask)
    w = s * 0.19
    pts = [(s * 0.17, s * 0.22), (s * 0.5, s * 0.80), (s * 0.83, s * 0.22)]
    d.line(pts, fill=255, width=int(w), joint='curve')
    r = w / 2
    for x, y in (pts[0], pts[1], pts[2]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    # a short inner stroke gives the mark a distinct, minimal silhouette
    d.rectangle([0, 0, 0, 0], fill=0)
    grad = Image.new('RGBA', (s, s))
    px = grad.load()
    top = (124, 192, 255); bot = (47, 107, 255)
    for y in range(s):
        t = min(1.0, max(0.0, (y - s * 0.15) / (s * 0.7)))
        c = tuple(int(top[i] + (bot[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(s):
            px[x, y] = c
    img.paste(grad, (0, 0), mask)
    save(img, 'mark.png', n)

def icon(name, draw_fn, n=64):
    img, s = canvas(n)
    d = ImageDraw.Draw(img)
    draw_fn(d, s)
    save(img, name, n)

W = (255, 255, 255, 255)

def chat(d, s):
    sw = s * 0.075
    d.rounded_rectangle([s * .16, s * .20, s * .84, s * .70], radius=s * .16, outline=W, width=int(sw))
    d.polygon([(s * .34, s * .66), (s * .30, s * .84), (s * .52, s * .69)], fill=W)
    for cx in (.36, .50, .64):
        d.ellipse([s * cx - s * .042, s * .45 - s * .042, s * cx + s * .042, s * .45 + s * .042], fill=W)

def bell(d, s):
    sw = int(s * .075)
    d.pieslice([s * .24, s * .16, s * .76, s * .68], 180, 360, fill=W)
    d.polygon([(s * .24, s * .42), (s * .76, s * .42), (s * .82, s * .70), (s * .18, s * .70)], fill=W)
    d.rounded_rectangle([s * .14, s * .66, s * .86, s * .74], radius=s * .04, fill=W)
    d.ellipse([s * .41, s * .76, s * .59, s * .90], fill=W)
    d.ellipse([s * .46, s * .08, s * .54, s * .18], fill=W)

def grid(d, s):
    a, b, g = .2, .44, .12
    for x in (a, a + b + g - .12 + .12):
        pass
    size = s * .26
    for ox in (.19, .55):
        for oy in (.19, .55):
            d.rounded_rectangle([s * ox, s * oy, s * ox + size, s * oy + size], radius=s * .07, fill=W)

def chevron(d, s):
    line(d, [(s * .26, s * .38), (s * .5, s * .62), (s * .74, s * .38)], s * .1, W)

def check(d, s):
    line(d, [(s * .24, s * .54), (s * .43, s * .72), (s * .78, s * .30)], s * .1, W)

def clear(d, s):
    line(d, [(s * .28, s * .28), (s * .72, s * .72)], s * .09, W)
    line(d, [(s * .72, s * .28), (s * .28, s * .72)], s * .09, W)

mark()
icon('chat.png', chat); icon('bell.png', bell); icon('grid.png', grid)
icon('chevron.png', chevron); icon('check.png', check); icon('close.png', clear)

def ramp(name, w, h, horizontal, strength, power):
    img = Image.new('RGBA', (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / (w - 1)) if horizontal else (y / (h - 1))
            a = int(255 * strength * (1 - t) ** power)
            px[x, y] = (4, 7, 14, a)
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    with open(path + '.mcmeta', 'w') as f:
        f.write('{"texture":{"blur":true,"clamp":true}}\n')

ramp('scrim_left.png', 256, 4, True, 0.86, 1.7)
ramp('scrim_bottom.png', 4, 128, False, 0.0, 1)  # replaced below
# bottom scrim is darkest at the bottom edge: invert the vertical ramp
img = Image.new('RGBA', (4, 128)); px = img.load()
for y in range(128):
    a = int(255 * 0.78 * (y / 127) ** 1.9)
    for x in range(4): px[x, y] = (4, 7, 14, a)
img.save(os.path.join(OUT, 'scrim_bottom.png'), optimize=True)
img = Image.new('RGBA', (4, 128)); px = img.load()
for y in range(128):
    a = int(255 * 0.55 * (1 - y / 127) ** 2.0)
    for x in range(4): px[x, y] = (4, 7, 14, a)
img.save(os.path.join(OUT, 'scrim_top.png'), optimize=True)
for n in ('scrim_top.png',):
    open(os.path.join(OUT, n + '.mcmeta'), 'w').write('{"texture":{"blur":true,"clamp":true}}\n')
print(sorted(os.listdir(OUT)))
