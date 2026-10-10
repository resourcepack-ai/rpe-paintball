"""
Builds every asset in the paintball content folder from scratch:

  python tools/make_assets.py

- the marker: the model the workbench builder generated (tools/src), given a
  namespaced texture and display transforms, and five skins painted from its atlas;
- the mask, the paintball, the splats and the grenade: models and textures made here;
- the HUD glyphs (screen splats, ammo pip, grenade icon) and the mask's camera overlay;
- the sounds, synthesised and written as Ogg Vorbis.

Deterministic: every random draw is seeded, so a re-run writes the same files.
"""
import colorsys
import json
import math
import os
import random

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "src")
OUT = os.path.join(ROOT, "..", "src", "main", "resources", "content", "paintball")
A = os.path.join(OUT, "assets")

TEAMS = {"red": (0xFF, 0x46, 0x55), "blue": (0x2E, 0x9B, 0xFF)}


def path(*p):
    full = os.path.join(A, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def save_json(obj, *p):
    with open(path(*p), "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1)


def write_text(text, *p):
    full = os.path.join(OUT, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def hexrgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def shade(rgb, k):
    return tuple(max(0, min(255, int(round(c * k)))) for c in rgb)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ---------------------------------------------------------------------------
# The marker
# ---------------------------------------------------------------------------

SKINS = {
    # body: (hue, saturation, lightness factor) for the tan furniture, or None to keep it;
    # accent: the colour the blue trim becomes, or None to keep it.
    "ember": dict(display="Ember", body=None, accent=0xFF4655),
    "tide": dict(display="Tide", body=None, accent=None),
    "toxic": dict(display="Toxic", body=(0.21, 0.42, 0.62), accent=0x7CFF4F),
    "sunset": dict(display="Sunset", body=(0.76, 0.40, 0.62), accent=0xFF8A3D),
    "gold": dict(display="Gold", body=(0.60, 0.10, 0.30), accent=0xFFC94A),
}
TAN_V = 198 / 255   # the furniture's main tan, which the lightness map is measured from
TRIM_V = 200 / 255  # the main blue of the trim

# Parts of the marker that sit between the eye and the sights when it is aimed.
# The aimed first-person model leaves them out; everybody else still sees them.
BEHIND_THE_SIGHTS = ("hopper", "air_bottle", "tank_", "stock_end", "pressure_gauge", "feed_neck")


def classify(r, g, b, a):
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    deg = h * 360
    if a < 255:
        return "lens", h, s, v
    if 20 <= deg <= 55 and s > 0.25 and v > 0.4:
        return "body", h, s, v
    if 185 <= deg <= 220 and s > 0.45:
        return "trim", h, s, v
    return "metal", h, s, v


def recolour(atlas, skin):
    cfg = SKINS[skin]
    out = atlas.copy()
    px = out.load()
    w, hgt = out.size
    if cfg["accent"] is not None:
        ah, as_, av = colorsys.rgb_to_hsv(*(c / 255 for c in hexrgb(cfg["accent"])))
    for y in range(hgt):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            kind, h, s, v = classify(r, g, b, a)
            if kind == "trim" and cfg["accent"] is not None:
                # the trim's own shading, in the new colour
                nr, ng, nb = colorsys.hsv_to_rgb(ah, min(1, as_ * s / 0.82), min(1, av * v / TRIM_V))
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
            elif kind == "body" and cfg["body"] is not None:
                bh, bs, bv = cfg["body"]
                nr, ng, nb = colorsys.hsv_to_rgb(bh, bs, min(1, bv * v / TAN_V))
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return out


def marker():
    model = json.load(open(os.path.join(SRC, "marker_generated.json"), encoding="utf-8"))
    model.pop("parent", None)
    model.pop("groups", None)
    model["textures"] = {"atlas": "paintball:item/marker_ember", "particle": "paintball:item/marker_ember"}
    # Studio's Tactical marker: barrel along +z (muzzle at 32), stock at -16,
    # the red dot's lens at (6, 19, 14). Hip fire holds it low and to the right
    # like any FPS, turned 14 degrees in so the barrel points across at the
    # crosshair (tools/preview_fp.py draws it the way the game projects it).
    # In third person the item frame is +x out, +y forward, +z up, so
    # rotationXYZ(90, 180, 0) lays it barrel-forward, hopper-up; 72 instead of
    # 90 takes back the 18 degrees the arm is raised by when holding an item.
    display = {
        "thirdperson_righthand": {"rotation": [72, 180, 0], "translation": [0, 1.5, 1.5], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_lefthand": {"rotation": [72, 180, 0], "translation": [0, 1.5, 1.5], "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [-4, 194, -4], "translation": [2.5, -2.8, -9], "scale": [0.42, 0.42, 0.42]},
        "firstperson_lefthand": {"rotation": [-4, 194, -4], "translation": [2.5, -2.8, -9], "scale": [0.42, 0.42, 0.42]},
        "gui": {"rotation": [20, 115, 0], "translation": [0.8, -2.2, 0], "scale": [0.3, 0.3, 0.3]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, -90, 0], "translation": [0, -2.4, 0], "scale": [0.34, 0.34, 0.34]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }
    model["display"] = display
    model["gui_light"] = "side"
    save_json(model, "models", "marker.json")

    # Aimed, as the holder sees it: the sights on the line of sight, the eye
    # twenty pixels behind the lens, and nothing that would be between them.
    # The lens itself is left open: the HUD's crosshair is the reticle.
    sights = json.loads(json.dumps(model))
    sights["elements"] = [e for e in sights["elements"] if not e.get("name", "").startswith(BEHIND_THE_SIGHTS)]
    for e in sights["elements"]:
        if e.get("name") == "sight_lens":
            e["faces"] = {}
    sights["elements"] = [e for e in sights["elements"] if e.get("faces")]
    aim = aim_transform((6, 19, 14), distance=0.45, scale=0.40)
    sights["display"] = {"firstperson_righthand": aim, "firstperson_lefthand": aim}
    save_json(sights, "models", "marker_sights.json")

    atlas = Image.open(os.path.join(SRC, "marker_atlas.png")).convert("RGBA")
    for skin in SKINS:
        recolour(atlas, skin).save(path("textures", "item", f"marker_{skin}.png"))


def aim_transform(sight, distance, scale):
    """The first-person display that puts model pixel `sight` dead ahead of the eye, `distance` blocks out.

    The right hand's arm transform is (0.56, -0.52, -0.72); the model is turned
    half a turn about y so its +z barrel points away from the eye.
    """
    x, y, z = [c / 16 - 0.5 for c in sight]
    turned = (-x, y, -z)
    t = [0 - 0.56 - scale * turned[0], 0 + 0.52 - scale * turned[1], -distance + 0.72 - scale * turned[2]]
    return {"rotation": [0, 180, 0], "translation": [round(v * 16, 3) for v in t], "scale": [scale] * 3}


# ---------------------------------------------------------------------------
# The mask: built here, around the head (the head fills 1.6..14.4 of the model)
# ---------------------------------------------------------------------------

def mask_texture(team):
    col = TEAMS[team]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = img.load()
    # shell 0..16 x 0..16: the team colour, lit from the top left
    for y in range(16):
        for x in range(16):
            k = 1.08 - (x + y) / 60
            if (x + y) % 7 == 0:
                k -= 0.04
            d[x, y] = shade(col, k) + (255,)
    for x in range(16):
        d[x, 0] = shade(col, 1.25) + (255,)
        d[x, 15] = shade(col, 0.7) + (255,)
    # trim 16..24 x 0..8: near-black rubber
    for y in range(8):
        for x in range(16, 24):
            d[x, y] = (30, 32, 38, 255) if (x + y) % 3 else (40, 43, 50, 255)
    # lens 16..32 x 8..16: smoked, with a highlight streak
    for y in range(8, 16):
        for x in range(16, 32):
            t = (y - 8) / 8
            base = mix((70, 92, 120), (14, 18, 28), t)
            if 2 <= (x - 16) - (y - 8) <= 3:
                base = mix(base, (220, 235, 255), 0.55)
            d[x, y] = base + (255,)
    # vents 0..16 x 16..24: the shell with dark slots
    for y in range(16, 24):
        for x in range(16):
            slot = (y - 16) % 2 == 1 and 2 <= x <= 13
            d[x, y] = ((22, 24, 28, 255) if slot else shade(col, 0.95 - (y - 16) / 40) + (255,))
    # strap 16..32 x 16..20
    for y in range(16, 20):
        for x in range(16, 32):
            d[x, y] = (24, 26, 30, 255) if (x // 2) % 2 else (36, 38, 44, 255)
    img.save(path("textures", "item", f"mask_{team}.png"))


def box(name, frm, to, tex):
    """An element whose six faces all map onto one atlas region (u0, v0, u1, v1), in 0..16."""
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        region = tex.get(f, tex["all"]) if isinstance(tex, dict) else tex
        faces[f] = {"uv": list(region), "texture": "#mask"}
    return {"name": name, "from": frm, "to": to, "faces": faces}


SHELL = (0, 0, 8, 8)
TRIM = (8, 0, 12, 4)
LENS = (8, 4, 16, 8)
VENT = (0, 8, 8, 12)
STRAP = (8, 8, 16, 10)


def mask_model():
    # On the head the model's 16 units are 10 of the player's pixels, so the
    # head is 1.6..14.4 and the skin's outer (hat) layer, half a pixel proud of
    # it, is 0.8..15.2. Everything that faces out is kept at least 0.3 outside
    # that box, or a skin's hat pixels poke through the mask (or flicker against
    # a face that lies exactly on them).
    e = [
        box("lens", [3, 7.5, 0.4], [13, 10.5, 1.6], {"all": TRIM, "north": LENS}),
        box("frame_top", [2, 10.5, 0.2], [14, 11.5, 1.8], TRIM),
        box("frame_bottom", [2, 6.5, 0.2], [14, 7.5, 1.8], TRIM),
        box("frame_left", [2, 7.5, 0.2], [3, 10.5, 1.8], TRIM),
        box("frame_right", [13, 7.5, 0.2], [14, 10.5, 1.8], TRIM),
        box("forehead", [2, 11.5, 0.5], [14, 14.6, 2.2], SHELL),
        box("crown", [2.5, 14.6, 0.5], [13.5, 15.5, 7], SHELL),
        box("mouth", [3.5, 1.6, 0.0], [12.5, 6.5, 1.6], {"all": SHELL, "north": VENT}),
        box("chin", [4.5, 0.5, 0.5], [11.5, 1.6, 4.5], SHELL),
        box("cheek_left", [0.5, 2, 0.5], [2.4, 11.5, 7.5], SHELL),
        box("cheek_right", [13.6, 2, 0.5], [15.5, 11.5, 7.5], SHELL),
        box("vent_left", [0.1, 5, 3], [0.5, 8, 6], TRIM),
        box("vent_right", [15.5, 5, 3], [15.9, 8, 6], TRIM),
        box("strap_left", [0.35, 8, 7.5], [0.5, 10, 15.65], STRAP),
        box("strap_right", [15.5, 8, 7.5], [15.65, 10, 15.65], STRAP),
        box("strap_back", [0.35, 8, 15.5], [15.65, 10, 15.65], STRAP),
    ]
    model = {
        "textures": {"mask": "paintball:item/mask_red", "particle": "paintball:item/mask_red"},
        "elements": e,
        "display": {
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [15, 160, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 200, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        },
    }
    save_json(model, "models", "mask.json")
    for t in TEAMS:
        mask_texture(t)


def mask_overlay():
    """Drawn over the wearer's whole view, like a carved pumpkin: the goggle frame round the edges."""
    w, h = 512, 256
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Frame: dark rubber everywhere outside a wide rounded lens, fading to clear.
    frame = Image.new("L", (w, h), 255)
    fd = ImageDraw.Draw(frame)
    fd.rounded_rectangle([20, 12, w - 20, h - 18], radius=70, fill=0)
    frame = frame.filter(ImageFilter.GaussianBlur(9))
    dark = Image.new("RGBA", (w, h), (10, 11, 14, 255))
    img = Image.composite(dark, img, frame.point(lambda v: int(v * 0.92)))
    # A faint tint and a reflection streak on the lens.
    tint = Image.new("RGBA", (w, h), (90, 120, 160, 14))
    img = Image.alpha_composite(img, tint)
    streak = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(streak)
    sd.polygon([(60, 30), (110, 30), (40, 120), (20, 120)], fill=(255, 255, 255, 16))
    streak = streak.filter(ImageFilter.GaussianBlur(4))
    img = Image.alpha_composite(img, streak)
    # The nose bridge dips in at the bottom middle.
    bridge = Image.new("L", (w, h), 0)
    bd = ImageDraw.Draw(bridge)
    bd.ellipse([w // 2 - 40, h - 40, w // 2 + 40, h + 40], fill=255)
    bridge = bridge.filter(ImageFilter.GaussianBlur(8))
    img = Image.composite(Image.new("RGBA", (w, h), (10, 11, 14, 235)), img, bridge)
    img.save(path("textures", "misc", "mask_overlay.png"))


# ---------------------------------------------------------------------------
# Balls, splats, grenade
# ---------------------------------------------------------------------------

def ball():
    for team, col in TEAMS.items():
        img = Image.new("RGBA", (16, 16))
        d = img.load()
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 5.5, y - 5.5) / 14
                k = 1.35 - r * 0.9
                d[x, y] = shade(mix(col, (255, 255, 255), max(0, 0.35 - r)), k) + (255,)
        img.save(path("textures", "item", f"ball_{team}.png"))
    full = [0, 0, 16, 16]
    faces = {f: {"uv": full, "texture": "#ball"} for f in ("north", "south", "east", "west", "up", "down")}
    model = {
        "textures": {"ball": "paintball:item/ball_red", "particle": "paintball:item/ball_red"},
        "elements": [
            {"from": [6.5, 6.5, 6.5], "to": [9.5, 9.5, 9.5], "faces": faces, "shade": False},
            {"from": [7, 6, 7], "to": [9, 10, 9], "faces": faces, "shade": False},
            {"from": [6, 7, 7], "to": [10, 9, 9], "faces": faces, "shade": False},
            {"from": [7, 7, 6], "to": [9, 9, 10], "faces": faces, "shade": False},
        ],
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [2.5, 2.5, 2.5]}},
    }
    save_json(model, "models", "ball.json")


def blob_mask(seed, size=64):
    rnd = random.Random(seed)
    s = 4  # supersample
    W = size * s
    img = Image.new("L", (W, W), 0)
    d = ImageDraw.Draw(img)
    c = W / 2
    core = W * rnd.uniform(0.21, 0.25)
    d.ellipse([c - core, c - core * rnd.uniform(0.85, 1.0), c + core, c + core], fill=255)
    for _ in range(rnd.randint(5, 8)):
        a = rnd.uniform(0, math.tau)
        r = core * rnd.uniform(0.55, 0.85)
        dist = core * rnd.uniform(0.5, 0.9)
        x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    for _ in range(rnd.randint(4, 7)):  # fingers flung outward
        a = rnd.uniform(0, math.tau)
        length = core * rnd.uniform(1.1, 1.75)
        width = core * rnd.uniform(0.14, 0.26)
        for i in range(24):
            t = i / 23
            x = c + math.cos(a) * length * t
            y = c + math.sin(a) * length * t
            r = width * (1 - t * 0.6)
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        x, y = c + math.cos(a) * length, c + math.sin(a) * length
        r = width * 1.1
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    for _ in range(rnd.randint(8, 14)):  # loose droplets
        a = rnd.uniform(0, math.tau)
        dist = core * rnd.uniform(1.3, 1.9)
        r = core * rnd.uniform(0.05, 0.13)
        x, y = c + math.cos(a) * dist, c + math.sin(a) * dist
        if 0 < x - r and x + r < W and 0 < y - r and y + r < W:
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
    img = img.filter(ImageFilter.GaussianBlur(s * 0.6))
    return img.resize((size, size), Image.LANCZOS)


def colour_blob(mask, col, gloss=True):
    size = mask.size[0]
    m = np.asarray(mask).astype(np.float32) / 255
    alpha = (m > 0.5).astype(np.float32)
    # Distance-ish from the edge: blur the mask and use it as depth.
    depth = np.asarray(mask.filter(ImageFilter.GaussianBlur(2.5))).astype(np.float32) / 255
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    light = 1 - ((xx + yy) / (2 * size)) * 0.25
    k = (0.72 + 0.38 * depth) * light
    rgb = np.zeros((size, size, 3), np.float32)
    for i in range(3):
        rgb[..., i] = np.clip(col[i] * k, 0, 255)
    if gloss:
        spec = np.clip((depth - 0.82) * 6, 0, 1) * np.clip(1 - (xx + yy) / size, 0, 1)
        rgb = rgb * (1 - spec[..., None] * 0.5) + 255 * spec[..., None] * 0.5
    out = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def splats():
    for n in (1, 2, 3):
        mask = blob_mask(100 + n)
        for team, col in TEAMS.items():
            colour_blob(mask, col).save(path("textures", "item", f"splat_{team}_{n}.png"))
    # Flat, both sides, through the middle of the model so it sits on the point it is placed at.
    model = {
        "textures": {"splat": "paintball:item/splat_red_1", "particle": "paintball:item/splat_red_1"},
        "elements": [{
            "from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
            "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#splat"},
                      "south": {"uv": [0, 0, 16, 16], "texture": "#splat"}},
        }],
        "display": {"gui": {"rotation": [0, 0, 0], "scale": [1, 1, 1]}},
    }
    save_json(model, "models", "splat.json")


def grenade():
    for team, col in TEAMS.items():
        img = Image.new("RGBA", (16, 16))
        d = img.load()
        for y in range(16):
            for x in range(16):
                if y < 3:
                    c = (200, 205, 212) if (x + y) % 2 else (180, 186, 194)  # lid
                elif 7 <= y <= 9:
                    c = (240, 240, 240)  # label band
                else:
                    c = shade(col, 1.1 - y / 40)
                if x in (0, 15):
                    c = shade(c, 0.8)
                d[x, y] = c + (255,)
        img.save(path("textures", "item", f"grenade_{team}.png"))
    side = lambda v0, v1: [0, v0, 16, v1]
    def el(frm, to, v0, v1, top=(0, 0, 16, 3)):
        faces = {f: {"uv": side(v0, v1), "texture": "#g"} for f in ("north", "south", "east", "west")}
        faces["up"] = {"uv": list(top), "texture": "#g"}
        faces["down"] = {"uv": [0, 13, 16, 16], "texture": "#g"}
        return {"from": frm, "to": to, "faces": faces}
    model = {
        "textures": {"g": "paintball:item/grenade_red", "particle": "paintball:item/grenade_red"},
        "elements": [
            el([5.5, 3, 5.5], [10.5, 10, 10.5], 3, 16),
            el([6, 10, 6], [10, 11.5, 10], 0, 3),
            el([7.25, 11.5, 7.25], [8.75, 12.5, 8.75], 0, 3),
        ],
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, 20, 0], "translation": [-1, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [25, 45, 0], "translation": [0, 0.5, 0], "scale": [1.1, 1.1, 1.1]},
            "ground": {"scale": [0.4, 0.4, 0.4]},
            "fixed": {"scale": [0.8, 0.8, 0.8]},
        },
    }
    save_json(model, "models", "grenade.json")


