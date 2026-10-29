"""
Builds every asset in the paintball content folder from scratch:

  python tools/make_assets.py

- the marker, the mask, the grenade and the paintball: models generated in ResourcePack AI
  Studio (tools/src), fitted, given namespaced textures and display transforms,
  and painted per skin or team from their own atlases;
- the splats: Studio's (tools/src/splats), painted per team, and smoothed up to
  the screen's size for the paint on a splatted player's screen;
- the HUD glyphs (kill feed, crosshair, digits) and the mask's camera overlay;
- the HUD's art (lettering, rings, the weapon cluster, the result band, the
  screen splat's fade): tools/hud_art.py;
- the sounds: generated in Studio (tools/src/sounds), trimmed and levelled.

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

import hud_art

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "src")
OUT = os.path.join(ROOT, "..", "src", "main", "resources", "content", "paintball")
A = os.path.join(OUT, "assets")

# Every paint a player can be in: the two teams' and, for a free for all, six
# more. Each gets its own ball, splats, grenade, mine, mask and kill-feed edge
# (the plugin's team/Paint.java has the same list and the same colours).
TEAMS = {
    "red": (0xFF, 0x46, 0x55), "blue": (0x2E, 0x9B, 0xFF),
    "green": (0x5B, 0xE3, 0x7D), "orange": (0xFF, 0x8A, 0x3D), "yellow": (0xFF, 0xD2, 0x3F),
    "purple": (0xA4, 0x5C, 0xFF), "pink": (0xFF, 0x5C, 0xC8), "cyan": (0x3D, 0xE0, 0xE0),
}
# The legacy colour code for each paint's item names.
CODES = {"red": "c", "blue": "9", "green": "a", "orange": "6", "yellow": "e", "purple": "5", "pink": "d", "cyan": "b"}
# Capture the flag has two sides, so two flags.
SIDES = ("red", "blue")


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
    # Free for all's last three colours.
    "violet": dict(display="Violet", body=(0.74, 0.28, 0.58), accent=0xA45CFF),
    "bubblegum": dict(display="Bubblegum", body=(0.91, 0.26, 0.80), accent=0xFF5CC8),
    "frost": dict(display="Frost", body=(0.53, 0.16, 0.82), accent=0x3DE0E0),
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
# Studio's models in team colours
# ---------------------------------------------------------------------------

def team_recolour(atlas, team, ref):
    """A Studio atlas's reds in a team's colour: `ref`, the atlas's own main red,
    becomes the team colour exactly, and every other red keeps its shading
    relative to it, highlights included. Greys, blacks and whites are left alone."""
    th, ts, tv = colorsys.rgb_to_hsv(*(c / 255 for c in TEAMS[team]))
    _, rs, rv = colorsys.rgb_to_hsv(*(c / 255 for c in ref))
    out = atlas.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, sat, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if sat < 0.12 or 15 < h * 360 < 330:  # a pink highlight is a red too
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(th, min(1, ts * sat / rs), min(1, tv * v / rv))
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def studio_model(name, key):
    """A model generated in Studio (tools/src/<name>_generated.json): its elements, every face on texture `key`."""
    model = json.load(open(os.path.join(SRC, f"{name}_generated.json"), encoding="utf-8"))
    for e in model["elements"]:
        for f in e["faces"].values():
            f["texture"] = "#" + key
    return model["elements"]


def turned(display):
    """Display transforms for a model whose front is +z rather than the game's -z (north)."""
    out = {}
    for ctx, t in display.items():
        t = dict(t)
        rx, ry, rz = t.get("rotation", [0, 0, 0])
        assert rz == 0, "a half turn about y only commutes past a rotation with no z"
        t["rotation"] = [rx, (ry + 180) % 360, rz]
        out[ctx] = t
    return out


# ---------------------------------------------------------------------------
# The mask: Studio's, fitted round the head
# ---------------------------------------------------------------------------
#
# On the head the model's 16 units are 10 of the player's pixels, so the head is
# 1.6..14.4 and the skin's outer (hat) layer, half a pixel proud of it, is
# 0.8..15.2. Everything that faces out is kept outside that box, or a skin's hat
# pixels poke through the mask (or flicker against a face that lies exactly on
# them). Studio drew the mask at about three quarters of a head, its front +z.

MASK_RED = (197, 45, 61)  # the generated atlas's shell red, which becomes each team's colour
MASK_SCALE = 1.37
MASK_CLEAR = 15.5         # the hat layer and 0.3 of air
MASK_FRONT = 0.4          # depth per Studio unit across the front stack: cheek fronts, lens, mouth guard


