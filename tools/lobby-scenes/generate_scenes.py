#!/usr/bin/env python3
"""Renders Vanta's lobby backdrops.

Each biome is a small procedural voxel world that is ray-traced into depth-separated RGBA layers
(sky, clouds, far, mid, near, front). Every layer is rendered as if the nearer geometry were not
there, so shifting the layers against each other in the menu reveals real terrain instead of holes.

    python3 generate_scenes.py [biome ...] [--out DIR] [--preview]

Requires numpy, scipy, pillow and numba. The generated PNGs are committed; this script is kept so
the art is reproducible and the palettes can be tuned.
"""
import math, os, sys, time
import numpy as np
from numba import njit, prange
from scipy.ndimage import zoom, gaussian_filter
from PIL import Image

NX, NY, NZ = 640, 128, 640
AIR, GRASS, DIRT, STONE, SAND, SNOW, WATER, LOG, LEAVES, SPRUCE, CHERRY, SANDSTONE, ICE, MESA, CACTUS, GRAVEL, PODZOL, PETAL, SPRUCELOG, CHERRYLOG, DEADBUSH, FLOWER_R, FLOWER_Y = range(23)
NBLOCKS = 23

def rgb(h):
    return [((h >> 16) & 255) / 255.0, ((h >> 8) & 255) / 255.0, (h & 255) / 255.0]

def palette(entries):
    top = np.zeros((NBLOCKS, 3)); side = np.zeros((NBLOCKS, 3)); bot = np.zeros((NBLOCKS, 3)); var = np.zeros(NBLOCKS)
    for block, (t, s, b, v) in entries.items():
        top[block] = rgb(t); side[block] = rgb(s); bot[block] = rgb(b); var[block] = v
    return top, side, bot, var

def fbm(n, seed, octaves=5, base=3, persistence=0.5):
    rng = np.random.default_rng(seed)
    out = np.zeros((n, n)); amp = 1.0; total = 0.0
    for o in range(octaves):
        cells = base * 2 ** o
        lattice = rng.random((cells + 3, cells + 3))
        field = zoom(lattice, (n / cells, n / cells), order=3)[:n, :n]
        out += field * amp; total += amp; amp *= persistence
    return out / total

def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)

# ---------------------------------------------------------------- world generation