# ---------------------------------------------------------------------------
# Font glyphs
# ---------------------------------------------------------------------------

def screen_splats():
    """White (shaded grey) splats; the title's text colour paints them the shooter's colour."""
    for n in (1, 2, 3):
        mask = blob_mask(300 + n, size=256)
        img = colour_blob(mask, (255, 255, 255), gloss=True)
        # Drips running down from the bottom edge of the splat.
        d = ImageDraw.Draw(img)
        rnd = random.Random(400 + n)
        m = np.asarray(mask)
        for _ in range(rnd.randint(4, 7)):
            x = rnd.randint(70, 186)
            col = m[:, x]
            ys = np.where(col > 128)[0]
            if len(ys) == 0:
                continue
            y0 = int(ys.max()) - 4
            length = rnd.randint(18, 60)
            w = rnd.uniform(3, 6)
            for i in range(length):
                ww = w * (1 - i / length * 0.4)
                d.ellipse([x - ww, y0 + i - ww, x + ww, y0 + i + ww], fill=(225, 225, 225, 255))
            d.ellipse([x - w * 1.3, y0 + length - w, x + w * 1.3, y0 + length + w * 1.4], fill=(215, 215, 215, 255))
        img.save(path("textures", "font", f"screen_splat_{n}.png"))


def small_glyphs():
    # Ammo pip: a 3x7 rounded bar (white, coloured by the text it is in).
    img = Image.new("RGBA", (3, 7), (0, 0, 0, 0))
    d = img.load()
    for y in range(7):
        for x in range(3):
            if (y in (0, 6)) and x != 1:
                continue
            d[x, y] = (255, 255, 255, 255) if x < 2 else (200, 200, 200, 255)
    img.save(path("textures", "font", "pip.png"))
    # Grenade icon: a little can, 7x8.
    img = Image.new("RGBA", (7, 8), (0, 0, 0, 0))
    d = img.load()
    for y in range(8):
        for x in range(7):
            if y == 0 and 2 <= x <= 4:
                d[x, y] = (255, 255, 255, 255)
            elif y >= 1 and 1 <= x <= 5:
                d[x, y] = (255, 255, 255, 255) if y != 4 else (190, 190, 190, 255)
    img.save(path("textures", "font", "grenade_icon.png"))