def mask_model():
    def fz(z):  # the cheek fronts' back plane (Studio's z = 11) sits on the clearance
        return MASK_CLEAR + (z - 11) * (MASK_FRONT if z > 11 else MASK_SCALE)

    def fit(p):
        x, y, z = p
        return [8 + (x - 8) * MASK_SCALE, 0.5 + y * MASK_SCALE, fz(z)]

    els = studio_model("mask", "mask")
    for e in els:
        a, b = fit(e["from"]), fit(e["to"])
        e["from"] = [round(min(a[i], b[i]), 3) for i in range(3)]
        e["to"] = [round(max(a[i], b[i]), 3) for i in range(3)]
        if e.get("rotation"):
            e["rotation"]["origin"] = [round(v, 3) for v in fit(e["rotation"]["origin"])]
    by = {e["name"]: e for e in els}

    def span(name, axis, lo, hi):
        by[name]["from"][axis], by[name]["to"][axis] = round(lo, 3), round(hi, 3)

    def shift(name, d):
        e = by[name]
        for i in range(3):
            e["from"][i] = round(e["from"][i] + d[i], 3)
            e["to"][i] = round(e["to"][i] + d[i], 3)
            if e.get("rotation"):
                e["rotation"]["origin"][i] = round(e["rotation"]["origin"][i] + d[i], 3)

    X, Y, Z = 0, 1, 2
    # The straps run round the outside of the hat layer, thin, the buckles on them.
    for side, (x0, x1) in (("left", (0.2, 0.5)), ("right", (15.5, 15.8))):
        span(f"strap_{side}", X, x0, x1)
        span(f"strap_{side}", Z, 0.2, 7.1)
        span(f"strap_buckle_{side}", X, x0 - 0.15 if side == "left" else x0, x1 if side == "left" else x1 + 0.15)
        span(f"strap_buckle_{side}", Z, 7.1, 8.5)
    span("strap_rear", X, 0.2, 15.8)
    span("strap_rear", Z, 0.2, 0.5)
    # The cheek guards reach back to the middle of the head, on the outside of the hat.
    span("cheek_side", X, 15.5, 16.4)
    span("cheek_side_copy", X, -0.4, 0.5)
    for name in ("cheek_side", "cheek_side_copy"):
        span(name, Z, 8.5, MASK_CLEAR)
        span(name, Y, by["cheek_front"]["from"][Y], by["strap_left"]["to"][Y])
    # The crown sits on the hair, as far into it as the hat layer allows a top to.
    span("crown", Y, 14.6, 15.5)
    # Studio's slanted cap becomes the forehead shell: straight, from the brow
    # frame up to the crown, faced with the crown's own red.
    fc = by["front_cap_facet"]
    fc.pop("rotation", None)
    fc["from"] = [by["lens_brow_frame"]["from"][X], by["lens_brow_frame"]["to"][Y], MASK_CLEAR]
    fc["to"] = [by["lens_brow_frame"]["to"][X], 15.5, by["cheek_front"]["to"][Z]]
    fc["faces"]["south"] = {"uv": [0, 0, 4, 3.5], "texture": "#mask"}
    # The goggle's wrap-round sides clear the hat at their back edges.
    shift("lens_side_frame", (0.35, 0, 0.25))
    shift("lens_side_frame_copy", (-0.35, 0, 0.25))
    # The mouth guard's angled returns, as deep as the guard now stands off the face.
    by["mouth_return_right"]["to"][X] = round(by["mouth_return_right"]["from"][X] + 1.1, 3)
    by["mouth_return_left"]["from"][X] = round(by["mouth_return_left"]["to"][X] - 1.1, 3)

    model = {
        "textures": {"mask": "paintball:item/mask_red", "particle": "paintball:item/mask_red"},
        "elements": els,
        "display": turned({
            "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
            "gui": {"rotation": [15, 160, 0], "translation": [0, 0, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 200, 0], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
        }),
    }
    save_json(model, "models", "mask.json")
    atlas = Image.open(os.path.join(SRC, "mask_atlas.png")).convert("RGBA")
    for team in TEAMS:
        team_recolour(atlas, team, MASK_RED).save(path("textures", "item", f"mask_{team}.png"))


def mask_overlay():
    """Drawn over the wearer's whole view, like a carved pumpkin: the goggle frame round the edges.

    Studio drew the goggles (tools/src/goggles.png) the other way round from an
    overlay: the lens as a dark shape, the nose bridge rising into it in grey.
    So its lens, the near-black part, is the hole here, smoothed up to the
    overlay's size, and everything else (the bridge, the lens's rim, the
    outside) is the frame."""
    w, h = 512, 256
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    art = Image.open(os.path.join(SRC, "goggles.png")).convert("RGBA")
    lens = Image.new("L", art.size, 0)
    ap, lp = art.load(), lens.load()
    for y in range(art.height):
        for x in range(art.width):
            r, g, b, a = ap[x, y]
            if a and r + g + b < 60:
                lp[x, y] = 255
    lens = lens.resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(4)).point(lambda v: 255 if v > 128 else 0)
    # Frame: dark rubber everywhere outside the lens, fading to clear.
    frame = lens.point(lambda v: 255 - v).filter(ImageFilter.GaussianBlur(9))
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
    img.save(path("textures", "misc", "mask_overlay.png"))


