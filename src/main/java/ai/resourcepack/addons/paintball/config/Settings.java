package ai.resourcepack.addons.paintball.config;

import ai.resourcepack.addons.paintball.team.Skin;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * config.yml, read once into fields so the hot paths never touch YAML.
 *
 * <p>An arena can set any of the game's options for itself ({@code settings:}
 * under it in arenas.yml, or {@code /pb arena set}); {@link #layered} reads
 * those over config.yml, so an option the arena leaves alone is the server's.
 */
public final class Settings {
    /** Top-level sections and options that are the server's, not an arena's. */
    private static final List<String> NOT_PER_ARENA = List.of("ui", "messages", "update-check");

    public final int matchSeconds;
    public final int killLimit;
    public final int freezeSeconds;
    public final int respawnTicks;
    public final int protectionTicks;
    public final int grenadeCooldownTicks;
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

    public final int maxDecals;

    /** Each team's marker. */
    public final Skin redSkin;
    public final Skin blueSkin;

    public final int feedTicks;
    public final int killBannerTicks;
    public final int hitMarkerTicks;
    /** Seconds left on the clock that sound a warning and show it. */
    public final java.util.Set<Integer> timeWarnings;

    public final String hudId;
    public final String scoreboardId;

    public Settings(FileConfiguration c) {
        matchSeconds = Math.max(20, c.getInt("match-seconds", 180));
        killLimit = Math.max(0, c.getInt("kill-limit", 0));
        freezeSeconds = Math.max(1, c.getInt("freeze-seconds", 3));
        respawnTicks = (int) Math.round(Math.max(0.5, c.getDouble("respawn-seconds", 3)) * 20);
        protectionTicks = (int) Math.round(Math.max(0, c.getDouble("spawn-protection-seconds", 1.5)) * 20);
        grenadeCooldownTicks = (int) Math.round(Math.max(0, c.getDouble("grenade.cooldown-seconds", 15)) * 20);
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

        maxDecals = Math.max(50, c.getInt("paint.max-decals", 1500));

        redSkin = Skin.parse(c.getString("teams.red.marker", "ember"));
        blueSkin = Skin.parse(c.getString("teams.blue.marker", "tide"));

        feedTicks = (int) Math.round(Math.max(1, c.getDouble("hud.kill-feed-seconds", 8)) * 20);
        killBannerTicks = (int) Math.round(Math.max(0, c.getDouble("hud.kill-banner-seconds", 2.5)) * 20);
        hitMarkerTicks = Math.max(0, c.getInt("hud.hit-marker-ticks", 6));
        timeWarnings = java.util.Set.copyOf(c.getIntegerList("hud.time-warnings"));

        hudId = c.getString("ui.hud", "paintball:hud").trim();
        scoreboardId = c.getString("ui.scoreboard", "paintball:scoreboard").trim();
    }

    /** The settings of an arena: its own options, then config.yml's for the rest. */
    public static Settings layered(FileConfiguration global, Map<String, Object> own) {
        // Copied rather than set as defaults: every read above names its own
        // fallback, and a fallback named in the call wins over a defaults
        // section, so an option the arena left alone would be the jar's rather
        // than the server's.
        YamlConfiguration c = new YamlConfiguration();
        java.util.Set<String> paths = new java.util.LinkedHashSet<>(global.getKeys(true));
        if (global.getDefaults() != null) paths.addAll(global.getDefaults().getKeys(true));
        for (String path : paths) {
            if (path.startsWith("messages") || global.isConfigurationSection(path)) continue;
            c.set(path, global.get(path));
        }
        for (Map.Entry<String, Object> e : own.entrySet()) c.set(e.getKey(), e.getValue());
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
        return out;
    }

    /**
     * {@code text} as a value for an option shaped like {@code example} (its
     * value in config.yml): a whole number, a number, true/false, a list of
     * whole numbers split by commas, or a word.
     *
     * @throws IllegalArgumentException when it is not one
     */
    public static Object parse(Object example, String text) {
        String t = text.trim();
        try {
            if (example instanceof Integer || example instanceof Long) return Integer.parseInt(t);
            if (example instanceof Number) return Double.parseDouble(t);
            if (example instanceof List<?>) {
                List<Integer> out = new ArrayList<>();
                for (String part : t.replace("[", "").replace("]", "").split("[,\\s]+")) {
                    if (!part.isEmpty()) out.add(Integer.parseInt(part));
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
}
