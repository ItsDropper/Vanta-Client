"""Soft lighting textures for the title screen: vignette, radial glow, light shaft."""
import json, os
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "client", "resources",
                   "assets", "vanta", "textures", "gui", "title")

def save(name, alpha):
    img = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    path = os.path.join(OUT, name + ".png")
    Image.fromarray(img, "RGBA").save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)

def smooth(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)

n = 192
ys, xs = np.mgrid[0:n, 0:n]
u = (xs + 0.5) / n * 2 - 1
v = (ys + 0.5) / n * 2 - 1
d = np.sqrt(u * u + v * v) / np.sqrt(2)
# Vignette is white here and tinted at draw time; strongest in the corners, clear in the middle.
save("vignette", smooth((d - 0.42) / 0.58) ** 1.3)

m = 128
ys, xs = np.mgrid[0:m, 0:m]
r = np.sqrt(((xs + 0.5) / m * 2 - 1) ** 2 + ((ys + 0.5) / m * 2 - 1) ** 2)
save("glow", (1 - smooth(r)) ** 1.6)

w, h = 64, 256
ys, xs = np.mgrid[0:h, 0:w]
x = (xs + 0.5) / w * 2 - 1
y = (ys + 0.5) / h
save("shaft", np.exp(-(x * 1.9) ** 2) * (1 - smooth(y)) ** 1.2)
print("ok")
