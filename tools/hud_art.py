"""
The HUD's art, drawn here: everything the plugin writes into the HUD's runs
that is a picture rather than words. make_assets.py calls build() and puts the
icon definitions it returns into fonts/icons.yml.

A run is one colour, and that colour is its address in the HUD's shader, so a
picture in more than one colour has to arrive already coloured, in a run drawn
white. That is why the lettering below comes in sheets per colour, and why the
weapon cluster at the bottom right is one picture per state rather than a
panel the plugin tints.

Three rules every glyph here follows:

- **Inked in its rightmost column.** The game measures a bitmap glyph's
  advance from its last non-transparent column and RP Engine measures it from
  the cell; the two agree only when the cell's last column has something in
  it, so a glyph that does not reach it gets a pixel nobody can see (FAINT).
- **Vertically placed by its ascent.** A glyph's top is drawn at the line's top
  + 7 - ascent, so pictures that sit at different heights in one run are
  different glyphs (the same texture, another ascent). Centred art uses
  ascent = h / 2 + 3, which puts its middle on the line's middle.
- **At most 256 texels on a side**, the font atlas's page: the game draws
  nothing for a bigger glyph.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

FAINT = (255, 255, 255, 1)
OUTLINE = (10, 12, 16, 235)

WHITE = (255, 255, 255)
GOLD = (255, 210, 63)
GREEN = (91, 227, 125)
RED = (255, 70, 85)
BLUE = (46, 155, 255)
GREY = (170, 176, 184)
DIM = (120, 126, 134)

# ---------------------------------------------------------------------------
# Lettering: 7x11 capitals in the score digits' own style (two-pixel strokes,
# corners cut), drawn with a one-pixel outline, so 9x13 cells.
# ---------------------------------------------------------------------------

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
}

CAPS = {
    "A": [".#####.", "##...##", "##...##", "##...##", "##...##", "#######", "##...##", "##...##", "##...##", "##...##", "##...##"],
    "B": ["######.", "##...##", "##...##", "##...##", "######.", "##...##", "##...##", "##...##", "##...##", "##...##", "######."],
    "C": [".#####.", "##...##", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "##.....", "##...##", ".#####."],
    "D": ["######.", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "######."],
    "E": ["#######", "##.....", "##.....", "##.....", "######.", "##.....", "##.....", "##.....", "##.....", "##.....", "#######"],
    "F": ["#######", "##.....", "##.....", "##.....", "######.", "##.....", "##.....", "##.....", "##.....", "##.....", "##....."],
    "G": [".#####.", "##...##", "##.....", "##.....", "##.....", "##.####", "##...##", "##...##", "##...##", "##...##", ".#####."],
    "H": ["##...##"] * 5 + ["#######"] + ["##...##"] * 5,
    "I": [".#####."] + ["..###.."] * 9 + [".#####."],
    "J": ["..#####", ".....##", ".....##", ".....##", ".....##", ".....##", ".....##", ".....##", ".....##", "##...##", ".#####."],
    "K": ["##...##", "##...##", "##..##.", "##.##..", "####...", "###....", "####...", "##.##..", "##..##.", "##...##", "##...##"],
    "L": ["##....."] * 10 + ["#######"],
    "M": ["##...##", "###.###", "#######", "##.#.##", "##.#.##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##"],
    "N": ["##...##", "###..##", "###..##", "####.##", "##.####", "##..###", "##..###", "##...##", "##...##", "##...##", "##...##"],
    "O": [".#####."] + ["##...##"] * 9 + [".#####."],
    "P": ["######.", "##...##", "##...##", "##...##", "##...##", "######.", "##.....", "##.....", "##.....", "##.....", "##....."],
    "Q": [".#####.", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##...##", "##.#.##", "##..##.", ".###.##"],
    "R": ["######.", "##...##", "##...##", "##...##", "##...##", "######.", "####...", "##.##..", "##..##.", "##...##", "##...##"],
    "S": [".#####.", "##...##", "##.....", "##.....", ".#####.", ".....##", ".....##", ".....##", ".....##", "##...##", ".#####."],
    "T": ["#######"] + ["..###.."] * 10,
    "U": ["##...##"] * 10 + [".#####."],
    "V": ["##...##"] * 6 + [".##.##.", ".##.##.", ".##.##.", "..###..", "..###.."],
    "W": ["##...##"] * 6 + ["##.#.##", "##.#.##", "#######", "###.###", "##...##"],
    "X": ["##...##", "##...##", ".##.##.", ".##.##.", "..###..", "..###..", "..###..", ".##.##.", ".##.##.", "##...##", "##...##"],
    "Y": ["##...##", "##...##", "##...##", ".##.##.", ".##.##.", "..###.."] + ["..###.."] * 5,
    "Z": ["#######", ".....##", ".....##", "....##.", "...##..", "..##...", ".##....", "##.....", "##.....", "##.....", "#######"],
    "!": ["..###.."] * 7 + ["......."] * 2 + ["..###.."] * 2,
    "-": ["......."] * 4 + [".#####.", ".#####."] + ["......."] * 5,
}

# Every character a lettering sheet holds, in sheet order. The plugin names a
# glyph by character, so this string is the whole contract with it.
CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!-"
CELL_W, CELL_H = 9, 13


def shade(rgb, k):
    return tuple(max(0, min(255, int(round(c * k)))) for c in rgb)


def _pattern(ch):
    return DIGITS.get(ch) or CAPS[ch]


def letter(ch, colour):
    """One 9x13 cell: the character in `colour`, lighter at the top, outlined."""
    rows = _pattern(ch)
    img = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    px = img.load()
    ink = set()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                ink.add((x + 1, y + 1))
    for x, y in ink:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in ink and 0 <= q[0] < CELL_W and 0 <= q[1] < CELL_H:
                    px[q] = OUTLINE
    for x, y in ink:
        k = 1.06 - (y - 1) / 10 * 0.24
        px[x, y] = shade(colour, k) + (255,)
    if not any(px[CELL_W - 1, y][3] for y in range(CELL_H)):
        px[CELL_W - 1, 0] = FAINT
    return img


# (sheet name, colour) and the scales each is used at. Scale n draws a cell
# n texels to the pixel, so every size stays crisp.
LETTERING = {
    "white": (WHITE, (2, 3, 4)),
    "grey": (GREY, (1, 2, 3, 4, 5)),
    "gold": (GOLD, (1, 3, 4, 5)),
    "green": (GREEN, (4, 5, 6)),
    "red": (RED, (2, 3, 4, 5)),
    "blue": (BLUE, (2,)),
}


def char_id(ch):
    return {"!": "bang", "-": "dash"}.get(ch, ch.lower())


def centred_ascent(h):
    """The ascent that puts a glyph h tall with its middle on the line's middle."""
    return h // 2 + 3