# ---------------------------------------------------------------------------
# HUD glyphs: the score digits, the kill feed's panel and weapons, the hit marker
# ---------------------------------------------------------------------------
#
# Every one of these is inked in its rightmost column, because the game measures
# a bitmap glyph's advance from its rightmost non-transparent column and RP
# Engine measures it from the cell: the two only agree when the cell is full.

DIGITS = {
    "0": [".#####.", "##...##", "##...##", "##..###", "##.####", "####.##", "###..##", "##...##", "##...##", "##...##", ".#####."],
    "1": ["...##..", "..###..", ".####..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", "..##..."],
    "2": [".#####.", "##...##", ".....##", ".....##", "....##.", "...##..", "..##...", ".##....", "##.....", "##.....", "..##..."],
    "3": [".#####.", "##...##", ".....##", ".....##", "..####.", ".....##", ".....##", ".....##", ".....##", "##...##", ".#####."],
    "4": ["....##.", "...###.", "..####.", ".##.##.", "##..##.", "##..##.", "#######", "....##.", "....##.", "....##.", "....##."],
    "5": ["#######", "##.....", "##.....", "######.", ".....##", ".....##", ".....##", ".....##", ".....##", "##...##", ".#####."],
    "6": [".#####.", "##...##", "##.....", "##.....", "######.", "##...##", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "7": ["#######", ".....##", ".....##", "....##.", "....##.", "...##..", "...##..", "..##...", "..##...", "..##...", "..##..."],
    "8": [".#####.", "##...##", "##...##", "##...##", ".#####.", "##...##", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "9": [".#####.", "##...##", "##...##", "##...##", "##...##", ".######", ".....##", ".....##", ".....##", "##...##", ".#####."],
    "colon": ["..", "..", "..", "##", "##", "..", "..", "##", "##", "..", ".."],
}
# A pixel the font sees and the eye does not: keeps a glyph's last column inked.
FAINT = (255, 255, 255, 1)

FEED_BG = (11, 15, 20, 176)
FEED_HI_BG = (26, 30, 36, 200)
FEED_HI_EDGE = (255, 210, 63, 255)
FEED_H = 13
FEED_WIDTHS = (1, 2, 4, 8, 16, 32, 64)

MARKER_ICON = [
    ".......####.........",
    "......######........",
    ".......####.........",
    "###..#############..",
    "####################",
    "###..#############..",
    "........##.#........",
    "........##..#.......",
    "........##..........",
]



def bitmap(rows, colour=(255, 255, 255, 255)):
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                px[x, y] = colour
    if not any(img.getpixel((img.width - 1, y))[3] for y in range(img.height)):
        px[img.width - 1, 0] = FAINT
    return img


def hud_glyphs():
    # Score and clock digits: one sheet of ten 7x11 cells, all the same width so
    # a clock does not jitter; the colon is its own, narrow glyph.
    sheet = Image.new("RGBA", (7 * 10, 11), (0, 0, 0, 0))
    for n in range(10):
        sheet.paste(bitmap(DIGITS[str(n)]), (n * 7, 0))
    sheet.save(path("textures", "font", "digits.png"))
    bitmap(DIGITS["colon"]).save(path("textures", "font", "digit_colon.png"))

    # The kill feed's panel, assembled per line by the plugin: a cap in the
    # killer's colour, middle pieces in powers of two, a cap in the victim's.
    for team, rgb in TEAMS.items():
        cap = Image.new("RGBA", (3, FEED_H), FEED_BG)
        for y in range(FEED_H):
            cap.putpixel((0, y), rgb + (255,))
            cap.putpixel((1, y), rgb + (255,))
        cap.save(path("textures", "font", f"feed_l_{team}.png"))
        cap.transpose(Image.FLIP_LEFT_RIGHT).save(path("textures", "font", f"feed_r_{team}.png"))
    for w in FEED_WIDTHS:
        Image.new("RGBA", (w, FEED_H), FEED_BG).save(path("textures", "font", f"feed_m{w}.png"))
        hi = Image.new("RGBA", (w, FEED_H), FEED_HI_BG)
        for x in range(w):
            hi.putpixel((x, 0), FEED_HI_EDGE)
            hi.putpixel((x, FEED_H - 1), FEED_HI_EDGE)
        hi.save(path("textures", "font", f"feed_h{w}.png"))

    bitmap(MARKER_ICON).save(path("textures", "font", "feed_marker.png"))
    hitmarker().save(path("textures", "font", "hitmarker.png"))
    for n, gap in CROSSHAIRS.items():
        crosshair(gap).save(path("textures", "font", f"xhair_{n}.png"))


# The crosshair, drawn by the HUD over the game's own (the HUD is drawn after it).
# The game puts its 1-pixel plus at floor((w - 15) / 2) + 7, which is half a pixel
# up and left of the middle on an even screen and on it on an odd one; a 2-pixel
# plus over the middle covers it either way, and is the thinnest one that does.
# Each glyph is the plus and, from the hip, four ticks this far out: aimed,
# standing, moving, in the air. Glyph pixel (c, r) lands at (c - R - 1, r - R - 1)
# from the middle of the screen: the engine centres the run on its width, 2R + 2,
# and an ascent of R + 4 hangs it there (fonts/icons.yml).
CROSSHAIRS = {0: None, 1: 7, 2: 10, 3: 13}
TICK = 3
PLUS = 5  # the plus reaches this far from the middle, past the game's 4.5


HIT_R = 8  # the hit marker's glyph is 2R + 1 square, centred the way the crosshair is


def hitmarker():
    """Four short diagonals round the crosshair, clear of its plus: a hit, without hiding the aim."""
    n = 2 * HIT_R + 1
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    ink = set()
    for k in range(3, 7):
        for x, y in ((k, k), (-k - 1, k), (k, -k - 1), (-k - 1, -k - 1)):
            ink.add((x + HIT_R + 1, y + HIT_R + 1))
            # two pixels wide along the diagonal, so it reads at any GUI scale
            ink.add((x + HIT_R + 1 + (1 if x >= 0 else -1), y + HIT_R + 1))
    for x, y in ink:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if 0 <= x + dx < n and 0 <= y + dy < n and (x + dx, y + dy) not in ink:
                    px[x + dx, y + dy] = (10, 12, 16, 150)
    for x, y in ink:
        px[x, y] = (255, 255, 255, 255)
    if not any(img.getpixel((n - 1, y))[3] for y in range(n)):
        px[n - 1, 0] = FAINT
    return img


def crosshair_radius(gap):
    return (gap + TICK if gap else PLUS) + 1


def crosshair(gap):
    R = crosshair_radius(gap)
    n = 2 * R + 1
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    ink = set()

    def rect(x0, x1, y0, y1):  # from the middle, half-open
        for x in range(x0, x1):
            for y in range(y0, y1):
                ink.add((x + R + 1, y + R + 1))

    rect(-1, 1, -PLUS, PLUS)
    rect(-PLUS, PLUS, -1, 1)
    if gap:
        rect(-1, 1, -(gap + TICK), -gap)
        rect(-1, 1, gap, gap + TICK)
        rect(-(gap + TICK), -gap, -1, 1)
        rect(gap, gap + TICK, -1, 1)
    for x, y in ink:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if 0 <= x + dx < n and 0 <= y + dy < n and (x + dx, y + dy) not in ink:
                    px[x + dx, y + dy] = (10, 12, 16, 160)
    for x, y in ink:
        px[x, y] = (255, 255, 255, 255)
    if not any(img.getpixel((n - 1, y))[3] for y in range(n)):
        px[n - 1, 0] = FAINT
    return img

# ---------------------------------------------------------------------------
# Sounds
# ---------------------------------------------------------------------------

SR = 44100


def env(n, attack, decay_shape):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * decay_shape(t)


def lowpass(x, cutoff):
    # one-pole, run forwards and backwards for zero phase
    a = math.exp(-2 * math.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    acc = 0.0
    for i in range(len(x) - 1, -1, -1):
        acc = (1 - a) * y[i] + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def write(name, x):
    sf.write(path("sounds", f"{name}.ogg"), norm(x).astype(np.float32), SR, format="OGG", subtype="VORBIS")


def sounds():
    rng = np.random.default_rng(7)
    # shot: a pneumatic "thup": a click, a short band of air, and a low knock
    n = int(SR * 0.22)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    air = highpass(lowpass(noise, 3800), 500) * np.exp(-t * 38)
    knock = np.sin(2 * math.pi * (180 * t - 260 * t * t)) * np.exp(-t * 45)
    click = np.zeros(n)
    click[:60] = np.hanning(120)[60:] * 1.0
    write("shot", 0.9 * air + 0.8 * knock + 0.4 * click)

    # splat: a wet slap, a burst of low noise with a few droplet ticks after it
    n = int(SR * 0.32)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    slap = lowpass(noise, 1400) * np.exp(-t * 26)
    body = np.sin(2 * math.pi * 120 * t) * np.exp(-t * 30) * 0.5
    drops = np.zeros(n)
    for k in range(5):
        at = int(SR * (0.04 + 0.05 * k + rng.uniform(0, 0.02)))
        f = rng.uniform(900, 1800)
        m = min(n - at, int(SR * 0.03))
        tt = np.arange(m) / SR
        drops[at:at + m] += np.sin(2 * math.pi * f * tt * (1 + tt * 8)) * np.exp(-tt * 140) * 0.25
    write("splat", slap + body + drops)

    # burst: the grenade, a bigger, longer splat with a thump
    n = int(SR * 0.7)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    boom = lowpass(noise, 900) * np.exp(-t * 7)
    thump = np.sin(2 * math.pi * (70 * t - 30 * t * t)) * np.exp(-t * 9)
    hiss = highpass(noise, 2500) * np.exp(-t * 14) * 0.25
    write("burst", boom + thump * 1.2 + hiss)

    # hit: the confirm ping, two quick bright notes
    n = int(SR * 0.18)
    t = np.arange(n) / SR
    tone = np.where(t < 0.06, np.sin(2 * math.pi * 1568 * t), np.sin(2 * math.pi * 2093 * t))
    write("hit", tone * np.exp(-((t % 0.06) * 30)) * np.where(t < 0.06, 1, np.exp(-(t - 0.06) * 18)))

    # empty: a dry trigger click
    n = int(SR * 0.08)
    t = np.arange(n) / SR
    write("empty", highpass(rng.standard_normal(n), 2000) * np.exp(-t * 120) + 0.4 * np.sin(2 * math.pi * 2400 * t) * np.exp(-t * 90))

    # reload: balls rattling into the hopper, then the lid snapping shut
    n = int(SR * 1.1)
    t = np.arange(n) / SR
    x = np.zeros(n)
    for _ in range(26):
        at = int(SR * rng.uniform(0.02, 0.8))
        m = min(n - at, int(SR * 0.02))
        tt = np.arange(m) / SR
        f = rng.uniform(1800, 3800)
        x[at:at + m] += np.sin(2 * math.pi * f * tt) * np.exp(-tt * 260) * rng.uniform(0.3, 0.8)
    at = int(SR * 0.92)
    m = n - at
    tt = np.arange(m) / SR
    x[at:] += highpass(rng.standard_normal(m), 1200) * np.exp(-tt * 80) * 1.2
    write("reload", x)

    # whistle: the referee's pea whistle, a trilled 2.9 kHz
    n = int(SR * 0.75)
    t = np.arange(n) / SR
    trill = 1 + 0.035 * np.sign(np.sin(2 * math.pi * 28 * t))
    phase = np.cumsum(2 * math.pi * 2900 * trill / SR)
    shape = np.clip(t / 0.02, 0, 1) * np.clip((0.75 - t) / 0.08, 0, 1)
    breath = highpass(rng.standard_normal(n), 3000) * 0.08
    write("whistle", (np.sin(phase) + 0.25 * np.sin(2 * phase) + breath) * shape)


# ---------------------------------------------------------------------------
# Definitions
# ---------------------------------------------------------------------------

def definitions():
    lines = ["# The marker: one model (assets/models/marker.json), a texture per skin, and an",
             "# aimed twin of each whose holder sees the sights. The plugin hands these out.", ""]
    codes = {"ember": "c", "tide": "9", "toxic": "a", "sunset": "6", "gold": "e"}
    lore = ["  lore:", '    - "&7Hold &fright-click&7 to fire, &fleft-click&7 to aim"',
            '    - "&fQ&7 reloads  &8·  &fF&7 scoreboard"']
    for skin, cfg in SKINS.items():
        textures = f"    textures: {{atlas: \"paintball:item/marker_{skin}\", particle: \"paintball:item/marker_{skin}\"}}"
        name = f'  name: "&{codes[skin]}&l{cfg["display"]} Marker"'
        lines += [f"marker_{skin}:", "  material: PAPER", name] + lore + ["  model:", "    parent: paintball:marker", textures, ""]
        # Aimed: the plugin swaps this in on a left click. Everybody else sees
        # the same marker; its holder sees the sights (models/marker_sights.json).
        lines += [f"marker_{skin}_aim:", "  material: PAPER", name] + lore + [
            "  model:", "    parent: paintball:marker", textures,
            "  first-person:", "    parent: paintball:marker_sights", textures, ""]
    write_text("\n".join(lines), "items", "markers.yml")

    lines = ["# Team gear and the things the plugin draws in the world:",
             "# masks are worn, balls fly, splats sit on walls, grenades are thrown.", ""]
    for team in TEAMS:
        nice = team.capitalize()
        code = "c" if team == "red" else "9"
        lines += [f"mask_{team}:", "  material: PAPER", f'  name: "&{code}{nice} Mask"', "  model:", "    parent: paintball:mask",
                  f"    textures: {{mask: \"paintball:item/mask_{team}\", particle: \"paintball:item/mask_{team}\"}}", ""]
        lines += [f"ball_{team}:", "  material: PAPER", f'  name: "&f{nice} Paintball"', "  model:", "    parent: paintball:ball",
                  f"    textures: {{ball: \"paintball:item/ball_{team}\", particle: \"paintball:item/ball_{team}\"}}", ""]
        lines += [f"grenade_{team}:", "  material: PAPER", f'  name: "&{code}&lPaint Grenade"',
                  "  lore:", '    - "&7Right-click to throw. It bursts a moment later,"', '    - "&7then recharges before you can throw again."',
                  "  model:", "    parent: paintball:grenade",
                  f"    textures: {{g: \"paintball:item/grenade_{team}\", particle: \"paintball:item/grenade_{team}\"}}", ""]
        for n in (1, 2, 3):
            lines += [f"splat_{team}_{n}:", "  material: PAPER", f'  name: "&f{nice} Splat"', "  model:", "    parent: paintball:splat",
                      f"    textures: {{splat: \"paintball:item/splat_{team}_{n}\", particle: \"paintball:item/splat_{team}_{n}\"}}", ""]
    write_text("\n".join(lines), "items", "gear.yml")

    icons = ["# Glyphs the plugin writes into titles and the action bar.", "",
             "# Full-screen paint, shown as a title when you are hit and tinted the",
             "# shooter's colour. A title is drawn four times larger and its line sits 10",
             "# above the middle, so ascent = height/2 - 3 centres the picture on screen.", ""]
    for n in (1, 2, 3):
        icons += [f"screen_splat_{n}:", f"  file: screen_splat_{n}", "  height: 96", "  ascent: 45", ""]
    icons += ["pip:", "  file: pip", "  height: 7", "  ascent: 7", "",
              "grenade_icon:", "  file: grenade_icon", "  height: 8", "  ascent: 7", ""]
    icons += ["# The HUD (huds/hud.json, drawn in Studio) prints these through its values.",
              "# Score and clock digits: 11 tall, hanging two pixels above a line of text.", ""]
    for n in range(10):
        icons += [f"digit_{n}:", "  file: digits", "  grid: { rows: 1, columns: 10, cell: " + str(n + 1) + " }",
                  "  height: 11", "  ascent: 9", ""]
    icons += ["digit_colon:", "  file: digit_colon", "  height: 11", "  ascent: 9", ""]
    icons += ["# The kill feed. Its panel is 13 tall, three pixels above and two below a line.", ""]
    names = [f"feed_l_{t}" for t in TEAMS] + [f"feed_r_{t}" for t in TEAMS]         + [f"feed_m{w}" for w in FEED_WIDTHS] + [f"feed_h{w}" for w in FEED_WIDTHS]
    for name in names:
        icons += [f"{name}:", f"  file: {name}", f"  height: {FEED_H}", "  ascent: 10", ""]
    icons += ["feed_marker:", "  file: feed_marker", "  height: 9", "  ascent: 8", "",
              "# Centred on the crosshair: half a pixel up and left of the middle, where",
              "# the game's own plus is on a screen an even number of pixels across.",
              "hitmarker:", "  file: hitmarker", f"  height: {2 * HIT_R + 1}", f"  ascent: {HIT_R + 4}", ""]
    for n, gap in CROSSHAIRS.items():
        R = crosshair_radius(gap)
        icons += [f"xhair_{n}:", f"  file: xhair_{n}", f"  height: {2 * R + 1}", f"  ascent: {R + 4}", ""]
    write_text("\n".join(icons), "fonts", "icons.yml")

    snd = ["# Synthesised by tools/make_assets.py.", ""]
    for name, cat, sub in [("shot", "player", "Paintball fired"), ("splat", "player", "Paint splats"),
                           ("burst", "player", "Paint grenade bursts"), ("hit", "player", "Hit confirmed"),
                           ("empty", "player", "Hopper empty"), ("reload", "player", "Hopper reloads"),
                           ("whistle", "master", "Referee whistle")]:
        snd += [f"{name}:", f"  file: {name}", f"  category: {cat}", f'  subtitle: "{sub}"', ""]
    write_text("\n".join(snd), "sounds", "sounds.yml")


if __name__ == "__main__":
    marker()
    mask_model()
    mask_overlay()
    ball()
    splats()
    grenade()
    screen_splats()
    small_glyphs()
    hud_glyphs()
    sounds()
    definitions()
    print("assets written to", os.path.normpath(OUT))
