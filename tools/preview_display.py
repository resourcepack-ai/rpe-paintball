"""
Renders a block model through Minecraft's display transforms, to check how an
item sits before anybody loads the pack:

  python tools/preview_display.py <model.json> <texture.png> <out.png>

Three panels: first person (right hand, 70 degree FOV, 16:9), third person
from the player's right (the arm raised 18 degrees as for any held item),
and the inventory slot. Faces are flat-shaded with the mean colour of their
UV region and drawn back to front; it is a placement check, not a renderer.
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
    # ItemTransform.apply: translate, rotate (XYZ), scale; then the renderer centres the model.
    return M(t=t) @ M(rx(r[0]) @ ry(r[1]) @ rz(r[2])) @ S(*s) @ M(t=[-0.5, -0.5, -0.5])


FACES = {
    "north": [(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)],
    "south": [(1, 1, 1), (0, 1, 1), (0, 0, 1), (1, 0, 1)],
    "east": [(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)],
    "west": [(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)],
    "up": [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)],
    "down": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
}
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def quads(model, tex):
    w, h = tex.size
    px = np.asarray(tex.convert("RGBA")).astype(float)
    out = []
    for e in model["elements"]:
        f = np.array(e["from"], float) / 16
        t = np.array(e["to"], float) / 16
        rot = e.get("rotation")
        R = np.eye(3)
        o = np.zeros(3)
        if rot:
            a = math.radians(rot["angle"])
            R = {"x": rx, "y": ry, "z": rz}[rot["axis"]](a)
            o = np.array(rot["origin"], float) / 16
        for name, face in e.get("faces", {}).items():
            u0, v0, u1, v1 = face["uv"]
            x0, x1 = sorted([u0 * w / 16, u1 * w / 16])
            y0, y1 = sorted([v0 * h / 16, v1 * h / 16])
            region = px[int(y0):max(int(y0) + 1, int(math.ceil(y1))), int(x0):max(int(x0) + 1, int(math.ceil(x1)))]
            region = region[region[..., 3] > 0]
            if len(region) == 0:
                continue
            col = region[:, :3].mean(0)
            pts = []
            for c in FACES[name]:
                p = f + (t - f) * np.array(c, float)
                pts.append(R @ (p - o) + o)
            n = R @ np.array(NORMAL[name], float)
            out.append((np.array(pts), n, col))
    return out


def render(quads_, world, project, size, bg, light=np.array([0.3, 0.9, 0.4]), extra=()):
    img = Image.new("RGB", size, bg)
    d = ImageDraw.Draw(img)
    items = []
    for pts, n, col in list(quads_) + list(extra):
        P = (world @ np.c_[pts, np.ones(len(pts))].T).T[:, :3]
        N = world[:3, :3] @ n
        N = N / (np.linalg.norm(N) + 1e-9)
        scr = [project(p) for p in P]
        if any(s is None for s in scr):
            continue
        depth = np.mean([s[2] for s in scr])
        k = 0.55 + 0.45 * max(0, float(N @ (light / np.linalg.norm(light))))
        items.append((depth, [(s[0], s[1]) for s in scr], tuple(int(min(255, c * k)) for c in col)))
    items.sort(key=lambda x: -x[0])
    for _, poly, col in items:
        d.polygon(poly, fill=col)
    return img


def first_person(model, q):
    W, H = 640, 360
    f = (H / 2) / math.tan(math.radians(35))
    world = M(t=[0.56, -0.52, -0.72]) @ display(model, "firstperson_righthand")

    def proj(p):
        if p[2] > -0.05:
            return None
        return (W / 2 + p[0] / -p[2] * f, H / 2 - p[1] / -p[2] * f, -p[2])

    img = render(q, world, proj, (W, H), (120, 160, 200))
    d = ImageDraw.Draw(img)
    d.line([(W / 2 - 6, H / 2), (W / 2 + 6, H / 2)], fill=(255, 255, 255))
    d.line([(W / 2, H / 2 - 6), (W / 2, H / 2 + 6)], fill=(255, 255, 255))
    return img


def player_boxes():
    """The player's body, in entity model space (pixels, y down), as plain boxes."""
    skin = np.array([200, 150, 120], float)
    shirt = np.array([60, 120, 170], float)
    legs = np.array([50, 50, 110], float)
    parts = [((-4, -8, -4), (4, 0, 4), skin), ((-4, 0, -2), (4, 12, 2), shirt),
             ((-4, 12, -2), (0, 24, 2), legs), ((0, 12, -2), (4, 24, 2), legs),
             ((4, -2 + 2, -2), (8, 10 + 2, 2), shirt)]
    out = []
    for f, t, col in parts:
        f = np.array(f, float) / 16
        t = np.array(t, float) / 16
        for name in FACES:
            pts = [f + (t - f) * np.array(c, float) for c in FACES[name]]
            out.append((np.array(pts), np.array(NORMAL[name], float), col))
    return out