# ---------------------------------------------------------------------------
# Balls, splats, grenade
# ---------------------------------------------------------------------------

BALL_RED = (236, 47, 67)  # the generated atlas's main red
BALL_SCALE = 1 / 3        # Studio drew it twelve pixels across; a ball in flight is four


def ball():
    """Studio's paintball, scaled down about its middle: it flies drawn at the model's own size."""
    els = studio_model("ball", "ball")
    lo = [min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    hi = [max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    mid = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def fit(p):
        return [round(8 + (p[i] - mid[i]) * BALL_SCALE, 3) for i in range(3)]

    for e in els:
        e["from"], e["to"] = fit(e["from"]), fit(e["to"])
        if e.get("rotation"):
            e["rotation"]["origin"] = fit(e["rotation"]["origin"])
    atlas = Image.open(os.path.join(SRC, "ball_atlas.png")).convert("RGBA")
    for team in TEAMS:
        team_recolour(atlas, team, BALL_RED).save(path("textures", "item", f"ball_{team}.png"))
    model = {
        "textures": {"ball": "paintball:item/ball_red", "particle": "paintball:item/ball_red"},
        "elements": els,
        "display": {"gui": {"rotation": [30, 45, 0], "scale": [2.5, 2.5, 2.5]}},
    }
    save_json(model, "models", "ball.json")


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


def studio_splat(n):
    """One of the three splats Studio drew (tools/src/splats): grey paint, shaded, 64x64."""
    return Image.open(os.path.join(SRC, "splats", f"splat_{n}.png")).convert("RGBA")


def droplets(n, size, count, r_lo, r_hi, reach):
    """Where paint flung off a splat lands: (x, y, radius) round the middle,
    between `reach` (a share of half the size) and the edge. Seeded, so a rebuild
    draws the same ones."""
    rnd = random.Random(500 + n * 7 + size)
    c = size / 2
    out = []
    for _ in range(count):
        a = rnd.uniform(0, math.tau)
        d = c * rnd.uniform(reach, 0.92)
        r = rnd.uniform(r_lo, r_hi)
        x, y = c + math.cos(a) * d, c + math.sin(a) * d
        if r < x < size - r and r < y < size - r:
            out.append((x, y, r))
    return out


def paint(img, col, n):
    """A grey splat in a team's colour: Studio's shading stretched to the full
    range, a darker rim, its brightest highlight left whiter, and droplets flung
    round it as the hand-drawn ones had."""
    out = img.copy()
    px = out.load()
    lums = sorted((r + g + b) / 765 for y in range(out.height) for x in range(out.width)
                  for r, g, b, a in [px[x, y]] if a)
    lo, hi = lums[0], lums[-1]
    # The highlight is the brightest twentieth of the paint, however bright Studio drew the rest.
    gloss = (lums[int(len(lums) * 0.95)] - lo) / max(1e-6, hi - lo)
    w, h = out.size
    inside = lambda x, y: 0 <= x < w and 0 <= y < h and px[x, y][3] > 0
    rim = {(x, y) for y in range(h) for x in range(w) if px[x, y][3]
           and not all(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            t = ((r + g + b) / 765 - lo) / max(1e-6, hi - lo)
            c = shade(col, (0.74 + 0.36 * t) * (0.82 if (x, y) in rim else 1))
            if t > gloss and t > 0.5:
                c = mix(c, (255, 255, 255), 0.4)
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(out)
    for x, y, r in droplets(n, w, 12, 0.6, 1.8, 0.72):
        if not inside(int(x), int(y)):
            d.ellipse([x - r, y - r, x + r, y + r], fill=shade(col, 0.9) + (255,))
    return out


def splats():
    for n in (1, 2, 3):
        art = studio_splat(n)
        for team, col in TEAMS.items():
            paint(art, col, n).save(path("textures", "item", f"splat_{team}_{n}.png"))
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


GRENADE_RED = (207, 48, 64)  # the generated atlas's can red
GRENADE_SCALE = 0.68         # Studio's can is ten pixels across; in flight it is drawn at the model's own size


def grenade():
    """Studio's paint grenade, scaled down about the middle of the can, which it spins round in flight."""
    els = studio_model("grenade", "g")
    lo = [min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    hi = [max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    mid = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def fit(p):
        return [round(8 + (p[i] - mid[i]) * GRENADE_SCALE, 3) for i in range(3)]

    for e in els:
        e["from"], e["to"] = fit(e["from"]), fit(e["to"])
        if e.get("rotation"):
            e["rotation"]["origin"] = fit(e["rotation"]["origin"])
    model = {
        "textures": {"g": "paintball:item/grenade_red", "particle": "paintball:item/grenade_red"},
        "elements": els,
        "display": {
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -20, 0], "translation": [1, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, 20, 0], "translation": [-1, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [25, 45, 0], "translation": [0, 0, 0], "scale": [1.3, 1.3, 1.3]},
            "ground": {"scale": [0.4, 0.4, 0.4]},
            "fixed": {"scale": [0.9, 0.9, 0.9]},
        },
    }
    save_json(model, "models", "grenade.json")
    atlas = Image.open(os.path.join(SRC, "grenade_atlas.png")).convert("RGBA")
    for team in TEAMS:
        team_recolour(atlas, team, GRENADE_RED).save(path("textures", "item", f"grenade_{team}.png"))


def flag():
    """Studio's capture-the-flag flag, stood on the middle of the model's floor
    and coloured per side. The plugin draws it on an item display and sizes it
    there, standing at a base or carried on a back."""
    src = os.path.join(SRC, "flag_generated.json")
    if not os.path.exists(src):
        return False
    els = studio_model("flag", "f")
    lo = [min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    hi = [max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3)]
    # Sixteen units tall, its foot on the floor and its pole in the middle of
    # the model, so the display's origin is where the pole meets the ground and
    # the banner flies off to one side of it.
    pole = next(e for e in els if "pole" in e.get("name", "") and "socket" not in e.get("name", ""))
    px = (pole["from"][0] + pole["to"][0]) / 2
    pz = (pole["from"][2] + pole["to"][2]) / 2
    k = 16 / (hi[1] - lo[1])

    def fit(p):
        return [round(8 + (p[0] - px) * k, 3),
                round((p[1] - lo[1]) * k, 3),
                round(8 + (p[2] - pz) * k, 3)]

    for e in els:
        e["from"], e["to"] = fit(e["from"]), fit(e["to"])
        # Studio's exact turn of the banner's panels, which a model file has no
        # word for: the legal angle beside it is what the game draws.
        e.pop("free_rotation", None)
        if e.get("rotation"):
            e["rotation"]["origin"] = fit(e["rotation"]["origin"])
    model = {
        "textures": {"f": "paintball:item/flag_red", "particle": "paintball:item/flag_red"},
        "elements": els,
        "display": {
            "gui": {"rotation": [20, 45, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
            "ground": {"scale": [0.5, 0.5, 0.5]},
            "fixed": {"scale": [1, 1, 1]},
        },
    }
    save_json(model, "models", "flag.json")
    atlas = Image.open(os.path.join(SRC, "flag_atlas.png")).convert("RGBA")
    ref = main_red(atlas)
    for side in SIDES:
        team_recolour(atlas, side, ref).save(path("textures", "item", f"flag_{side}.png"))
    return True


def main_red(atlas):
    """The red that covers most of an atlas: what becomes the side's colour exactly."""
    counts = {}
    for r, g, b, a in atlas.getdata():
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s >= 0.35 and v >= 0.35 and (h * 360 <= 15 or h * 360 >= 330):
            counts[(r, g, b)] = counts.get((r, g, b), 0) + 1
    return max(counts, key=counts.get) if counts else (220, 40, 50)


# The flag icons beside capture the flag's score: 9 wide, 11 tall, the digits'
# height. Home is the banner solid, taken is its outline, dropped is the banner
# fallen to the foot of the pole.
FLAG_ICONS = {
    "home": [
        "##.......",
        "#######..",
        "########.",
        "#######..",
        "########.",
        "#######..",
        "#........",
        "#........",
        "#........",
        "#........",
        "###......",
    ],
    "taken": [
        "##.......",
        "#######..",
        "#.....##.",
        "#.....#..",
        "#.....##.",
        "#######..",
        "#........",
        "#........",
        "#........",
        "#........",
        "###......",
    ],
    "dropped": [
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "..######.",
        ".#######.",
        "..######.",
        ".#######.",
        "#########",
    ],
}


def flag_icons():
    for side in SIDES:
        rgb = TEAMS[side]
        for state, rows in FLAG_ICONS.items():
            img = Image.new("RGBA", (9, 11), (0, 0, 0, 0))
            px = img.load()
            for y, row in enumerate(rows):
                for x, c in enumerate(row):
                    if c != "#":
                        continue
                    pole = x == 0 and state != "dropped"
                    px[x, y] = ((236, 236, 236, 255) if pole else rgb + (255,))
            # A clear right column keeps the advance the cell's (see bitmap()).
            if not any(img.getpixel((8, y))[3] for y in range(11)):
                px[8, 0] = FAINT
            img.save(path("textures", "font", f"flag_{side}_{state}.png"))


# ---------------------------------------------------------------------------
# Font glyphs
# ---------------------------------------------------------------------------

def screen_splats():
    """The paint on a splatted player's screen: Studio's splats, their outlines
    smoothed up to the screen's size, glossed, with drips running off the
    bottom. White, shaded grey: the HUD run's colour paints it the shooter's."""
    for n in (1, 2, 3):
        alpha = studio_splat(n).split()[3]
        big = alpha.resize((256, 256), Image.BICUBIC).filter(ImageFilter.GaussianBlur(3)).point(lambda v: 255 if v > 128 else 0)
        bd = ImageDraw.Draw(big)
        for x, y, r in droplets(n, 256, 14, 2.5, 7, 0.74):
            bd.ellipse([x - r, y - r, x + r, y + r], fill=255)
        mask = big.filter(ImageFilter.GaussianBlur(1.2))
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
    # Grenade icon, for the kill feed: a little can, 7x8.
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
    "1": ["...##..", "..###..", ".####..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", "...##..", ".######"],
    "2": [".#####.", "##...##", ".....##", ".....##", "....##.", "...##..", "..##...", ".##....", "##.....", "##.....", "#######"],
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

SOUNDS = [("shot", "player", "Paintball fired"), ("splat", "player", "Paint splats"),
          ("burst", "player", "Paint grenade bursts"), ("hit", "player", "Hit confirmed"),
          ("empty", "player", "Hopper empty"), ("reload", "player", "Hopper reloads"),
          ("whistle", "master", "Referee whistle")]


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def sounds():
    """Studio's sounds (tools/src/sounds), cut to start on their first sound and
    end on their last, faded over the cut, and levelled to one peak. A shot
    that starts a quarter of a second late reads as lag, not as a sound."""
    for name, _, _ in SOUNDS:
        src = os.path.join(SRC, "sounds", f"{name}.ogg")
        dst = path("sounds", f"{name}.ogg")
        # Vorbis never encodes the same file twice, so an untouched source is left alone.
        if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
            continue
        x, sr = sf.read(src)
        if x.ndim > 1:
            x = x.mean(axis=1)
        # Loudness in 5ms windows: a stray sample is not where a sound starts.
        w = int(sr * 0.005)
        rms = np.array([np.sqrt(np.mean(x[i:i + w] ** 2)) for i in range(0, len(x) - w + 1, w)])
        start = np.nonzero(rms > rms.max() * 0.08)[0][0] * w
        end = (np.nonzero(rms > rms.max() * 0.02)[0][-1] + 1) * w
        x = x[max(0, start - int(sr * 0.003)):min(len(x), end + int(sr * 0.03))].copy()
        fade = min(len(x), int(sr * 0.03))
        x[-fade:] *= np.linspace(1, 0, fade)
        # 0.8 rather than full scale: Vorbis overshoots a peak it is handed.
        sf.write(dst, norm(x, 0.8).astype(np.float32), sr, format="OGG", subtype="VORBIS")


# ---------------------------------------------------------------------------
# Definitions
# ---------------------------------------------------------------------------

def definitions():
    lines = ["# The marker: one model (assets/models/marker.json), a texture per skin, and an",
             "# aimed twin of each whose holder sees the sights. The plugin hands these out.", ""]
    codes = {"ember": "c", "tide": "9", "toxic": "a", "sunset": "6", "gold": "e", "violet": "5", "bubblegum": "d", "frost": "b"}
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
        code = CODES[team]
        lines += [f"mask_{team}:", "  material: PAPER", f'  name: "&{code}{nice} Mask"', "  model:", "    parent: paintball:mask",
                  f"    textures: {{mask: \"paintball:item/mask_{team}\", particle: \"paintball:item/mask_{team}\"}}", ""]
        lines += [f"ball_{team}:", "  material: PAPER", f'  name: "&f{nice} Paintball"', "  model:", "    parent: paintball:ball",
                  f"    textures: {{ball: \"paintball:item/ball_{team}\", particle: \"paintball:item/ball_{team}\"}}", ""]
        lines += [f"grenade_{team}:", "  material: PAPER", f'  name: "&{code}&lPaint Grenade"',
                  "  lore:", '    - "&7Right-click to throw. It bursts a moment later,"', '    - "&7then recharges before you can throw again."',
                  "  model:", "    parent: paintball:grenade",
                  f"    textures: {{g: \"paintball:item/grenade_{team}\", particle: \"paintball:item/grenade_{team}\"}}", ""]
        # A mine is the grenade, which the plugin squashes flat when it is set down.
        lines += [f"mine_{team}:", "  material: PAPER", f'  name: "&{code}&lPaint Mine"',
                  "  lore:", '    - "&7Right-click the ground to set it. The first enemy"', '    - "&7to step on it sets it off."',
                  "  model:", "    parent: paintball:grenade",
                  f"    textures: {{g: \"paintball:item/grenade_{team}\", particle: \"paintball:item/grenade_{team}\"}}", ""]
        for n in (1, 2, 3):
            lines += [f"splat_{team}_{n}:", "  material: PAPER", f'  name: "&f{nice} Splat"', "  model:", "    parent: paintball:splat",
                      f"    textures: {{splat: \"paintball:item/splat_{team}_{n}\", particle: \"paintball:item/splat_{team}_{n}\"}}", ""]
    if os.path.exists(path("models", "flag.json")):
        lines += ["# Capture the flag's flags, drawn by the plugin at each base and on a carrier's back.", ""]
        for side in SIDES:
            lines += [f"flag_{side}:", "  material: PAPER", f'  name: "&{CODES[side]}&l{side.capitalize()} Flag"', "  model:",
                      "    parent: paintball:flag",
                      f"    textures: {{f: \"paintball:item/flag_{side}\", particle: \"paintball:item/flag_{side}\"}}", ""]
    write_text("\n".join(lines), "items", "gear.yml")

    icons = ["# Glyphs the plugin writes into the HUD (huds/hud.json). Generated by",
             "# tools/make_assets.py and tools/hud_art.py: edit those, not this.", "",
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
    icons += ["# Capture the flag: each flag's state, beside its team's score.", ""]
    for side in SIDES:
        for state in FLAG_ICONS:
            icons += [f"flag_{side}_{state}:", f"  file: flag_{side}_{state}", "  height: 11", "  ascent: 9", ""]
    icons += ["feed_marker:", "  file: feed_marker", "  height: 9", "  ascent: 8", "",
              "# Centred on the crosshair: half a pixel up and left of the middle, where",
              "# the game's own plus is on a screen an even number of pixels across.",
              "hitmarker:", "  file: hitmarker", f"  height: {2 * HIT_R + 1}", f"  ascent: {HIT_R + 4}", ""]
    for n, gap in CROSSHAIRS.items():
        R = crosshair_radius(gap)
        icons += [f"xhair_{n}:", f"  file: xhair_{n}", f"  height: {2 * R + 1}", f"  ascent: {R + 4}", ""]
    icons += hud_art.build(path("textures", "font", "x")[:-2])
    write_text("\n".join(icons), "fonts", "icons.yml")

    snd = ["# Generated in ResourcePack AI Studio; trimmed and levelled by tools/make_assets.py.", ""]
    for name, cat, sub in SOUNDS:
        snd += [f"{name}:", f"  file: {name}", f"  category: {cat}", f'  subtitle: "{sub}"', ""]
    write_text("\n".join(snd), "sounds", "sounds.yml")


if __name__ == "__main__":
    marker()
    mask_model()
    mask_overlay()
    ball()
    splats()
    grenade()
    flag()
    flag_icons()
    screen_splats()
    small_glyphs()
    hud_glyphs()
    sounds()
    definitions()
    print("assets written to", os.path.normpath(OUT))
