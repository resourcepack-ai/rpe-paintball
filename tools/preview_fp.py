"""
Textured first-person preview of a held item, the way the game projects it:

  python tools/preview_fp.py <model.json> <texture.png> <display context> <out.png> [background.png]

The right hand's arm transform (0.56, -0.52, -0.72), then the model's display
transform for the context (translate, rotate XYZ, scale, then the -0.5 that
centres a block model), drawn with a 70 degree vertical field of view at
640x360 (the hand is always drawn at 70, whatever the zoom). Faces are textured
with perspective correction and the game's face shading; a crosshair marks the
middle of the screen. Use it to line a sight up with the crosshair.
"""
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def M(rot3=None, t=None):
    m = np.eye(4)
    if rot3 is not None:
        m[:3, :3] = rot3
    if t is not None:
        m[:3, 3] = t
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def display(model, ctx):
    d = model.get("display", {}).get(ctx, {})
    r = [math.radians(v) for v in d.get("rotation", [0, 0, 0])]
    t = [v / 16 for v in d.get("translation", [0, 0, 0])]
    s = d.get("scale", [1, 1, 1])
    return M(t=t) @ M(rx(r[0]) @ ry(r[1]) @ rz(r[2])) @ S(*s) @ M(t=[-0.5, -0.5, -0.5])


# Corners from the outside, top-left first: the order a face's uv runs in.
def faces(f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def triangles(model):
    out = []
    for e in model["elements"]:
        f = np.array(e["from"], float) / 16
        t = np.array(e["to"], float) / 16
        rot = e.get("rotation")
        R, o = np.eye(3), np.zeros(3)
        if rot and rot.get("angle"):
            R = {"x": rx, "y": ry, "z": rz}[rot["axis"]](math.radians(rot["angle"]))
            o = np.array(rot["origin"], float) / 16
        for name, corners in faces(f, t).items():
            face = e.get("faces", {}).get(name)
            if not face:
                continue
            u1, v1, u2, v2 = [c / 16 for c in face.get("uv", [0, 0, 16, 16])]
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            k = (face.get("rotation", 0) // 90) % 4
            uvs = [uvs[(i - k) % 4] for i in range(4)]
            P = [R @ (np.array(c) - o) + o for c in corners]
            shade = SHADE[name] if e.get("shade", True) else 1.0
            out.append((P, uvs, shade))
    return out


NEAR = 0.05


def clip_near(V, uvs):
    """Clips a polygon to the near plane (z = -NEAR), as the GPU does, carrying its uvs."""
    out_v, out_uv = [], []
    n = len(V)
    for i in range(n):
        a, b = V[i], V[(i + 1) % n]
        ua, ub = np.array(uvs[i]), np.array(uvs[(i + 1) % n])
        ina, inb = a[2] <= -NEAR, b[2] <= -NEAR
        if ina:
            out_v.append(a)
            out_uv.append(tuple(ua))
        if ina != inb:
            t = (-NEAR - a[2]) / (b[2] - a[2])
            out_v.append(a + (b - a) * t)
            out_uv.append(tuple(ua + (ub - ua) * t))
    return out_v, out_uv


def render(model, tex, ctx, size=(640, 360), background=None, fov=70):
    W, H = size
    T = np.asarray(tex.convert("RGBA")).astype(float)
    th, tw = T.shape[:2]
    f = (H / 2) / math.tan(math.radians(fov / 2))
    world = M(t=[0.56, -0.52, -0.72]) @ display(model, ctx)
    if background is not None:
        img = np.asarray(background.convert("RGBA").resize((W, H))).astype(float)
    else:
        img = np.zeros((H, W, 4)) + np.array([120, 160, 200, 255], float)
    zbuf = np.full((H, W), np.inf)
    for P, uvs, shade in triangles(model):
        V = [(world @ np.r_[p, 1])[:3] for p in P]
        V, uvs = clip_near(V, list(uvs))
        if len(V) < 3:
            continue
        scr = [np.array([W / 2 + v[0] / -v[2] * f, H / 2 - v[1] / -v[2] * f, -v[2]]) for v in V]
        for a, b, c in [(0, i, i + 1) for i in range(1, len(V) - 1)]:
            p0, p1, p2 = scr[a], scr[b], scr[c]
            minx = max(0, int(math.floor(min(p0[0], p1[0], p2[0]))))
            maxx = min(W - 1, int(math.ceil(max(p0[0], p1[0], p2[0]))))
            miny = max(0, int(math.floor(min(p0[1], p1[1], p2[1]))))
            maxy = min(H - 1, int(math.ceil(max(p0[1], p1[1], p2[1]))))
            if minx > maxx or miny > maxy:
                continue
            den = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            w0 = ((p1[1] - p2[1]) * (xs - p2[0]) + (p2[0] - p1[0]) * (ys - p2[1])) / den
            w1 = ((p2[1] - p0[1]) * (xs - p2[0]) + (p0[0] - p2[0]) * (ys - p2[1])) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not inside.any():
                continue
            iz = w0 / p0[2] + w1 / p1[2] + w2 / p2[2]
            u = (w0 * uvs[a][0] / p0[2] + w1 * uvs[b][0] / p1[2] + w2 * uvs[c][0] / p2[2]) / iz
            v = (w0 * uvs[a][1] / p0[2] + w1 * uvs[b][1] / p1[2] + w2 * uvs[c][1] / p2[2]) / iz
            depth = 1 / iz
            col = T[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
            sub_z = zbuf[miny:maxy + 1, minx:maxx + 1]
            ok = inside & (col[..., 3] > 0) & (depth < sub_z)
            alpha = (col[..., 3] / 255.0)[..., None]
            sub = img[miny:maxy + 1, minx:maxx + 1]
            rgb = col[..., :3] * shade
            blended = sub[..., :3] * (1 - alpha) + rgb * alpha
            sub[..., :3][ok] = blended[ok]
            sub_z[ok & (col[..., 3] > 200)] = depth[ok & (col[..., 3] > 200)]
    out = Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(out)
    d.line([(W / 2 - 6, H / 2), (W / 2 + 6, H / 2)], fill=(255, 255, 255, 255))
    d.line([(W / 2, H / 2 - 6), (W / 2, H / 2 + 6)], fill=(255, 255, 255, 255))
    return out


if __name__ == "__main__":
    model = json.load(open(sys.argv[1], encoding="utf-8"))
    bg = Image.open(sys.argv[5]) if len(sys.argv) > 5 else None
    render(model, Image.open(sys.argv[2]), sys.argv[3], background=bg).save(sys.argv[4])