def third_person(model, q):
    W, H = 360, 360
    root = M(ry(math.pi)) @ S(-1, -1, 1) @ M(t=[0, -1.501, 0])
    arm_pivot = M(t=[-5 / 16, 2 / 16, 0]) @ M(rx(math.radians(-18)))
    hand = M(rx(math.radians(-90))) @ M(ry(math.pi)) @ M(t=[1 / 16, 2 / 16, -10 / 16])
    world = root @ arm_pivot @ hand @ display(model, "thirdperson_righthand")
    arm = []
    for f, t in [((-3, -2, -2), (1, 10, 2))]:
        f = np.array(f, float) / 16
        t = np.array(t, float) / 16
        for name in FACES:
            pts = [f + (t - f) * np.array(c, float) for c in FACES[name]]
            arm.append((np.array(pts), np.array(NORMAL[name], float), np.array([200, 150, 120], float)))
    scale = 120

    # Seen from the player's right (world -x), looking toward +x: screen x is world +z.
    def proj(p):
        return (W / 2 + p[2] * scale, H - 20 - p[1] * scale, p[0])

    body = render([], root, proj, (W, H), (150, 190, 150))
    img = render(q, world, proj, (W, H), (150, 190, 150), extra=[])
    # draw the body and arm under the item
    base = render(player_boxes(), root, proj, (W, H), (150, 190, 150))
    armimg = render(arm, root @ arm_pivot, proj, (W, H), (150, 190, 150))
    mask_item = Image.eval(img.convert("L"), lambda v: 0)
    out = base.copy()
    for layer in (armimg, img):
        diff = np.any(np.asarray(layer) != np.array([150, 190, 150]), axis=2)
        arr = np.asarray(out).copy()
        arr[diff] = np.asarray(layer)[diff]
        out = Image.fromarray(arr)
    return out


def gui(model, q):
    W = H = 192
    world = display(model, "gui")

    def proj(p):
        return (W / 2 + p[0] * W, H / 2 - p[1] * H, -p[2])

    img = render(q, world, proj, (W, H), (139, 139, 139), light=np.array([-0.3, 0.8, 0.6]))
    d = ImageDraw.Draw(img)
    d.rectangle([W / 2 - W / 2 + 1, 1, W - 2, H - 2], outline=(80, 80, 80))
    return img


if __name__ == "__main__":
    model = json.load(open(sys.argv[1], encoding="utf-8"))
    tex = Image.open(sys.argv[2])
    q = quads(model, tex)
    a, b, c = first_person(model, q), third_person(model, q), gui(model, q)
    sheet = Image.new("RGB", (a.width + b.width + c.width + 20, max(a.height, b.height)), (30, 30, 30))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (a.width + 10, 0))
    sheet.paste(c, (a.width + b.width + 20, 0))
    sheet.save(sys.argv[3])


def head(model, q):
    """The model worn on the head (CustomHeadLayer), seen from the front-right, over a head-sized box."""
    W = H = 256
    root = M(ry(math.pi)) @ S(-1, -1, 1) @ M(t=[0, -1.501, 0])
    head_layer = M(t=[0, -0.25, 0]) @ M(ry(math.pi)) @ S(0.625, -0.625, -0.625)
    world = root @ head_layer @ display(model, "head")
    turn = ry(math.radians(-35)) @ rx(math.radians(0))

    def proj(p):
        v = turn @ (p - np.array([0, 1.75, 0]))
        return (W / 2 + v[0] * -300, H / 2 - v[1] * 300, -v[2])

    skull = []
    f, t = np.array([-4, -8, -4]) / 16, np.array([4, 0, 4]) / 16
    for name in FACES:
        pts = [f + (t - f) * np.array(c, float) for c in FACES[name]]
        skull.append((np.array(pts), np.array(NORMAL[name], float), np.array([210, 160, 130], float)))
    base = render(skull, root, proj, (W, H), (140, 170, 200))
    img = render(q, world, proj, (W, H), (140, 170, 200))
    diff = np.any(np.asarray(img) != np.array([140, 170, 200]), axis=2)
    arr = np.asarray(base).copy()
    arr[diff] = np.asarray(img)[diff]
    return Image.fromarray(arr)
