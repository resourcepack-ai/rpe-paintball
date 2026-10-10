package ai.resourcepack.addons.paintball;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once into fields so the hot paths never touch YAML. */
public final class Settings {
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

        hudId = c.getString("ui.hud", "paintball:hud").trim();
        scoreboardId = c.getString("ui.scoreboard", "paintball:scoreboard").trim();
    }
}