# ---------------------------------------------------------------------------
# Small grey digits (5x7) and a slash: the hopper size after the ammo count.
# ---------------------------------------------------------------------------

SMALL = {
    "0": [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    "3": ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
    "4": ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    "6": [".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."],
    "7": ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
    "8": [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
    "9": [".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."],
    "/": ["....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."],
}
SMALL_CHARS = "0123456789/"


def small_sheet(colour):
    sheet = Image.new("RGBA", (5 * len(SMALL_CHARS), 7), (0, 0, 0, 0))
    px = sheet.load()
    for i, ch in enumerate(SMALL_CHARS):
        for y, row in enumerate(SMALL[ch]):
            for x, c in enumerate(row):
                if c == "#":
                    px[i * 5 + x, y] = colour + (255,)
        if not any(px[i * 5 + 4, y][3] for y in range(7)):
            px[i * 5 + 4, 0] = FAINT
    return sheet


# ---------------------------------------------------------------------------
# Rings: the countdown round the numbers. Drawn smooth, at twice the pixel.
# ---------------------------------------------------------------------------

def ring(size, thickness, remaining, colour, track_alpha=55, ss=4):
    """A ring `size` texels across: a faint full track and an arc of `colour`
    for the part still to go, from twelve o'clock, emptying clockwise."""
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = ss * 2
    box = [pad, pad, S - pad - 1, S - pad - 1]
    w = thickness * ss
    # A soft dark halo under it, so it reads over a bright sky.
    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse(box, outline=(8, 10, 14, 150), width=w + ss * 2)
    img = Image.alpha_composite(img, halo.filter(ImageFilter.GaussianBlur(ss)))
    d = ImageDraw.Draw(img)
    d.ellipse(box, outline=(255, 255, 255, track_alpha), width=w)
    if remaining > 0.001:
        start = -90 + 360 * (1 - remaining)
        d.arc(box, start=start, end=270, fill=colour + (255,), width=w)
        # A bright head where the arc ends.
        a = math.radians(start)
        r = (S - 2 * pad) / 2 - w / 2
        cx = cy = S / 2
        hx, hy = cx + r * math.cos(a), cy + r * math.sin(a)
        d.ellipse([hx - w * 0.75, hy - w * 0.75, hx + w * 0.75, hy + w * 0.75], fill=shade(colour, 1.15) + (255,))
    out = img.resize((size, size), Image.LANCZOS)
    px = out.load()
    if not any(px[size - 1, y][3] for y in range(size)):
        px[size - 1, 0] = FAINT
    return out


# ---------------------------------------------------------------------------
# The weapon cluster, bottom right: the grenade card and the ammo panel.
# ---------------------------------------------------------------------------
#
# Both are 30 tall and hang from the run's line (their tops at its top + 7, so
# ascent 0); the plugin lays them side by side with the ammo count drawn over
# the panel. The positions below are the contract with Hud.java.

CLUSTER_H = 30
CARD_W = 26
PANEL_W = 74
BAR_X, BAR_W, BAR_Y = 6, 62, 22          # the ammo bar, in the panel
AMMO_TOP = 6                             # the big digits' top, in the panel
SMALL_TOP = AMMO_TOP + 11 - 7            # the hopper size, bottoms level with the count
CARD_FRAMES = 19                         # recharge 0..18; 18 is ready
LOW_AMMO = 0.25                          # at or under this share of the hopper the bar is red

PANEL_BG = (11, 15, 20, 190)
PANEL_EDGE = (255, 255, 255, 34)
TRACK = (44, 50, 58, 255)


def panel(w, h):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 0, w - 2, h - 1], fill=PANEL_BG)
    d.rectangle([0, 1, w - 1, h - 2], fill=PANEL_BG)
    d.line([1, 0, w - 2, 0], fill=PANEL_EDGE)
    return img


