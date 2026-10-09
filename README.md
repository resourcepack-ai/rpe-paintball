# Paintball

5v5 paintball on RP Engine. One mode: a hit splats you, you're back in three
seconds, and the team with more kills when the clock runs out wins.

Paper 1.21.11 or newer, with RP Engine 0.2.3 or newer.

## Playing

| | |
|---|---|
| `/pb join` | Into the lobby (the waiting box over the field) |
| `/pb start` | Op: starts the match now, even if you're alone (it joins you first) |
| `/pb stop` | Op: ends the match (scoreboard, then back to the lobby); in the lobby, sends everybody home |
| `/pb leave` | Out, with your inventory and position back |
| **F** or `/pb scoreboard` | The match scoreboard |
| **Tab** | The live player list: score and clock on top, each player's kills / deaths |

In a match: hold right-click to fire, left-click to aim down the sights (zoomed,
slower, no spread), Q reloads (it also reloads itself when empty), right-click the
grenade to throw it. Every ball is fired so that, drop and all, it lands exactly on
the crosshair; from the hip it strays by the spread the crosshair's ticks show.

## Everything you see is RP Engine content

The jar carries a content folder (`src/main/resources/content/paintball/`) and
installs it into `plugins/RPEngine/content/paintball/` on enable. Nothing on
screen is drawn by this plugin:

- **The HUD** is `huds/hud.json`, a full-screen shader object drawn in
  ResourcePack AI Studio: the score strip (top centre), a Valorant-style kill
  feed (top left: the killer's and victim's colours, the weapon), the marker's
  ammo (bottom right), a hit marker and the respawn countdown. The plugin only
  sets its values (`pb_red`, `pb_time`, `pb_f1`..., see `game/Hud.java`).
- **The scoreboard** is `dialogs/scoreboard.json`, a Studio dialog: both teams'
  tables with faces, kills, deaths and accuracy.
- **The marker** is Studio's Tactical model in five colourways (Ember for red,
  Tide for blue), each with an aimed twin (`marker_<skin>_aim`) whose holder sees
  `models/marker_sights.json` down the sights instead (RP Engine's `first-person:`).
- **The crosshair** is the HUD's own, a 2-pixel plus drawn over the game's 1-pixel
  one (the HUD is drawn after it), with spread ticks from the hip.
- **The mask, balls, splats, grenade, HUD glyphs and sounds** are ordinary items,
  icons and sounds. Item names and lore are in `items/*.yml`.
- **Every word a player reads** is in `config.yml` under `messages:`.

`config.yml`'s `ui.hud` and `ui.scoreboard` point at other content ids to
replace either; a replacement has to print the same values.

## Arenas (op)

`/pb arena generate <id> confirm [name]` builds a speedball field where you stand and
sets it up. From the console: `/pb arena generate <id> at <world> <x> <y> <z> [name]`.
By hand: `create`, `setwaiting`, `addspawn <red|blue>`, `wand` + `setbounds`, `enable`.

Numbers (match length, kill limit, respawn time, fire rate, ball speed...) are in
`plugins/RPEPaintball/config.yml`; `/pb reload` re-reads it.

## Building

```
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./gradlew build   # build/libs/RPE-paintball-<version>.jar
npm run deploy -- addon paintball --server-only                 # from the repo root: the test server only
```

The engine has to be built first (`rp-engine/build/libs`). The art:

```
python tools/make_assets.py                 # models, textures, glyphs, sounds, item YAML
python tools/preview_display.py <model.json> <texture.png> out.png   # how an item is held
```

The HUD and the scoreboard come out of Studio. `dialogs-src/` holds what was
exported (`dialogs.json`, the scoreboard's Dialog-style spec; `hud.json`, the
shader object); to rebuild the content folder's `dialogs/`, `huds/` and
`resourcepack/` from them, run in `studio/`:

```
node scripts/export-dialog-content.mjs ../rpe-addons/paintball/dialogs-src/dialogs.json <out> \
  --namespace paintball --shaders <shaders.json>
```

where `shaders.json` is `[{"id": "hud", "shader": <dialogs-src/hud.json>}]`, then copy
`<out>/dialogs`, `<out>/huds` and `<out>/resourcepack` into the content folder.