class World:
    def __init__(self, cfg, seed):
        self.cfg = cfg
        self.rng = np.random.default_rng(seed)
        self.grid = np.zeros((NX, NY, NZ), dtype=np.uint8)
        self.build_height(seed)
        self.fill_columns()
        self.plant()

    def build_height(self, seed):
        c = self.cfg
        n = NX
        xs = np.arange(n)[:, None] * np.ones((1, n)); zs = np.ones((n, 1)) * np.arange(n)[None, :]
        rolling = fbm(n, seed, 5, 3) - 0.5
        detail = fbm(n, seed + 1, 3, 12) - 0.5
        h = c['base'] + rolling * c['roll'] + detail * c['detail']
        ridge = 1.0 - np.abs(fbm(n, seed + 2, 4, 4) - 0.5) * 2.0
        mountains = ridge ** c['ridge_pow'] * c['mountain'] * smooth(0, 1, (zs - c['mount_start']) / c['mount_ramp'])
        h = h + mountains
        # side hills framing the middle of the view, clear corridor in the centre
        side = np.abs(xs - NX / 2) / (NX / 2)
        h += (smooth(0.12, 0.7, side)) * c['side_hills'] * (fbm(n, seed + 3, 3, 5))
        # flat plaza under the camera so a character can stand there
        plaza = 1.0 - smooth(18, 95, zs) * 1.0
        plaza *= 1.0 - smooth(20, 70, np.abs(xs - NX / 2))
        h = h * (1 - plaza) + c['base'] * plaza
        # lake
        if c.get('lake'):
            lx, lz, lr, ld = c['lake']
            d = np.hypot(xs - lx, zs - lz) / lr
            h -= smooth(1.15, 0.2, d) * ld
        self.height = np.clip(np.round(h), 2, NY - 20).astype(np.int32)

    def fill_columns(self):
        c = self.cfg; g = self.grid; hm = self.height
        gx, gz = np.gradient(hm.astype(float))
        slope = np.hypot(gx, gz)
        wl = c['water']
        noise = fbm(NX, 99, 4, 20)
        for x in range(NX):
            col = hm[x]
            for z in range(NZ):
                top = col[z]
                g[x, :top - 3, z] = STONE
                g[x, top - 3:top, z] = c['sub']
                surf = c['surface']
                if top > c['snowline'] + noise[x, z] * 6 and c.get('snowcap'):
                    surf = SNOW
                elif slope[x, z] > c['rock_slope']:
                    surf = STONE
                elif top <= wl + 1:
                    surf = c['shore']
                g[x, top, z] = surf
                if top < wl:
                    g[x, top + 1:wl + 1, z] = c['liquid']
        self.surface_y = hm

    def blob(self, cx, cy, cz, rx, ry, rz, block, hole=0.0):
        g = self.grid
        for x in range(max(0, int(cx - rx)), min(NX, int(cx + rx) + 1)):
            for y in range(max(0, int(cy - ry)), min(NY, int(cy + ry) + 1)):
                for z in range(max(0, int(cz - rz)), min(NZ, int(cz + rz) + 1)):
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1.0 and g[x, y, z] in (AIR,) and self.rng.random() > hole:
                        g[x, y, z] = block

    def tree(self, x, z, kind):
        r = self.rng; g = self.grid
        y = self.height[x, z] + 1
        if y >= NY - 30 or g[x, y - 1, z] in (WATER, ICE, STONE, SNOW) and kind != 'spruce':
            return
        if kind == 'oak':
            h = int(r.integers(5, 8)); g[x, y:y + h, z] = LOG
            rad = int(r.integers(3, 5))
            self.blob(x, y + h, z, rad, 2.6, rad, LEAVES, 0.05)
            self.blob(x, y + h + 2, z, rad - 1, 1.6, rad - 1, LEAVES, 0.05)
        elif kind == 'birch':
            h = int(r.integers(6, 9)); g[x, y:y + h, z] = LOG
            self.blob(x, y + h, z, 3, 2.4, 3, LEAVES, 0.05)
        elif kind == 'spruce':
            h = int(r.integers(8, 14)); g[x, y:y + h, z] = SPRUCELOG
            layers = h + 1; top = y + h + 1
            for i in range(layers):
                rad = max(0, int(round((1 - i / layers) * (h * 0.32 + 1)))) if i % 2 == 0 else max(0, int(round((1 - i / layers) * (h * 0.32 + 1))) - 1)
                yy = top - i - 1
                self.blob(x, yy, z, max(rad, 0) + 0.5, 0.6, max(rad, 0) + 0.5, SPRUCE, 0.0)
        elif kind == 'cherry':
            h = int(r.integers(4, 7)); g[x, y:y + h, z] = CHERRYLOG
            rad = int(r.integers(4, 6))
            self.blob(x + int(r.integers(-1, 2)), y + h, z, rad, 2.8, rad, CHERRY, 0.08)
            self.blob(x + 2, y + h + 1, z - 1, rad - 1, 2.0, rad - 1, CHERRY, 0.08)
        elif kind == 'cactus':
            h = int(r.integers(2, 5)); g[x, y:y + h, z] = CACTUS
        elif kind == 'bush':
            self.blob(x, y, z, 1.8, 1.2, 1.8, LEAVES, 0.1)

    def plant(self):
        c = self.cfg; r = self.rng
        trees = c['trees']
        for kind, density, zmin, zmax in trees:
            count = int(density * NX * NZ / 10000)
            for _ in range(count):
                x = int(r.integers(8, NX - 8)); z = int(r.integers(zmin, zmax))
                # keep a corridor free for the character and a clear horizon view
                if abs(x - NX / 2) < 22 and z < 85:
                    continue
                if self.grid[x, self.height[x, z], z] in (WATER, ICE):
                    continue
                self.tree(x, z, kind)
        # framing trees close to the camera
        for kind, offsets in c.get('frame', []):
            for dx, z in offsets:
                x = int(NX / 2 + dx)
                self.tree(x, z, kind)
        for block, density in c.get('decor', []):
            for _ in range(int(density * NX * NZ / 10000)):
                x = int(r.integers(0, NX)); z = int(r.integers(0, NZ)); y = self.height[x, z] + 1
                if self.grid[x, y - 1, z] in (GRASS, SAND, SNOW) and self.grid[x, y, z] == AIR:
                    self.grid[x, y, z] = block

# ---------------------------------------------------------------- ray tracing

@njit(cache=True, fastmath=True)
def hash3(x, y, z, a):
    h = (x * 374761393 + y * 668265263 + z * 2147483647 + a * 1274126177) & 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFF) / 65535.0