def ammo_panel(level, low):
    img = panel(PANEL_W, CLUSTER_H)
    d = ImageDraw.Draw(img)
    d.rectangle([BAR_X, BAR_Y, BAR_X + BAR_W - 1, BAR_Y + 1], fill=TRACK)
    if level > 0:
        col = RED if low else WHITE
        d.rectangle([BAR_X, BAR_Y, BAR_X + level - 1, BAR_Y + 1], fill=col + (255,))
    return img


GRENADE_ICON = [
    "....##......",
    "...#..#.....",
    "....##......",
    "..########..",
    ".##########.",
    ".##########.",
    "############",
    "############",
    "============",
    "============",
    "############",
    "############",
    ".##########.",
    "..########..",
]


def grenade_icon(fill_rows, ready):
    """12x14: the can, lit from the left; the rows from the bottom up to
    `fill_rows` bright, the rest dim. The band round it is gold when ready."""
    img = Image.new("RGBA", (12, 14), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(GRENADE_ICON):
        lit = (13 - y) < fill_rows
        for x, c in enumerate(row):
            if c == ".":
                continue
            if c == "=":
                base = GOLD if ready else (215, 219, 224)
            elif y <= 2:
                base = (196, 202, 210)
            else:
                base = (236, 238, 242)
            k = 1.08 - x / 11 * 0.3
            col = shade(base, k)
            if not lit:
                col = shade((90, 96, 104), k)
            px[x, y] = col + (255 if lit else 210,)
    return img


def grenade_card(frame):
    ready = frame >= CARD_FRAMES - 1
    img = panel(CARD_W, CLUSTER_H)
    d = ImageDraw.Draw(img)
    # The key it is on: slot 2 of the hotbar.
    d.rectangle([3, 3, 9, 11], fill=(255, 255, 255, 30))
    d.rectangle([3, 3, 9, 3], fill=(255, 255, 255, 60))
    two = SMALL["2"]
    for y, row in enumerate(two):
        for x, c in enumerate(row):
            if c == "#":
                img.putpixel((4 + x, 4 + y), GREY + (255,))
    rows = round(14 * frame / (CARD_FRAMES - 1))
    img.alpha_composite(grenade_icon(rows, ready), (8, 8))
    # The recharge bar, under the can.
    d.rectangle([4, 25, 21, 26], fill=TRACK)
    span = round(18 * frame / (CARD_FRAMES - 1))
    if span:
        d.rectangle([4, 25, 4 + span - 1, 26], fill=(GOLD if ready else WHITE) + (255,))
    return img


# ---------------------------------------------------------------------------
# The result band: a dark strip behind VICTORY / DEFEAT / DRAW, fading out at
# both ends, with a line of the result's colour along its top and bottom.
# Two tiles, each under the atlas page's 256.
# ---------------------------------------------------------------------------

BAND_W, BAND_H = 320, 50


def band(colour):
    img = Image.new("RGBA", (BAND_W, BAND_H), (0, 0, 0, 0))
    px = img.load()
    for x in range(BAND_W):
        edge = min(x, BAND_W - 1 - x) / 70
        a = min(1.0, edge)
        a = a * a * (3 - 2 * a)
        for y in range(BAND_H):
            if y in (0, BAND_H - 1):
                px[x, y] = colour + (int(230 * a),)
            elif y in (1, BAND_H - 2):
                px[x, y] = colour + (int(70 * a),)
            else:
                v = 1 - abs(y - BAND_H / 2) / (BAND_H / 2) * 0.25
                px[x, y] = (8, 10, 14, int(200 * a * v))
    return img


# ---------------------------------------------------------------------------
# The screen splat, faded out in steps: the paint the victim's view gets.
# ---------------------------------------------------------------------------

SPLAT_FADE = (1.0, 0.82, 0.62, 0.42, 0.22)
SPLAT_SIZE = 256


def splat_frames(src):
    base = Image.open(src).convert("RGBA").resize((SPLAT_SIZE, SPLAT_SIZE), Image.LANCZOS)
    r, g, b, a = base.split()
    return [Image.merge("RGBA", (r, g, b, a.point(lambda v, f=f: int(v * f)))) for f in SPLAT_FADE]


# ---------------------------------------------------------------------------
# Everything, written and declared.
# ---------------------------------------------------------------------------

def pack(cells):
    """Cells of one size into a sheet RP Engine can cut: at most 16 rows and
    16 columns. Returns the sheet, its (rows, columns) and each cell's number."""
    n = len(cells)
    cols = min(16, math.ceil(math.sqrt(n)))
    rows = math.ceil(n / cols)
    assert rows <= 16, n
    w, h = cells[0].size
    sheet = Image.new("RGBA", (w * cols, h * rows), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % cols) * w, (i // cols) * h))
    return sheet, rows, cols


def build(font_dir):
    """Writes the textures into `font_dir` and returns the icons.yml lines for them."""
    os.makedirs(font_dir, exist_ok=True)

    def save(img, name):
        img.save(os.path.join(font_dir, name + ".png"))

    out = ["# ---- The HUD's art, from tools/hud_art.py ----", ""]

    def icon(name, file, h, ascent, grid=None):
        out.append(f"{name}:")
        out.append(f"  file: {file}")
        if grid:
            rows, cols, cell = grid
            out.append(f"  grid: {{ rows: {rows}, columns: {cols}, cell: {cell} }}")
        out.append(f"  height: {h}")
        out.append(f"  ascent: {ascent}")
        out.append("")

    out += ["# Lettering, centred on the line: t_<colour><scale>_<char>, e.g. t_gold3_v.", ""]
    for sheet, (colour, scales) in LETTERING.items():
        img, rows, cols = pack([letter(ch, colour) for ch in CHARSET])
        save(img, f"type_{sheet}")
        for s in scales:
            h = CELL_H * s
            for i, ch in enumerate(CHARSET):
                icon(f"t_{sheet}{s}_{char_id(ch)}", f"type_{sheet}", h, centred_ascent(h), (rows, cols, i + 1))

    out += ["# The hopper size after the ammo count, in the weapon cluster.", ""]
    save(small_sheet(GREY), "small_grey")
    for i, ch in enumerate(SMALL_CHARS):
        icon(f"w_small_{'slash' if ch == '/' else ch}", "small_grey", 7, -SMALL_TOP, (1, len(SMALL_CHARS), i + 1))

    out += ["# The ammo count, 11 tall, hanging from the cluster's top like everything in it.", ""]
    big = Image.new("RGBA", (7 * 10, 11), (0, 0, 0, 0))
    for n in range(10):
        cell = Image.new("RGBA", (7, 11), (0, 0, 0, 0))
        for y, row in enumerate(DIGITS[str(n)]):
            for x, c in enumerate(row):
                if c == "#":
                    cell.putpixel((x, y), (255, 255, 255, 255))
        if not any(cell.getpixel((6, y))[3] for y in range(11)):
            cell.putpixel((6, 0), FAINT)
        big.paste(cell, (n * 7, 0))
    save(big, "w_digits")
    for n in range(10):
        icon(f"w_digit_{n}", "w_digits", 11, -AMMO_TOP, (1, 10, n + 1))

    out += ["# The ammo panel with its bar at every length, white and (low) red.", ""]
    for low in (False, True):
        img, rows, cols = pack([ammo_panel(level, low) for level in range(BAR_W + 1)])
        name = "w_panel_low" if low else "w_panel"
        save(img, name)
        for level in range(BAR_W + 1):
            icon(f"{name}_{level}", name, CLUSTER_H, 0, (rows, cols, level + 1))

    out += ["# The grenade card, recharging: frame 18 is ready.", ""]
    img, rows, cols = pack([grenade_card(k) for k in range(CARD_FRAMES)])
    save(img, "w_card")
    for k in range(CARD_FRAMES):
        icon(f"w_card_{k}", "w_card", CLUSTER_H, 0, (rows, cols, k + 1))

    out += ["# Countdown rings, emptying: the match start's (gold, a second a lap) and",
            "# the respawn's (white, the whole wait a lap). Drawn at twice the pixel.", ""]
    for name, size, thick, frames, colour in (("ring_start", 112, 6, 10, GOLD), ("ring_respawn", 84, 5, 30, WHITE)):
        img, rows, cols = pack([ring(size, thick, 1 - k / frames, colour) for k in range(frames)])
        save(img, name)
        h = size // 2
        for k in range(frames):
            icon(f"{name}_{k}", name, h, centred_ascent(h), (rows, cols, k + 1))

    out += ["# The band behind the result, in two tiles.", ""]
    for name, colour in (("victory", GOLD), ("defeat", RED), ("draw", GREY)):
        b = band(colour)
        half = BAND_W // 2
        for i, (x0, x1) in enumerate(((0, half), (half, BAND_W))):
            tile = b.crop((x0, 0, x1, BAND_H))
            if not any(tile.getpixel((tile.width - 1, y))[3] for y in range(BAND_H)):
                tile.putpixel((tile.width - 1, 0), FAINT)
            save(tile, f"band_{name}_{i}")
            icon(f"band_{name}_{i}", f"band_{name}_{i}", BAND_H, centred_ascent(BAND_H))

    out += ["# The paint on the screen of whoever was just hit, fading: tinted by the run.", ""]
    for n in (1, 2, 3):
        img, rows, cols = pack(splat_frames(os.path.join(font_dir, f"screen_splat_{n}.png")))
        save(img, f"hud_splat_{n}")
        for k in range(len(SPLAT_FADE)):
            icon(f"hud_splat_{n}_{k}", f"hud_splat_{n}", SPLAT_SIZE, centred_ascent(SPLAT_SIZE), (rows, cols, k + 1))
    return out
