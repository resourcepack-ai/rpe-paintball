package ai.resourcepack.addons.paintball.config;

import ai.resourcepack.addons.paintball.game.Mode;
import ai.resourcepack.addons.paintball.team.Paint;
import ai.resourcepack.addons.paintball.team.Skin;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml, read once into fields so the hot paths never touch YAML.
 *
 * <p>What a match plays by is four layers, each over the one before:
 * config.yml; config.yml's {@code modes.<mode>} for the mode being played; the
 * arena's own settings ({@code settings:} under it in arenas.yml, or
 * {@code /pb arena set}); and the arena's own {@code modes.<mode>}.
 * {@link #layered} stacks them, so an option nobody changed is the server's.
 */
public final class Settings {
    /** Top-level sections and options that are the server's, not an arena's. */
    private static final List<String> NOT_PER_ARENA = List.of("ui", "messages", "update-check");

    /** What an arena plays when nothing says otherwise. */
    public final Mode mode;

    public final int matchSeconds;
    public final int killLimit;
    public final int freezeSeconds;
    public final int respawnTicks;
    /** While a player is out, the camera circles whoever splatted them. */
    public final boolean killerCamera;
    public final int protectionTicks;
    /** The safe zone round the spawn a player came in at: its size in blocks (0 for none), and whether it is a square. */
    public final double protectionRadius;
    public final boolean protectionSquare;
    public final int grenadeCooldownTicks;
    /** Whether a respawn brings the grenade straight back. */
    public final boolean grenadeResetOnRespawn;
    public final int endSeconds;

    public final int fireIntervalTicks;
    public final int hopper;
    public final int reloadTicks;
    public final double speed;
    public final double gravity;
    public final double drag;
    public final double spread;
    public final double movingSpread;
    public final double aimSpread;
    public final double aimSlowdown;
    public final double range;
    public final int lifetimeTicks;

    public final int fuseTicks;
    public final double throwSpeed;
    public final double grenadeRadius;

    public final int minesPerMatch;
    public final int mineArmTicks;
    public final double mineRadius;

    public final int maxDecals;

    /** Each team's marker. */
    public final Skin redSkin;
    public final Skin blueSkin;

    /** Free for all: the colours handed out, in order, and the marker each one carries. */
    public final List<Paint> ffaColours;
    /** Whether players choose their colour out of {@link #ffaColours}. */
    public final boolean colourPick;

    /** Computer players: each side (or a free for all) filled up to this many, and the sides evened. */
    public final int botsFill;
    public final boolean botsEvenTeams;
    public final double botSpread;
    public final int botReactionTicks;
    public final List<String> botNames;
    public final Map<Paint, Skin> ffaMarkers;

    /** Capture the flag. See config.yml's {@code ctf:}. */
    public final int captures;
    public final int flagReturnTicks;
    public final double carrierSpeed;
    public final boolean needOwnFlag;
    public final boolean carrierGlows;
    public final boolean carrierThrows;
    public final double flagReach;

    public final int feedTicks;
    public final int killBannerTicks;
    public final int hitMarkerTicks;
    /** Names over heads drawn as text the world hides, instead of the game's tags; whether enemies' show; how they read. */
    public final boolean nametagsBehindBlocks;
    public final boolean nametagsEnemies;
    public final String nametagFormat;
    /** Seconds left on the clock that sound a warning and show it. */
    public final java.util.Set<Integer> timeWarnings;

    public final String hudId;
    public final String ffaHudId;
    public final String scoreboardId;
    public final String ffaScoreboardId;
    public final String ctfScoreboardId;

    public Settings(FileConfiguration c) {
        Mode m = Mode.parse(c.getString("mode", "teams"));
        mode = m == null ? Mode.TEAMS : m;

        matchSeconds = Math.max(20, c.getInt("match-seconds", 180));
        killLimit = Math.max(0, c.getInt("kill-limit", 0));
        freezeSeconds = Math.max(1, c.getInt("freeze-seconds", 3));
        respawnTicks = (int) Math.round(Math.max(0.5, c.getDouble("respawn-seconds", 3)) * 20);
        killerCamera = c.getBoolean("killer-camera", true);
        protectionTicks = (int) Math.round(Math.max(0, c.getDouble("spawn-protection-seconds", 1.5)) * 20);
        protectionRadius = Math.max(0, c.getDouble("spawn-protection-radius", 0));
        protectionSquare = "square".equalsIgnoreCase(c.getString("spawn-protection-shape", "circle").trim());
        grenadeCooldownTicks = (int) Math.round(Math.max(0, c.getDouble("grenade.cooldown-seconds", 15)) * 20);
        grenadeResetOnRespawn = c.getBoolean("grenade.reset-on-respawn", false);
        endSeconds = Math.max(3, c.getInt("end-seconds", 12));

        fireIntervalTicks = Math.max(1, c.getInt("marker.fire-interval-ticks", 3));
        hopper = Math.max(1, c.getInt("marker.hopper", 40));
        reloadTicks = (int) Math.round(Math.max(0.2, c.getDouble("marker.reload-seconds", 1.6)) * 20);
        speed = c.getDouble("marker.speed", 2.4);
        gravity = c.getDouble("marker.gravity", 0.022);
        drag = c.getDouble("marker.drag", 0.992);
        spread = c.getDouble("marker.spread-degrees", 0.7);
        movingSpread = c.getDouble("marker.moving-spread-degrees", 2.4);
        aimSpread = Math.max(0, c.getDouble("marker.aim-spread-degrees", 0.0));
        aimSlowdown = Math.max(0, Math.min(0.9, c.getDouble("marker.aim-slowdown", 0.55)));
        range = Math.max(8, c.getDouble("marker.range", 96));
        lifetimeTicks = Math.max(5, c.getInt("marker.lifetime-ticks", 60));

        fuseTicks = Math.max(5, c.getInt("grenade.fuse-ticks", 30));
        throwSpeed = c.getDouble("grenade.throw-speed", 1.1);
        grenadeRadius = c.getDouble("grenade.radius", 3.5);

        minesPerMatch = Math.max(0, c.getInt("mine.per-match", 1));
        mineArmTicks = (int) Math.round(Math.max(0, c.getDouble("mine.arm-seconds", 1.5)) * 20);
        mineRadius = Math.max(0.5, c.getDouble("mine.radius", 3.0));

        maxDecals = Math.max(50, c.getInt("paint.max-decals", 1500));

        redSkin = Skin.parse(c.getString("teams.red.marker", "ember"));
        blueSkin = Skin.parse(c.getString("teams.blue.marker", "tide"));

        List<Paint> colours = new ArrayList<>();
        for (String s : c.getStringList("ffa.colours")) {
            Paint p = Paint.parse(s);
            if (p != null && !colours.contains(p)) colours.add(p);
        }
        ffaColours = colours.isEmpty() ? List.of(Paint.values()) : List.copyOf(colours);
        colourPick = c.getBoolean("colour-pick", true);

        botsFill = Math.max(0, Math.min(16, c.getInt("bots.fill", 0)));
        botsEvenTeams = c.getBoolean("bots.even-teams", false);
        botSpread = Math.max(0, c.getDouble("bots.spread-degrees", 4.0));
        botReactionTicks = Math.max(0, c.getInt("bots.reaction-ticks", 12));
        List<String> names = new ArrayList<>();
        for (String n : c.getStringList("bots.names")) if (n != null && !n.isBlank()) names.add(n.trim());
        botNames = List.copyOf(names);
        Map<Paint, Skin> markers = new EnumMap<>(Paint.class);
        for (Paint p : Paint.values()) {
            String skin = c.getString("ffa.markers." + p.id());
            markers.put(p, skin == null ? p.skin() : Skin.parse(skin));
        }
        ffaMarkers = markers;

        captures = Math.max(1, c.getInt("ctf.captures", 3));
        flagReturnTicks = (int) Math.round(Math.max(0, c.getDouble("ctf.flag-return-seconds", 20)) * 20);
        carrierSpeed = Math.max(0.2, Math.min(1.5, c.getDouble("ctf.carrier-speed", 0.85)));
        needOwnFlag = c.getBoolean("ctf.need-own-flag", true);
        carrierGlows = c.getBoolean("ctf.carrier-glows", true);
        carrierThrows = c.getBoolean("ctf.carrier-can-throw", false);
        flagReach = Math.max(0.5, c.getDouble("ctf.flag-reach", 1.3));

        feedTicks = (int) Math.round(Math.max(1, c.getDouble("hud.kill-feed-seconds", 8)) * 20);
        killBannerTicks = (int) Math.round(Math.max(0, c.getDouble("hud.kill-banner-seconds", 2.5)) * 20);
        hitMarkerTicks = Math.max(0, c.getInt("hud.hit-marker-ticks", 6));
        nametagsBehindBlocks = c.getBoolean("nametags.hide-behind-blocks", false);
        nametagsEnemies = c.getBoolean("nametags.enemies", false);
        nametagFormat = c.getString("nametags.format", "<{team_colour}>{player}");
        timeWarnings = java.util.Set.copyOf(c.getIntegerList("hud.time-warnings"));

        hudId = c.getString("ui.hud", "paintball:hud").trim();
        ffaHudId = c.getString("ui.hud-ffa", "paintball:hud_ffa").trim();
        scoreboardId = c.getString("ui.scoreboard", "paintball:scoreboard").trim();
        ffaScoreboardId = c.getString("ui.scoreboard-ffa", "paintball:scoreboard_ffa").trim();
        ctfScoreboardId = c.getString("ui.scoreboard-ctf", "paintball:scoreboard_ctf").trim();
    }

    /** The HUD a match of this mode draws. */
    public String hud(Mode m) {
        return m == Mode.FFA ? ffaHudId : hudId;
    }

    /** The scoreboard dialog a match of this mode opens. */
    public String scoreboard(Mode m) {
        return switch (m) {
            case FFA -> ffaScoreboardId;
            case CTF -> ctfScoreboardId;
            default -> scoreboardId;
        };
    }

    /**
     * What a match of {@code mode} on an arena plays by: config.yml, its
     * {@code modes.<mode>}, then the arena's own settings and the arena's own
     * {@code modes.<mode>}, each over the one before.
     */
    public static Settings layered(FileConfiguration global, Map<String, Object> own, Mode mode) {
        // Copied rather than set as defaults: every read above names its own
        // fallback, and a fallback named in the call wins over a defaults
        // section, so an option nobody changed would be the jar's rather
        // than the server's.
        YamlConfiguration c = new YamlConfiguration();
        java.util.Set<String> paths = new java.util.LinkedHashSet<>(global.getKeys(true));
        if (global.getDefaults() != null) paths.addAll(global.getDefaults().getKeys(true));
        for (String path : paths) {
            if (path.startsWith("messages") || global.isConfigurationSection(path)) continue;
            c.set(path, global.get(path));
        }
        String prefix = "modes." + mode.id() + ".";
        for (String path : paths) {
            if (path.startsWith(prefix) && !global.isConfigurationSection(path)) {
                c.set(path.substring(prefix.length()), global.get(path));
            }
        }
        for (Map.Entry<String, Object> e : own.entrySet()) {
            if (!e.getKey().startsWith("modes.")) c.set(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Object> e : own.entrySet()) {
            if (e.getKey().startsWith(prefix)) c.set(e.getKey().substring(prefix.length()), e.getValue());
        }
        c.set("mode", mode.id());
        return new Settings(c);
    }

    /** Every option an arena may set for itself, by path, in config.yml's order. */
    public static List<String> arenaOptions(Configuration config) {
        Configuration known = config.getDefaults() != null ? config.getDefaults() : config;
        List<String> out = new ArrayList<>();
        for (String path : known.getKeys(true)) {
            if (known.isConfigurationSection(path) || NOT_PER_ARENA.contains(path.split("\\.", 2)[0])) continue;
            out.add(path);
        }
        // An arena can change a mode's own value without config.yml having one for it.
        for (Mode m : Mode.values()) {
            for (String path : new ArrayList<>(out)) {
                if (path.startsWith("modes.") || path.equals("mode")) continue;
                String per = "modes." + m.id() + "." + path;
                if (!out.contains(per)) out.add(per);
            }
        }
        return out;
    }

    /** The value config.yml has for an option, for {@link #parse}; a mode's own options fall back to the plain one. */
    public static Object example(Configuration config, String path) {
        Object v = config.get(path);
        if (v == null && path.startsWith("modes.")) {
            String[] parts = path.split("\\.", 3);
            if (parts.length == 3) v = config.get(parts[2]);
        }
        return v;
    }

    /**
     * {@code text} as a value for an option shaped like {@code example} (its
     * value in config.yml): a whole number, a number, true/false, a list split
     * by commas (of whole numbers or of words), or a word. The game mode and
     * paint colours are checked by name.
     *
     * @throws IllegalArgumentException when it is not one
     */
    public static Object parse(String path, Object example, String text) {
        String t = text.trim();
        if (path.equals("mode")) {
            Mode m = Mode.parse(t);
            if (m == null) throw new IllegalArgumentException("teams, ffa or ctf, not " + t);
            return m.id();
        }
        if (path.endsWith(".marker") || path.startsWith("ffa.markers.")) {
            for (Skin s : Skin.values()) if (s.name().equalsIgnoreCase(t)) return s.name().toLowerCase(Locale.ROOT);
            throw new IllegalArgumentException("a marker (" + names(Skin.values()) + "), not " + t);
        }
        try {
            if (example instanceof Integer || example instanceof Long) return Integer.parseInt(t);
            if (example instanceof Number) return Double.parseDouble(t);
            if (example instanceof List<?> list) {
                boolean words = !list.isEmpty() && list.get(0) instanceof String;
                List<Object> out = new ArrayList<>();
                for (String part : t.replace("[", "").replace("]", "").split("[,\\s]+")) {
                    if (part.isEmpty()) continue;
                    if (!words) {
                        out.add(Integer.parseInt(part));
                    } else if (path.equals("ffa.colours")) {
                        Paint p = Paint.parse(part);
                        if (p == null) throw new IllegalArgumentException("a colour (" + names(Paint.values()) + "), not " + part);
                        out.add(p.id());
                    } else {
                        out.add(part);
                    }
                }
                return out;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + t);
        }
        if (example instanceof Boolean) {
            if (t.equalsIgnoreCase("true") || t.equalsIgnoreCase("false")) return Boolean.parseBoolean(t.toLowerCase(Locale.ROOT));
            throw new IllegalArgumentException("true or false, not " + t);
        }
        return t;
    }

    private static String names(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> v : values) out.add(v.name().toLowerCase(Locale.ROOT));
        return String.join(", ", out);
    }
}