@njit(cache=True, fastmath=True)
def sky_color(dx, dy, dz, sun, zen, hor, sunc, glow):
    t = max(dy, 0.0) ** 0.55
    r = hor[0] + (zen[0] - hor[0]) * t; g = hor[1] + (zen[1] - hor[1]) * t; b = hor[2] + (zen[2] - hor[2]) * t
    s = dx * sun[0] + dy * sun[1] + dz * sun[2]
    if s > 0.0:
        a = (s ** 6) * 0.22 * glow; bb = (s ** 40) * 0.45 * glow
        r += sunc[0] * (a + bb); g += sunc[1] * (a + bb); b += sunc[2] * (a + bb)
    # low haze band at the horizon
    hz = math.exp(-abs(dy) * 7.0) * 0.18
    r += (hor[0] - r) * hz; g += (hor[1] - g) * hz; b += (hor[2] - b) * hz
    return r, g, b

@njit(cache=True, fastmath=True)
def shade_hit(grid, bx, by, bz, face, px, py, pz, dx, dy, dz, t, pal_top, pal_side, pal_bot, var,
              sun, sunc, amb, fogc, fogd, zen, hor, water_color, glow):
    block = grid[bx, by, bz]
    fx = px - bx; fy = py - by; fz = pz - bz
    # texel inside a 16x16 face
    if face == 0 or face == 1:
        u = fx; v = fz
    elif face == 2 or face == 3:
        u = fz; v = fy
    else:
        u = fx; v = fy
    tx = min(15, int(u * 16.0)); ty = min(15, int(v * 16.0))
    n = hash3(bx, by, bz, face * 256 + tx * 16 + ty)
    n2 = hash3(bx // 1, by, bz, tx * 31 + ty * 17 + 7)
    if face == 0:
        base = pal_top[block]
    elif face == 1:
        base = pal_bot[block]
    else:
        base = pal_side[block]
    cr = base[0]; cg = base[1]; cb = base[2]
    if block == 1 and face >= 2:  # grass side: green fringe over dirt
        if fy > 0.8125 or (fy > 0.69 and n > 0.55):
            cr = pal_top[1][0]; cg = pal_top[1][1]; cb = pal_top[1][2]
        else:
            cr = pal_side[1][0]; cg = pal_side[1][1]; cb = pal_side[1][2]
    amt = var[block]
    k = 1.0 - amt + 2.0 * amt * n
    if block == 8 or block == 9 or block == 10:  # leaves: dark pits
        if n2 < 0.22:
            k *= 0.62
    if block == 7 or block == 18 or block == 19:  # logs: bark stripes on the sides, rings on top
        if face >= 2:
            k *= 0.82 + 0.3 * hash3(bx, by, bz, tx * 3 + 1)
    cr *= k; cg *= k; cb *= k
    # lighting
    if face == 0:
        nx = 0.0; ny = 1.0; nz = 0.0
    elif face == 1:
        nx = 0.0; ny = -1.0; nz = 0.0
    elif face == 2:
        nx = 1.0; ny = 0.0; nz = 0.0
    elif face == 3:
        nx = -1.0; ny = 0.0; nz = 0.0
    elif face == 4:
        nx = 0.0; ny = 0.0; nz = 1.0
    else:
        nx = 0.0; ny = 0.0; nz = -1.0
    if block == 6 or block == 12:
        # water / ice: reflect the sky, add sun glints
        rx = dx; ry = -dy; rz = dz
        sr, sg, sb = sky_color(rx, ry, rz, sun, zen, hor, sunc, glow)
        fres = 0.25 + 0.55 * (1.0 - min(1.0, abs(dy) * 2.2)) ** 3
        if block == 12:
            fres = 0.18 + 0.4 * (1.0 - min(1.0, abs(dy) * 2.2)) ** 3
        cr = water_color[0] * (1 - fres) + sr * fres
        cg = water_color[1] * (1 - fres) + sg * fres
        cb = water_color[2] * (1 - fres) + sb * fres
        if block == 12:
            cr = cr * 0.75 + 0.25 * base[0] * k; cg = cg * 0.75 + 0.25 * base[1] * k; cb = cb * 0.75 + 0.25 * base[2] * k
        s = rx * sun[0] + ry * sun[1] + rz * sun[2]
        if s > 0.0:
            gl = (s ** 220) * 1.6
            cr += sunc[0] * gl; cg += sunc[1] * gl; cb += sunc[2] * gl
        diff = 1.0
    else:
        diff = max(0.0, nx * sun[0] + ny * sun[1] + nz * sun[2])
        # cheap hard shadow toward the sun
        shadow = 1.0
        if diff > 0.0:
            sx = px + nx * 0.02; sy = py + ny * 0.02; sz = pz + nz * 0.02
            for i in range(1, 44):
                sx += sun[0] * 0.9; sy += sun[1] * 0.9; sz += sun[2] * 0.9
                ix = int(sx); iy = int(sy); iz = int(sz)
                if ix < 0 or iz < 0 or ix >= grid.shape[0] or iz >= grid.shape[2] or iy >= grid.shape[1]:
                    break
                if iy >= 0:
                    sb = grid[ix, iy, iz]
                    if sb != 0 and sb != 6 and sb != 12:
                        shadow = 0.0
                        break
        lit = diff * shadow
        sideshade = 1.0
        if face == 1:
            sideshade = 0.5
        elif face >= 2:
            sideshade = 0.82
        lr = amb[0] * sideshade + sunc[0] * lit
        lg = amb[1] * sideshade + sunc[1] * lit
        lb = amb[2] * sideshade + sunc[2] * lit
        cr *= lr; cg *= lg; cb *= lb
    # distance fog
    f = 1.0 - math.exp(-((t / fogd) ** 1.35))
    cr = cr + (fogc[0] - cr) * f
    cg = cg + (fogc[1] - cg) * f
    cb = cb + (fogc[2] - cb) * f
    return cr, cg, cb

@njit(cache=True, fastmath=True)
def trace(grid, ox, oy, oz, dx, dy, dz, tmin, tmax):
    """Returns (hit, bx, by, bz, face, t). Starts at tmin along the ray."""
    px = ox + dx * tmin; py = oy + dy * tmin; pz = oz + dz * tmin
    ix = int(math.floor(px)); iy = int(math.floor(py)); iz = int(math.floor(pz))
    sx = 1 if dx > 0 else -1; sy = 1 if dy > 0 else -1; sz = 1 if dz > 0 else -1
    inf = 1e30
    tdx = abs(1.0 / dx) if dx != 0 else inf
    tdy = abs(1.0 / dy) if dy != 0 else inf
    tdz = abs(1.0 / dz) if dz != 0 else inf
    nxt = (ix + (1 if sx > 0 else 0) - px) / dx if dx != 0 else inf
    nyt = (iy + (1 if sy > 0 else 0) - py) / dy if dy != 0 else inf
    nzt = (iz + (1 if sz > 0 else 0) - pz) / dz if dz != 0 else inf
    t = tmin
    face = 0
    nx_, ny_, nz_ = grid.shape
    first = True
    while t < tmax:
        if ix < 0 or iz < 0 or ix >= nx_ or iz >= nz_ or iy < 0 or iy >= ny_:
            if iy >= ny_ and dy >= 0:
                return 0, 0, 0, 0, 0, t
            if iy < 0 or ix < 0 or iz < 0 or ix >= nx_ or iz >= nz_:
                return 0, 0, 0, 0, 0, t
        else:
            if grid[ix, iy, iz] != 0:
                return 1, ix, iy, iz, face, t
        if nxt < nyt and nxt < nzt:
            t = tmin + nxt; ix += sx; face = 3 if sx > 0 else 2; nxt += tdx
        elif nyt < nzt:
            t = tmin + nyt; iy += sy; face = 1 if sy > 0 else 0; nyt += tdy
        else:
            t = tmin + nzt; iz += sz; face = 5 if sz > 0 else 4; nzt += tdz
    return 0, 0, 0, 0, 0, t

@njit(parallel=True, cache=True, fastmath=True)
def render_layer(grid, W, H, SS, cam, f, r, u, tanx, tany, tmin, tmax, mode,
                 pal_top, pal_side, pal_bot, var, sun, sunc, amb, fogc, fogd, zen, hor, water_color, glow):
    out = np.zeros((H, W, 4))
    for j in prange(H):
        for i in range(W):
            ar = 0.0; ag = 0.0; ab = 0.0; aa = 0.0
            for sj in range(SS):
                for si in range(SS):
                    sxn = ((i + (si + 0.5) / SS) / W) * 2.0 - 1.0
                    syn = 1.0 - ((j + (sj + 0.5) / SS) / H) * 2.0
                    dx = f[0] + r[0] * sxn * tanx + u[0] * syn * tany
                    dy = f[1] + r[1] * sxn * tanx + u[1] * syn * tany
                    dz = f[2] + r[2] * sxn * tanx + u[2] * syn * tany
                    ln = math.sqrt(dx * dx + dy * dy + dz * dz)
                    dx /= ln; dy /= ln; dz /= ln
                    if mode == 0:  # sky
                        cr, cg, cb = sky_color(dx, dy, dz, sun, zen, hor, sunc, glow)
                        # square pixel sun like the game's
                        s = dx * sun[0] + dy * sun[1] + dz * sun[2]
                        if s > 0.9985:
                            ax = dx - sun[0] * s; ay = dy - sun[1] * s; az = dz - sun[2] * s
                            if abs(ax) < 0.034 and abs(ay) < 0.034 and abs(az) < 0.034:
                                cr = 1.0; cg = 0.97; cb = 0.86
                        ar += cr; ag += cg; ab += cb; aa += 1.0
                    else:
                        hit, bx, by, bz, face, t = trace(grid, cam[0], cam[1], cam[2], dx, dy, dz, tmin, tmax)
                        if hit == 1:
                            px = cam[0] + dx * t; py = cam[1] + dy * t; pz = cam[2] + dz * t
                            cr, cg, cb = shade_hit(grid, bx, by, bz, face, px, py, pz, dx, dy, dz, t,
                                                    pal_top, pal_side, pal_bot, var, sun, sunc, amb, fogc, fogd,
                                                    zen, hor, water_color, glow)
                            ar += cr; ag += cg; ab += cb; aa += 1.0
            n = SS * SS
            if aa > 0.0:
                out[j, i, 0] = ar / n; out[j, i, 1] = ag / n; out[j, i, 2] = ab / n; out[j, i, 3] = aa / n
    return out

# ---------------------------------------------------------------- composition

def basis(yaw, pitch):
    f = np.array([math.sin(yaw) * math.cos(pitch), math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    r = np.cross([0, 1, 0], f); r /= np.linalg.norm(r)
    u = np.cross(f, r)
    return f, r, u

def tonemap(img, sat=1.08, contrast=1.04):
    lum = img[..., :3] @ np.array([0.299, 0.587, 0.114])
    img[..., :3] = lum[..., None] + (img[..., :3] - lum[..., None]) * sat
    img[..., :3] = (img[..., :3] - 0.5) * contrast + 0.5
    img[..., :3] = np.clip(img[..., :3], 0, 1.0)
    # soft highlight roll-off
    img[..., :3] = 1.0 - np.exp(-img[..., :3] * 1.7) * 0.0 + 0 * img[..., :3]
    return img

def finish(layer, blur, sat):
    """Blur premultiplied RGBA (depth of field), tone map and convert to straight-alpha 8 bit."""
    a = layer[..., 3:4]
    pre = layer.copy(); pre[..., :3] *= a
    if blur > 0:
        for c in range(4):
            pre[..., c] = gaussian_filter(pre[..., c], blur)
    alpha = pre[..., 3]
    col = np.where(alpha[..., None] > 1e-4, pre[..., :3] / np.maximum(alpha[..., None], 1e-4), 0.0)
    lum = col @ np.array([0.299, 0.587, 0.114])
    col = lum[..., None] + (col - lum[..., None]) * sat
    col = np.clip(col, 0, 1)
    # gentle filmic curve
    col = np.clip(col * 1.04, 0, 1.0)
    col = col ** (1 / 1.04)
    out = np.dstack([col, alpha])
    return Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA')

def make_clouds(cfg, w=1820, h=312, seed=5):
    rng = np.random.default_rng(seed)
    cell = 26
    cw, ch = w // cell, h // cell
    lattice = rng.random((ch + 4, cw))
    def periodic(scale, offs):
        # periodic bilinear value noise in x, plain in y
        gw, gh = max(2, int(cw * scale / 10)), max(2, int(ch * scale / 5))
        grid = np.random.default_rng(seed + offs).random((gh + 2, gw))
        xs = np.linspace(0, gw, cw, endpoint=False); ys = np.linspace(0, gh, ch, endpoint=False)
        x0 = np.floor(xs).astype(int); fx = xs - x0; y0 = np.floor(ys).astype(int); fy = ys - y0
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
        a = grid[y0][:, x0 % gw]; b = grid[y0][:, (x0 + 1) % gw]
        c = grid[y0 + 1][:, x0 % gw]; d = grid[y0 + 1][:, (x0 + 1) % gw]
        top = a * (1 - fx)[None, :] + b * fx[None, :]; bot = c * (1 - fx)[None, :] + d * fx[None, :]
        return top * (1 - fy)[:, None] + bot * fy[:, None]
    n = periodic(1, 1) * 0.6 + periodic(2, 2) * 0.3 + periodic(4, 3) * 0.2
    n -= n.min(); n /= n.max()
    mask = (n > cfg['cloud_cover']).astype(float)
    # fade near top and bottom so the strip has no hard horizontal edges
    ys = np.linspace(0, 1, ch)[:, None]
    mask *= smooth(0.0, 0.18, ys) * smooth(1.0, 0.7, ys)
    mask = (mask > 0.5).astype(float)
    big = np.kron(mask, np.ones((cell, cell)))
    rgb_img = np.zeros((h, w, 3))
    base = np.array(cfg['cloud_color']); shade = np.array(cfg['cloud_shade'])
    # underside shading: lower rows of each cloud darker
    below = np.roll(mask, -1, axis=0)
    edge = (mask > 0) & (below == 0)
    shade_mask = np.kron(edge.astype(float), np.ones((cell, cell)))
    rgb_img[:] = base
    rgb_img = rgb_img * (1 - shade_mask[..., None] * 0.9) + shade * shade_mask[..., None] * 0.9
    alpha = np.kron(mask, np.ones((cell, cell))) * cfg['cloud_alpha']
    alpha = gaussian_filter(alpha, 0.8)
    return Image.fromarray((np.dstack([rgb_img, alpha]) * 255 + 0.5).astype(np.uint8), 'RGBA')

BIOMES = {}

BIOMES['meadow'] = dict(
    title='Meadow', seed=11, base=56, roll=26, detail=5, ridge_pow=2.2, mountain=52, mount_start=240, mount_ramp=200, side_hills=26,
    lake=(NX / 2 + 52, 170, 52, 11), water=52, sub=DIRT, surface=GRASS, shore=SAND, liquid=WATER, snowline=98, snowcap=True, rock_slope=2.0,
    trees=[('oak', 5.0, 90, 400), ('birch', 1.2, 90, 400), ('oak', 2.0, 400, 620)],
    frame=[('oak', [(-38, 44), (-27, 63), (42, 52), (30, 78), (-52, 90), (54, 100)]), ('birch', [(-46, 36)])],
    decor=[(FLOWER_R, 1.5), (FLOWER_Y, 1.5)],
    pal={GRASS: (0x79c05a, 0x866043, 0x866043, 0.07), DIRT: (0x866043,) * 3 + (0.07,), STONE: (0x7d7d7d, 0x7d7d7d, 0x6e6e6e, 0.09),
         SAND: (0xdbd3a0,) * 3 + (0.05,), SNOW: (0xf6fbff,) * 3 + (0.03,), WATER: (0x2f6bd6,) * 3 + (0.0,), LOG: (0x9c7f4d, 0x6b5233, 0x6b5233, 0.1),
         LEAVES: (0x4fa23a,) * 3 + (0.1,), FLOWER_R: (0xd8352f,) * 3 + (0.1,), FLOWER_Y: (0xf1d238,) * 3 + (0.1,)},
    sun=(-0.45, 0.62, 0.64), sunc=(1.05, 0.97, 0.85), amb=(0.52, 0.6, 0.72), zen=(0.24, 0.47, 0.84), hor=(0.78, 0.88, 0.97), fog=(0.74, 0.85, 0.95), fogd=420,
    water_color=(0.10, 0.27, 0.58), glow=1.0, cloud_cover=0.58, cloud_color=(1, 1, 1), cloud_shade=(0.78, 0.84, 0.93), cloud_alpha=0.92,
    yaw=0.05, pitch=-0.15, cam=(NX / 2, 0, 8), cam_h=16, sat=1.12)

BIOMES['taiga'] = dict(
    title='Snowy taiga', seed=23, base=58, roll=22, detail=4, ridge_pow=2.0, mountain=58, mount_start=230, mount_ramp=190, side_hills=24,
    lake=(NX / 2 - 60, 160, 50, 9), water=54, sub=DIRT, surface=SNOW, shore=SNOW, liquid=ICE, snowline=0, snowcap=False, rock_slope=2.2,
    trees=[('spruce', 7.0, 90, 460), ('spruce', 3.0, 460, 620)],
    frame=[('spruce', [(-36, 48), (-26, 70), (40, 46), (28, 66), (-54, 92), (52, 88), (62, 60)])],
    decor=[],
    pal={SNOW: (0xf4f9ff, 0xe4eef8, 0xe4eef8, 0.03), DIRT: (0x7a5c44,) * 3 + (0.06,), STONE: (0x8a8d93, 0x8a8d93, 0x777a80, 0.08),
         ICE: (0x9cc4f0,) * 3 + (0.03,), SPRUCE: (0x2d5a3f,) * 3 + (0.1,), SPRUCELOG: (0x5a4128, 0x45311e, 0x45311e, 0.08)},
    sun=(0.5, 0.34, 0.8), sunc=(0.85, 0.78, 0.88), amb=(0.56, 0.64, 0.82), zen=(0.17, 0.27, 0.52), hor=(0.72, 0.8, 0.93), fog=(0.66, 0.75, 0.9), fogd=330,
    water_color=(0.2, 0.32, 0.55), glow=0.7, cloud_cover=0.5, cloud_color=(0.95, 0.97, 1.0), cloud_shade=(0.65, 0.72, 0.88), cloud_alpha=0.85,
    yaw=-0.04, pitch=-0.16, cam=(NX / 2, 0, 8), cam_h=16, sat=1.1)

BIOMES['cherry'] = dict(
    title='Cherry grove', seed=37, base=57, roll=22, detail=5, ridge_pow=2.2, mountain=46, mount_start=250, mount_ramp=210, side_hills=24,
    lake=(NX / 2 - 56, 175, 46, 8), water=53, sub=DIRT, surface=GRASS, shore=SAND, liquid=WATER, snowline=104, snowcap=True, rock_slope=2.1,
    trees=[('cherry', 5.5, 90, 420), ('cherry', 2.0, 420, 620), ('oak', 0.4, 90, 300)],
    frame=[('cherry', [(-36, 46), (-24, 66), (40, 50), (30, 74), (-56, 88), (56, 96)])],
    decor=[(PETAL, 30)],
    pal={GRASS: (0x9fd86a, 0x866043, 0x866043, 0.07), DIRT: (0x7d5a40,) * 3 + (0.07,), STONE: (0x808080, 0x808080, 0x707070, 0.09),
         SAND: (0xdcd2a4,) * 3 + (0.05,), SNOW: (0xf9f6ff,) * 3 + (0.03,), WATER: (0x3d7bd8,) * 3 + (0.0,), CHERRYLOG: (0x3a2a3a, 0x2f2230, 0x2f2230, 0.08),
         CHERRY: (0xf2a6c8,) * 3 + (0.1,), LOG: (0x9c7f4d, 0x6b5233, 0x6b5233, 0.1), LEAVES: (0x5fae3f,) * 3 + (0.1,), PETAL: (0xf6b9d6,) * 3 + (0.08,),
         FLOWER_R: (0xd8478f,) * 3 + (0.1,)},
    sun=(0.62, 0.2, 0.75), sunc=(1.1, 0.78, 0.66), amb=(0.6, 0.58, 0.72), zen=(0.36, 0.38, 0.7), hor=(1.0, 0.76, 0.7), fog=(0.96, 0.74, 0.74), fogd=380,
    water_color=(0.22, 0.3, 0.58), glow=1.5, cloud_cover=0.55, cloud_color=(1, 0.9, 0.9), cloud_shade=(0.82, 0.62, 0.76), cloud_alpha=0.9,
    yaw=0.03, pitch=-0.14, cam=(NX / 2, 0, 8), cam_h=16, sat=1.14)

BIOMES['desert'] = dict(
    title='Desert', seed=51, base=55, roll=24, detail=5, ridge_pow=2.8, mountain=40, mount_start=260, mount_ramp=210, side_hills=34,
    lake=None, water=0, sub=SANDSTONE, surface=SAND, shore=SAND, liquid=WATER, snowline=999, snowcap=False, rock_slope=2.6,
    trees=[('cactus', 1.4, 90, 420), ('cactus', 0.8, 420, 620)],
    frame=[('cactus', [(-36, 50), (-27, 70), (38, 56), (30, 76), (-50, 96)])],
    decor=[(DEADBUSH, 10)],
    pal={SAND: (0xe3d29a, 0xe3d29a, 0xd9c88a, 0.05), SANDSTONE: (0xd8c88b, 0xcdb97a, 0xcdb97a, 0.07), STONE: (0xb98a5f, 0xb98a5f, 0xa87b52, 0.08),
         CACTUS: (0x3e8a3a, 0x2f7a31, 0x2f7a31, 0.08), DEADBUSH: (0x8a6a3a,) * 3 + (0.1,), WATER: (0x2f7ad0,) * 3 + (0.0,)},
    sun=(-0.3, 0.7, 0.65), sunc=(1.12, 0.96, 0.78), amb=(0.62, 0.6, 0.62), zen=(0.3, 0.55, 0.88), hor=(0.95, 0.86, 0.72), fog=(0.92, 0.82, 0.66), fogd=430,
    water_color=(0.12, 0.35, 0.62), glow=1.2, cloud_cover=0.64, cloud_color=(1, 0.98, 0.94), cloud_shade=(0.86, 0.8, 0.72), cloud_alpha=0.85,
    yaw=-0.06, pitch=-0.14, cam=(NX / 2, 0, 8), cam_h=16, sat=1.12)

COMMON_PAL = {  # fallbacks used by every biome
    GRASS: (0x79c05a, 0x866043, 0x866043, 0.07), DIRT: (0x866043,) * 3 + (0.07,), STONE: (0x7d7d7d, 0x7d7d7d, 0x6e6e6e, 0.09),
    SAND: (0xdbd3a0,) * 3 + (0.05,), SNOW: (0xf6fbff,) * 3 + (0.03,), WATER: (0x2f6bd6,) * 3 + (0.0,), LOG: (0x9c7f4d, 0x6b5233, 0x6b5233, 0.1),
    LEAVES: (0x4fa23a,) * 3 + (0.1,), SPRUCE: (0x2d5a3f,) * 3 + (0.1,), CHERRY: (0xf2a6c8,) * 3 + (0.1,), SANDSTONE: (0xd8c88b,) * 3 + (0.07,),
    ICE: (0x9cc4f0,) * 3 + (0.03,), MESA: (0xb4643c,) * 3 + (0.08,), CACTUS: (0x3e8a3a,) * 3 + (0.08,), GRAVEL: (0x857f7b,) * 3 + (0.1,),
    PODZOL: (0x6b4a2c,) * 3 + (0.1,), PETAL: (0xf6b9d6,) * 3 + (0.08,), SPRUCELOG: (0x5a4128,) * 3 + (0.08,), CHERRYLOG: (0x3a2a3a,) * 3 + (0.08,),
    DEADBUSH: (0x8a6a3a,) * 3 + (0.1,), FLOWER_R: (0xd8352f,) * 3 + (0.1,), FLOWER_Y: (0xf1d238,) * 3 + (0.1,)}

LAYERS = [  # name, tmin, tmax, blur(px), render mode
    ('far', 175.0, 760.0, 1.9),
    ('mid', 86.0, 175.0, 1.0),
    ('near', 36.0, 86.0, 0.8),
    ('front', 0.0, 36.0, 2.6),
]

def render_biome(name, out_dir, W, H, SS, preview):
    cfg = BIOMES[name]
    t0 = time.time()
    print(f'[{name}] building world', flush=True)
    world = World(cfg, cfg['seed'])
    surface = world.height
    cx = int(cfg['cam'][0]); cz = int(cfg['cam'][2])
    cam = np.array([cfg['cam'][0] + 0.5, surface[cx, cz] + cfg['cam_h'] + 0.5, cfg['cam'][2] + 0.5])
    pal = dict(COMMON_PAL); pal.update(cfg['pal'])
    pt, ps, pb, var = palette(pal)
    f, r, u = basis(cfg['yaw'], cfg['pitch'])
    fovx = math.radians(74.0)
    tanx = math.tan(fovx / 2); tany = tanx * H / W
    sun = np.array(cfg['sun'], dtype=np.float64); sun /= np.linalg.norm(sun)
    args = dict(sun=sun, sunc=np.array(cfg['sunc']), amb=np.array(cfg['amb']), fogc=np.array(cfg['fog']), fogd=float(cfg['fogd']),
                zen=np.array(cfg['zen']), hor=np.array(cfg['hor']), water_color=np.array(cfg['water_color']), glow=float(cfg['glow']))
    out = os.path.join(out_dir, name); os.makedirs(out, exist_ok=True)
    layers = {}
    sky = render_layer(world.grid, W // 2, H // 2, 1, cam, f, r, u, tanx, tany, 0.0, 1.0, 0, pt, ps, pb, var, **args)
    layers['sky'] = finish(sky, 0.0, 1.0).convert('RGB').resize((W // 2, H // 2), Image.LANCZOS)
    for lname, tmin, tmax, blur in LAYERS:
        print(f'[{name}] layer {lname} ({time.time() - t0:.0f}s)', flush=True)
        data = render_layer(world.grid, W, H, SS, cam, f, r, u, tanx, tany, tmin, tmax, 1, pt, ps, pb, var, **args)
        layers[lname] = finish(data, blur, cfg['sat'])
    layers['clouds'] = make_clouds(cfg, seed=cfg['seed'])
    for lname, image in layers.items():
        path = os.path.join(out, f'{lname}.png')
        image.save(path, optimize=True)
        with open(path + '.mcmeta', 'w') as meta:
            meta.write('{"texture":{"blur":true,"clamp":%s}}\n' % ('false' if lname == 'clouds' else 'true'))
    if preview:
        comp = layers['sky'].resize((W, H), Image.BICUBIC).convert('RGBA')
        comp.alpha_composite(layers['clouds'].resize((W, int(H * 0.25))), (0, int(H * 0.08)))
        for lname in ('far', 'mid', 'near', 'front'):
            comp.alpha_composite(layers[lname])
        comp.convert('RGB').save(os.path.join(out_dir, f'preview_{name}.png'))
    print(f'[{name}] done in {time.time() - t0:.0f}s', flush=True)

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    out_dir = 'out'
    for a in sys.argv[1:]:
        if a.startswith('--out='):
            out_dir = a.split('=', 1)[1]
    W = int(os.environ.get('VW', 1600)); H = int(os.environ.get('VH', 900)); SS = int(os.environ.get('SS', 2))
    for biome in (args or list(BIOMES)):
        render_biome(biome, out_dir, W, H, SS, '--preview' in sys.argv)
