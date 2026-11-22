"""Generates anti-aliased superellipse (n=4) corner textures used by VantaUiTheme.

Each texture is 2S x 2S: a full rounded square of corner radius S, so the four
quadrants can be blitted as corners. 'fill_S' are solid; 'ring_S_k' are outlines
whose thickness is S/RING_DENOMINATORS[k] pixels in the texture.
"""
import json, os, sys
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "client", "resources",
                   "assets", "vanta", "textures", "gui", "shape")
LEVELS = [4, 8, 16, 32, 64, 128]
RING_DENOMINATORS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 20, 24, 32]
SS = 8  # supersampling per axis

def inside(px, py, S, radius):
    """Coverage mask of a superellipse-cornered square of size 2S, corner radius `radius`,
    inset so that its outer edge is at `inset` (handled by caller via radius/offset)."""
    raise NotImplementedError

def coverage(S, inset, radius):
    n = 2 * S
    ys = (np.arange(n * SS) + 0.5) / SS
    xs = (np.arange(n * SS) + 0.5) / SS
    X, Y = np.meshgrid(xs, ys)
    # distance into the shape from each edge
    cx = np.minimum(X, n - X) - inset  # >=0 inside straight region
    cy = np.minimum(Y, n - Y) - inset
    mask = np.zeros_like(X, dtype=bool)
    if radius <= 0:
        mask = (cx >= 0) & (cy >= 0)
    else:
        corner = (cx < radius) & (cy < radius)
        nx = np.clip((radius - cx) / radius, 0, None)
        ny = np.clip((radius - cy) / radius, 0, None)
        inside_corner = (nx ** 4 + ny ** 4) <= 1.0
        mask = np.where(corner, inside_corner & (cx >= 0) & (cy >= 0),
                        (cx >= 0) & (cy >= 0))
    mask = mask.reshape(n, SS, n, SS).mean(axis=(1, 3))
    return mask

def save(name, alpha):
    img = np.zeros((alpha.shape[0], alpha.shape[1], 4), dtype=np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    path = os.path.join(OUT, name + ".png")
    Image.fromarray(img, "RGBA").save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)

def main():
    os.makedirs(OUT, exist_ok=True)
    for S in LEVELS:
        save(f"fill_{S}", coverage(S, 0, S))
        for k, b in enumerate(RING_DENOMINATORS):
            tau = S / b
            outer = coverage(S, 0, S)
            inner = coverage(S, tau, max(S - tau, 0.0))
            save(f"ring_{S}_{k}", np.clip(outer - inner, 0, 1))
    print("ok")

if __name__ == "__main__":
    main()
