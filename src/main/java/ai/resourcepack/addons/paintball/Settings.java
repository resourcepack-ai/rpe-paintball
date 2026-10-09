package ai.resourcepack.addons.paintball;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once into fields so the hot paths never touch YAML. */
public final class Settings {
    public final int matchSeconds;
    public final int killLimit;
    public final int freezeSeconds;
    public final int respawnTicks;
    public final int protectionTicks;
    public final int grenadesPerLife;
    public final int endSeconds;

    public final int fireIntervalTicks;
    public final int hopper;
    public final int reloadTicks;
    public final double speed;
    public final double gravity;
    public final double drag;
    public final double spread;
    public final double movingSpread;
    public final int lifetimeTicks;

    public final int fuseTicks;
    public final double throwSpeed;
    public final double grenadeRadius;

    public final int maxDecals;

    public final String hudId;
    public final String scoreboardId;

    public Settings(FileConfiguration c) {
        matchSeconds = Math.max(20, c.getInt("match-seconds", 180));
        killLimit = Math.max(0, c.getInt("kill-limit", 0));
        freezeSeconds = Math.max(1, c.getInt("freeze-seconds", 3));
        respawnTicks = (int) Math.round(Math.max(0.5, c.getDouble("respawn-seconds", 3)) * 20);
        protectionTicks = (int) Math.round(Math.max(0, c.getDouble("spawn-protection-seconds", 1.5)) * 20);
        grenadesPerLife = Math.max(0, c.getInt("grenades-per-life", 1));
        endSeconds = Math.max(3, c.getInt("end-seconds", 12));

        fireIntervalTicks = Math.max(1, c.getInt("marker.fire-interval-ticks", 3));
        hopper = Math.max(1, c.getInt("marker.hopper", 40));
        reloadTicks = (int) Math.round(Math.max(0.2, c.getDouble("marker.reload-seconds", 1.6)) * 20);
        speed = c.getDouble("marker.speed", 2.4);
        gravity = c.getDouble("marker.gravity", 0.022);
        drag = c.getDouble("marker.drag", 0.992);
        spread = c.getDouble("marker.spread-degrees", 0.7);
        movingSpread = c.getDouble("marker.moving-spread-degrees", 2.4);
        lifetimeTicks = Math.max(5, c.getInt("marker.lifetime-ticks", 60));

        fuseTicks = Math.max(5, c.getInt("grenade.fuse-ticks", 30));
        throwSpeed = c.getDouble("grenade.throw-speed", 1.1);
        grenadeRadius = c.getDouble("grenade.radius", 3.5);

        maxDecals = Math.max(50, c.getInt("paint.max-decals", 1500));

        hudId = c.getString("ui.hud", "paintball:hud").trim();
        scoreboardId = c.getString("ui.scoreboard", "paintball:scoreboard").trim();
    }
}
