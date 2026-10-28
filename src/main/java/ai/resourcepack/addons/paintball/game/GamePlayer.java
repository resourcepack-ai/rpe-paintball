package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.stats.StatLine;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/** One player's part in the current match. */
public final class GamePlayer {
    final UUID id;
    final String name;
    Team team;

    /** The team they asked for in the lobby, or null for wherever they are needed; kept from match to match. */
    Team pick;
    /** When they last picked, so whoever picked a crowded side last is the one moved. */
    long pickedAt;

    boolean alive;
    int ammo;
    /** Ticks of reloading still to go; 0 when the hopper is not being filled. */
    int reloading;
    long nextShot;
    /** The tick the grenade is back; until then it is recharging. */
    long grenadeReadyAt;
    long protectedUntil;
    /** The tick they come back, or 0 while they are in. */
    long respawnAt;
    /** Who splatted them last, and their team, for the HUD while they are out. */
    String splattedBy;
    Team splattedByTeam;
    /** When they were splatted, and which of the screen splats they got. */
    long splattedAt;
    int splat = 1;
    /** Who they splatted last, their team, and when, for the HUD's moment of glory. */
    String lastKill;
    Team lastKillTeam;
    long lastKillAt;
    /** The hit marker is up until this tick. */
    long hitUntil;
    /** Down the sights: zoomed, slower, and every ball goes exactly where the crosshair is. */
    boolean aiming;
    /** When aiming last changed, so one click is one toggle. */
    long aimChangedAt;
    /** The last place they stood inside the arena, for putting them back when they stray. */
    Location lastInside;
    /** While they are out: the camera they watch their killer through, whose it is, where round it started, and when. See {@link KillCam}. */
    org.bukkit.entity.ItemDisplay camera;
    UUID camOn;
    double camAngle;
    long camFrom;

    /** This match's counters: kills, deaths, shots, hits. */
    final StatLine stats = new StatLine();
    boolean left;

    GamePlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Team team() {
        return team;
    }

    public StatLine stats() {
        return stats;
    }

    public Player player() {
        return left ? null : Bukkit.getPlayer(id);
    }

    void reset() {
        stats.kills = 0;
        stats.deaths = 0;
        stats.shots = 0;
        stats.hits = 0;
        respawnAt = 0;
        protectedUntil = 0;
        reloading = 0;
        splattedBy = null;
        splattedByTeam = null;
        splattedAt = 0;
        lastKill = null;
        lastKillTeam = null;
        lastKillAt = 0;
        hitUntil = 0;
        aiming = false;
        grenadeReadyAt = 0;
    }

    /** Valorant orders a team by combat score; here that is kills, then fewer deaths, then hits. */
    long rank() {
        return stats.kills * 100_000L - stats.deaths * 100L + stats.hits;
    }
}
