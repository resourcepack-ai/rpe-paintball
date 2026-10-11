# Paintball

5v5 paintball for Minecraft servers, built on
[RP Engine](https://github.com/resourcepack-ai/rp-engine). One mode: a hit
splats you, you're back in three seconds, and the team with more kills when the
clock runs out wins.

It is an RP Engine addon, and it uses nothing but the engine's public API
(`ai.resourcepack.engine.api`). Everything a player sees, from the marker in
their hand to the whole HUD, is ordinary RP Engine content shipped inside the
jar, so it doubles as a worked example of an addon.

## Requirements

- **Spigot or Paper, 1.21.11 or newer**, on Java 21. The marker needs item
  components 1.21.11 added (hold to fire, no swing, no reach).
- **[RP Engine](https://resourcepack.ai/rp-engine) 0.2.3 or newer.** Paintball
  checks the engine's version as it starts, and on anything older says so in
  the console and disables itself.

## Installing

1. Put `RPEngine.jar` and `RPE-paintball-<version>.jar` in `plugins/` and start
   the server.
2. Paintball installs its content into `plugins/RPEngine/content/paintball/` and
   reloads the engine once, so the resource pack the engine serves now carries
   it.
3. As an op, stand somewhere flat and run `/pb arena generate main confirm`.
4. `/pb join`, then `/pb start`. Alone is fine.

If you run Multiverse-Core, Paintball loads after it, so an arena in one of its
worlds is found.

## Playing

| | |
|---|---|
| `/pb join` | Into the lobby (the waiting box over the field) |
| `/pb team red\|blue\|auto` | The side you'd like, from the next match; the red and blue items in the lobby's hotbar do the same. Picks are kept within one player of even: whoever picked a crowded side last is moved, and told |
| `/pb start` | Staff: starts the match now, even if you're alone (it joins you first) |
| `/pb stop` | Staff: ends the match (scoreboard, then back to the lobby); in the lobby, sends everybody home |
| `/pb leave` | Out, with your inventory and position back |
| **F** or `/pb scoreboard` | The match scoreboard |
| **Tab** | The live player list: score and clock on top, each player's kills / deaths |

In a match: hold right-click to fire, left-click to aim down the sights (zoomed,
slower, no spread), Q reloads (it also reloads itself when empty), right-click the
grenade to throw it. Every ball is fired so that, drop and all, it lands exactly on
the crosshair; from the hip it strays by the spread the crosshair's ticks show.

`/pb as <player> <subcommand>` runs a subcommand for somebody else, for NPCs and
anything else that can only run console commands.

## Arenas (staff)

`/pb arena generate <id> confirm [name]` builds a speedball field where you stand and
sets it up. From the console: `/pb arena generate <id> at <world> <x> <y> <z> [name]`.
By hand: `create`, `setwaiting`, `addspawn <red|blue>`, `wand` + `setbounds`, `enable`.
`/pb arena list` shows every arena and what it still needs; `/pb help` lists the
rest (`disable`, `clearspawns`, `tp`, `name`, `delete`).

`/pb arena disable <id>` closes an arena until `enable` opens it again: nobody
can join it or start a match on it, however they ask (`/pb join <id>`,
`/pb start <id>`, `/pb as`). Its lobby is sent home, and a match being played on
it ends the way `/pb stop` ends one (the result, the scoreboard), after which
everybody goes home rather than back to the lobby. `/pb arena delete` does the
same. Staff can still `tp` to a closed arena and change it.

## Configuration

`plugins/RPEPaintball/config.yml` has the numbers (match length, kill limit,
respawn time, fire rate, ball speed, grenade cooldown...), each team's marker,
and every word a player reads, in MiniMessage under `messages:`. `/pb reload`
re-reads it. Options a newer version adds are written into your existing file
on start.

`update-check: true` tells the console, and anybody with `paintball.update`, when
a newer Paintball is on SpigotMC. Set it to `false` to turn that off.

## Permissions

One node per command, so you can hand out exactly what somebody needs (a
referee who can start and stop matches but not touch arenas, say).
`paintball.play` and `paintball.admin` are only shorthand: each grants every
node listed under it, and nothing checks them directly, so a setup that grants
either one keeps working. `/pb leave` needs no permission at all, so nobody
can be stuck in a match.

| Permission | Default | |
|---|---|---|
| `paintball.play` | everyone | All three below |
| `paintball.join` | everyone | `/pb join` |
| `paintball.team` | everyone | `/pb team`, and the red and blue items in the lobby |
| `paintball.scoreboard` | everyone | `/pb scoreboard`, F in a match or its lobby, and the lobby's scoreboard item |
| `paintball.admin` | op | Every node below |
| `paintball.start` | op | `/pb start`; also changes the lobby's "waiting for an op" hint to "start whenever you like" |
| `paintball.stop` | op | `/pb stop` |
| `paintball.reload` | op | `/pb reload` |
| `paintball.as` | op | `/pb as <player> ...`; what they run is checked against *their* permissions |
| `paintball.arena` | op | Arena setup: `/pb arena list`, `create`, `setwaiting`, `addspawn`, `clearspawns`, `wand` (and using the wand), `setbounds`, `enable`, `disable`, `tp`, `name` |
| `paintball.arena.generate` | op | `/pb arena generate`, which replaces every block in a 31 × 14 × 57 box (two below your feet to eleven above) and builds a glass waiting box over the middle, up to 17 above |
| `paintball.arena.delete` | op | `/pb arena delete`, which also ends that arena's match and sends its players home |
| `paintball.bypass.commands` | op | Run any command during a match. Without it a player in a match may only use `/pb`, `/msg`, `/tell`, `/w`, `/r`, `/reply`, `/me` and `/list` |
| `paintball.update` | op | Told when a newer version is out (not part of `paintball.admin`) |

`/pb help` and tab-completion only offer what you may run.

## Everything you see is RP Engine content

The jar carries a content folder (`src/main/resources/content/paintball/`) and
installs it into `plugins/RPEngine/content/paintball/` on enable. Nothing on
screen is drawn by this plugin:

- **The HUD** is `huds/hud.json`, a full-screen shader object drawn in
  [ResourcePack AI Studio](https://resourcepack.ai), and it is the whole screen:
  there is not a title in the game. The score strip (top centre), a
  Valorant-style kill feed (top left), the weapon cluster (bottom right: the
  grenade card filling as it recharges, the ammo panel and its bar), the
  crosshair and hit marker, and the middle of the screen, which carries every
  moment of a match: the countdown ring, GO!, the paint on a splatted player's
  screen and their respawn ring, SPLATTED and who, and the result on its band
  with the score. The plugin only sets its values (`pb_red`, `pb_weapon`,
  `pb_center`..., see `game/Hud.java`); the pictures in them are
  `tools/hud_art.py`'s, which is why a word drawn in one (VICTORY, GO!) is art
  rather than config.
- **The scoreboard** is `dialogs/scoreboard.json`, a Studio dialog: both teams'
  tables with faces, kills, deaths and accuracy.
- **The marker** is a Studio model in five colourways (Ember for red, Tide for
  blue), each with an aimed twin (`marker_<skin>_aim`) whose holder sees
  `models/marker_sights.json` down the sights instead (RP Engine's `first-person:`).
- **The crosshair** is the HUD's own, a 2-pixel plus drawn over the game's 1-pixel
  one (the HUD is drawn after it), with spread ticks from the hip.
- **The mask, grenade and paintball** are Studio models too (`tools/src/`), fitted
  and coloured per team by `tools/make_assets.py`; the sounds are Studio's. The
  splats and the HUD's glyphs are drawn by the tools. Item names and lore are in
  `items/*.yml`.
- **Every word a player reads in chat, the player list and the HUD's text** is in
  `config.yml` under `messages:`.

`config.yml`'s `ui.hud` and `ui.scoreboard` point at other content ids to
replace either; a replacement has to print the same values.

## Building

```
./gradlew build        # build/libs/RPE-paintball-<version>.jar
```

Gradle 8.10.2 needs a JDK it can run on: 17 or 21 work, 24 does not. If your
default `java` is newer, `JAVA_HOME=/path/to/jdk-21 ./gradlew build`. The jar
itself targets Java 21 through a toolchain Gradle downloads on its own.

RP Engine is compile-only (the server already has it). The build uses, in order:

1. `ENGINE_JAR`, if set: the path to any RP Engine jar.
2. An engine built beside this project, in `../../rp-engine/build/libs`
   (`-PengineDir=<dir>` looks somewhere else). If several versions are there,
   the newest.
3. Otherwise RP Engine 0.2.3, the oldest version Paintball runs on, downloaded
   once from `https://cdn.resourcepack.ai/RPEngine-0.2.3.jar` into
   `.gradle/rp-engine/` and checked against its SHA-256. Versioned engine jars
   there are never rewritten.

`ai.resourcepack:common`, a small shared library (the update check), comes from
`https://repo.resourcepack.ai`, and is shaded into the jar and relocated.

### The art

`tools/` rebuilds the models, textures, glyphs, HUD art, sounds and item YAML in
the content folder from the sources in `tools/src/`. It needs Python 3 with
Pillow, NumPy and soundfile (`pip install pillow numpy soundfile`), and nothing
else: no network, no keys.

```
python tools/make_assets.py                                          # everything, deterministically
python tools/preview_display.py <model.json> <texture.png> out.png   # how an item is held
python tools/preview_fp.py <model.json> <texture.png> <context> out.png [background.png]   # first person, textured
```

The HUD and the scoreboard were made and exported in
[ResourcePack AI Studio](https://resourcepack.ai). `dialogs-src/` keeps what was
exported (`dialogs.json`, the scoreboard's Dialog-style document; `hud.json`,
the HUD's shader object), and the content folder's `dialogs/`, `huds/` and
`resourcepack/` are the export. To change either, edit the HUD or dialog in
Studio and export it again over those folders, or edit the exported JSON by hand.

## Support

Questions, bugs and suggestions: [resourcepack.ai/discord](https://resourcepack.ai/discord),
or an issue on this repository.

## Licence

GPL-3.0-or-later. See [`LICENSE`](LICENSE).

The jar bundles [Adventure](https://github.com/KyoriPowered/adventure) (MIT),
relocated so it cannot meet Paper's copy. The Spigot API and RP Engine are
compile-only and come from the server at runtime.
