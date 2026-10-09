package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.Settings;
import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import ai.resourcepack.addons.paintball.arena.Arena;
import ai.resourcepack.addons.paintball.paint.PaintLayer;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The match on one arena. Players join and wait in the lobby; an op starts it;
 * a hit splats you and you are back in a few seconds; when the clock runs out
 * (or a team reaches the kill limit) the team with more kills wins, everybody
 * sees the scoreboard, and it is back to the lobby for another.
 *
 * <pre>WAITING -> FREEZE -> LIVE -> ENDED -> WAITING</pre>
 *
 * Everything runs on the main thread, ticked by {@link GameManager}.
 */
public final class Game {
    public enum Phase { WAITING, FREEZE, LIVE, ENDED }

    private static NamespacedKey FREEZE_KEY;
    private static NamespacedKey AIM_KEY;

    final PaintballPlugin plugin;
    final Arena arena;
    final Content content;
    final Fx fx;
    final Map<UUID, GamePlayer> players = new LinkedHashMap<>();
    final PaintLayer paint;
    final Combat combat;
    final Hud hud;
    private final GameManager manager;

    Phase phase = Phase.WAITING;
    /** Ticks left in FREEZE or ENDED. */
    int timer;
    /** Ticks left on the match clock while LIVE. */
    int clock;
    long now;
    final EnumMap<Team, Integer> kills = new EnumMap<>(Team.class);
    /** The kill feed, newest first. */
    final Deque<Kill> feed = new ArrayDeque<>();

    /** One line of the kill feed. */
    record Kill(String killer, Team killerTeam, String victim, Team victimTeam, Weapon weapon, long at) {
    }
    Team winner;
    boolean played;

    Game(PaintballPlugin plugin, GameManager manager, Arena arena) {
        this.plugin = plugin;
        this.manager = manager;
        this.arena = arena;
        this.content = plugin.content();
        this.fx = new Fx(content);
        this.paint = new PaintLayer(content, plugin.settings().maxDecals);
        this.combat = new Combat(this, content, fx);
        this.hud = new Hud(this);
        if (FREEZE_KEY == null) FREEZE_KEY = new NamespacedKey(plugin, "freeze");
        if (AIM_KEY == null) AIM_KEY = new NamespacedKey(plugin, "aim");
        for (Team t : Team.values()) kills.put(t, 0);
    }

    Settings settings() {
        return plugin.settings();
    }

    ai.resourcepack.addons.paintball.Messages messages() {
        return plugin.messages();
    }

    public Arena arena() {
        return arena;
    }

    public Phase phase() {
        return phase;
    }

    public List<GamePlayer> active() {
        List<GamePlayer> out = new ArrayList<>();
        for (GamePlayer gp : players.values()) if (!gp.left) out.add(gp);
        return out;
    }

    public int size() {
        return active().size();
    }

    public GamePlayer get(UUID id) {
        return players.get(id);
    }

    /** A team's players, best first. */
    public List<GamePlayer> roster(Team t) {
        List<GamePlayer> out = new ArrayList<>();
        for (GamePlayer gp : players.values()) if (gp.team == t) out.add(gp);
        out.sort(Comparator.comparingLong(GamePlayer::rank).reversed());
        return out;
    }

    public int kills(Team t) {
        return kills.get(t);
    }

    public boolean live() {
        return phase == Phase.FREEZE || phase == Phase.LIVE;
    }

    public Team winner() {
        return winner;
    }

    public boolean played() {
        return played;
    }

    // ---- joining and leaving -------------------------------------------------------

    void join(Player p) {
        GamePlayer gp = new GamePlayer(p.getUniqueId(), p.getName());
        players.put(p.getUniqueId(), gp);
        plugin.snapshots().take(p);
        PlayerSnapshot.reset(p);
        hud.join(p);
        if (live()) {
            // Mid-match: straight onto the smaller team.
            gp.team = smallerTeam();
            hud.refreshTeams();
            spawn(gp, p, true);
            broadcast(messages().component("joined-team", "player", p.getName(),
                    "colour", gp.team.color().asHexString(), "team", gp.team.display()));
        } else {
            toLobby(p);
            broadcast(messages().component("joined-lobby", "player", p.getName(), "count", String.valueOf(size())));
            p.sendMessage(messages().component(p.hasPermission("paintball.admin") ? "start-hint-op" : "start-hint"));
        }
    }

    void leave(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null) return;
        if (live()) gp.left = true;
        else players.remove(p.getUniqueId());
        aim(gp, p, false);
        unfreeze(p);
        hud.leave(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        plugin.snapshots().restore(p);
        broadcast(messages().component("left", "player", p.getName()));
        if (size() == 0) {
            close();
            return;
        }
        hud.refreshTeams();
    }

    private Team smallerTeam() {
        int red = 0;
        int blue = 0;
        for (GamePlayer gp : active()) {
            if (gp.team == Team.RED) red++;
            else if (gp.team == Team.BLUE) blue++;
        }
        return red <= blue ? Team.RED : Team.BLUE;
    }

    private void toLobby(Player p) {
        unfreeze(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(arena.waiting());
        PlayerSnapshot.reset(p);
        Loadout.lobby(content, messages(), p);
    }

    // ---- the clock -----------------------------------------------------------------

    void tick() {
        now++;
        switch (phase) {
            case WAITING -> {
            }
            case FREEZE -> {
                timer--;
                if (timer > 0 && timer % 20 == 0) {
                    int n = timer / 20;
                    for (Player p : online()) {
                        fx.title(p, messages().component("title-countdown", "seconds", String.valueOf(n)), Component.empty(), 0, 18, 2);
                        fx.tick(p, n);
                    }
                }
                if (timer <= 0) goLive();
            }
            case LIVE -> tickLive();
            case ENDED -> {
                timer--;
                if (timer == settings().endSeconds * 20 - 30) {
                    for (Player p : online()) plugin.scoreboard().open(p, this);
                }
                if (timer % 20 == 0 && timer > settings().endSeconds * 20 - 120 && winner != null) {
                    List<GamePlayer> team = roster(winner);
                    if (!team.isEmpty()) {
                        Player w = team.get((int) (now / 20 % team.size())).player();
                        if (w != null) fx.firework(w.getLocation().add(0, 1, 0), winner);
                    }
                }
                if (timer <= 0) backToLobby();
            }
        }
        hud.tick();
    }

    private void tickLive() {
        clock--;
        combat.tick();
        if (phase != Phase.LIVE) return;
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            if (gp.alive) {
                combat.trigger(gp, p, now);
                keepInside(gp, p);
            } else if (gp.respawnAt > 0 && now >= gp.respawnAt) {
                spawn(gp, p, false);
            }
        }
        if (clock <= 0) end();
    }

    @SuppressWarnings("deprecation") // isOnGround is the client's word for it; good enough for "where they last stood"
    private void keepInside(GamePlayer gp, Player p) {
        Location at = p.getLocation();
        if (arena.contains(at)) {
            if (now % 10 == 0 && p.isOnGround()) gp.lastInside = at;
            return;
        }
        p.teleport(gp.lastInside != null ? gp.lastInside : spawnFor(gp));
    }

    // ---- starting ----------------------------------------------------------------

    /** Deals the teams and starts the countdown. Works with one player: they play against nobody. */
    public boolean start() {
        if (live() || size() == 0) return false;
        if (phase == Phase.ENDED) backToLobby();
        players.values().removeIf(gp -> gp.left);
        List<GamePlayer> list = new ArrayList<>(active());
        Collections.shuffle(list);
        for (int i = 0; i < list.size(); i++) {
            GamePlayer gp = list.get(i);
            gp.team = i % 2 == 0 ? Team.RED : Team.BLUE;
            gp.reset();
        }
        for (Team t : Team.values()) kills.put(t, 0);
        feed.clear();
        winner = null;
        paint.clear();
        combat.clear();
        played = true;
        phase = Phase.FREEZE;
        timer = settings().freezeSeconds * 20;
        clock = settings().matchSeconds * 20;
        hud.refreshTeams();
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            spawn(gp, p, true);
            freeze(p);
            fx.title(p, messages().component("brand"), messages().component("title-team",
                    "colour", gp.team.color().asHexString(), "team", gp.team.display().toUpperCase()), 5, 40, 10);
        }
        return true;
    }

    private void goLive() {
        phase = Phase.LIVE;
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            unfreeze(p);
            fx.title(p, messages().component("title-go"), Component.empty(), 0, 15, 8);
            fx.whistle(p);
        }
    }

    /** Puts a player at one of their team's spawns, loaded up. */
    private void spawn(GamePlayer gp, Player p, boolean first) {
        Settings s = settings();
        gp.alive = true;
        gp.respawnAt = 0;
        gp.ammo = s.hopper;
        gp.reloading = 0;
        gp.grenades = s.grenadesPerLife;
        gp.protectedUntil = first ? 0 : now + s.protectionTicks;
        aim(gp, p, false);
        Location at = first ? slotFor(gp) : spawnFor(gp);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(at);
        gp.lastInside = at;
        PlayerSnapshot.reset(p);
        Loadout.equip(content, p, gp);
        if (!first) fx.respawned(p);
    }

    /** At the start, everybody gets their own spawn point in turn. */
    private Location slotFor(GamePlayer gp) {
        List<Location> spawns = arena.spawns(gp.team);
        int i = roster(gp.team).indexOf(gp);
        Location at = spawns.get(Math.max(0, i) % spawns.size()).clone();
        if (i >= spawns.size()) at.add((i / spawns.size()) * 0.8, 0, 0);
        return at;
    }

    private Location spawnFor(GamePlayer gp) {
        List<Location> spawns = arena.spawns(gp.team);
        return spawns.get((int) (Math.random() * spawns.size()));
    }

    // ---- hits ----------------------------------------------------------------------

    /** {@code shooter} hit {@code victim}: one hit and they are splatted. */
    void hit(GamePlayer shooter, GamePlayer victim, Location at, Weapon weapon) {
        if (phase != Phase.LIVE || !victim.alive || victim.left || victim.team == shooter.team) return;
        if (now < victim.protectedUntil) {
            fx.protectedHit(at);
            return;
        }
        shooter.stats.hits++;
        shooter.stats.kills++;
        victim.stats.deaths++;
        victim.alive = false;
        victim.respawnAt = now + settings().respawnTicks;
        kills.merge(shooter.team, 1, Integer::sum);
        feed.addFirst(new Kill(shooter.name, shooter.team, victim.name, victim.team, weapon, now));
        while (feed.size() > 3) feed.removeLast();
        victim.splattedBy = shooter.name;
        shooter.lastKill = victim.name;
        shooter.lastKillAt = now;
        shooter.hitUntil = now + 6;

        Player vp = victim.player();
        Player sp = shooter.player();
        fx.screenSplat(vp, shooter.team);
        fx.hitConfirm(sp);
        if (vp != null) {
            // The paint on their screen is the title; who did it is on the HUD.
            aim(victim, vp, false);
            fx.knockedOut(vp);
            vp.getInventory().clear();
            vp.setGameMode(GameMode.SPECTATOR);
        }
        hud.refreshScores();
        int limit = settings().killLimit;
        if (limit > 0 && kills.get(shooter.team) >= limit) end();
    }

    // ---- the end ---------------------------------------------------------------------

    /** Time is up (or the limit was reached): more kills wins. */
    public void end() {
        if (!live()) return;
        combat.clear();
        int red = kills.get(Team.RED);
        int blue = kills.get(Team.BLUE);
        winner = red == blue ? null : red > blue ? Team.RED : Team.BLUE;
        phase = Phase.ENDED;
        timer = settings().endSeconds * 20;
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            aim(gp, p, false);
            unfreeze(p);
            if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            boolean won = winner != null && gp.team == winner;
            Component title = messages().component(winner == null ? "title-draw" : won ? "title-victory" : "title-defeat");
            fx.title(p, title, messages().component("title-score", "red", String.valueOf(red), "blue", String.valueOf(blue)), 5, 60, 15);
            fx.fanfare(p, won);
        }
        hud.refreshScores();
    }

    /** The scoreboard has been up long enough: everybody back to the lobby, paint off the walls. */
    private void backToLobby() {
        phase = Phase.WAITING;
        combat.clear();
        paint.clear();
        players.values().removeIf(gp -> gp.left);
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p != null) toLobby(p);
        }
        hud.refreshTeams();
    }

    /** Sends everybody home: {@code /pb stop} in the lobby, the last player leaving, or the plugin going down. */
    void close() {
        combat.clear();
        paint.clear();
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            unfreeze(p);
            hud.leave(p);
            if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
            manager.forget(p);
            plugin.snapshots().restore(p);
        }
        hud.close();
        manager.reset(arena);
    }

    /** {@code /pb stop}: mid-match it ends the match now; in the lobby it sends everybody home. */
    public void stop() {
        if (live()) {
            broadcast(messages().component("match-stopped"));
            end();
        } else {
            broadcast(messages().component("closed"));
            close();
        }
    }

    // ---- freezing ------------------------------------------------------------------

    private static void freeze(Player p) {
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null || inst.getModifier(FREEZE_KEY) != null) continue;
            inst.addTransientModifier(new AttributeModifier(FREEZE_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    static void unfreeze(Player p) {
        if (FREEZE_KEY == null) return;
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null && inst.getModifier(FREEZE_KEY) != null) inst.removeModifier(FREEZE_KEY);
        }
    }

    // ---- everybody ---------------------------------------------------------------------

    List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p != null) out.add(p);
        }
        return out;
    }

    void broadcast(Component c) {
        for (Player p : online()) p.sendMessage(c);
    }

    /** Throws a grenade for a player, if they have one. */
    public void throwGrenade(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null || phase != Phase.LIVE || !gp.alive || gp.grenades <= 0) return;
        gp.grenades--;
        combat.throwGrenade(gp, p);
        Loadout.grenades(content, p, gp);
    }

    /** A left click with the marker: up to the sights, or back down. */
    public void toggleAim(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null || phase != Phase.LIVE || !gp.alive || now - gp.aimChangedAt < 4) return;
        if (!gp.aiming && gp.reloading > 0) return;
        aim(gp, p, !gp.aiming);
    }

    /** Down from the sights, if they were up: a new weapon in hand, a sprint. */
    public void lowerAim(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp != null && gp.aiming) aim(gp, p, false);
    }

    /**
     * Up to the sights or back down. Aimed, the marker in the hotbar is its
     * aimed twin (the holder sees down the sights, everybody else the marker),
     * every ball goes exactly to the crosshair, and the player is slowed, which
     * is also the zoom: the game narrows the view of a player who walks slower.
     */
    void aim(GamePlayer gp, Player p, boolean on) {
        boolean changed = gp.aiming != on;
        gp.aiming = on;
        if (changed) gp.aimChangedAt = now;
        if (p == null) return;
        var inv = p.getInventory();
        int held = inv.getHeldItemSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (!Loadout.isMarker(content, inv.getItem(slot)) || gp.team == null) continue;
            boolean aimed = on && slot == held;
            if (Loadout.isAimed(content, inv.getItem(slot)) != aimed) {
                inv.setItem(slot, Loadout.marker(content, Loadout.skin(gp.team), aimed));
            }
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            if (speed.getModifier(AIM_KEY) != null) speed.removeModifier(AIM_KEY);
            if (on) {
                speed.addTransientModifier(new AttributeModifier(AIM_KEY, -settings().aimSlowdown,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
        if (changed) fx.aim(p, on);
    }

    public void reload(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null || phase != Phase.LIVE || !gp.alive) return;
        combat.reload(gp, p);
    }

    /** The match clock as m:ss: the full length while waiting, what is left while playing. */
    public String clockText() {
        int seconds = phase == Phase.WAITING ? settings().matchSeconds : phase == Phase.FREEZE ? settings().matchSeconds : (clock + 19) / 20;
        return Text.clock(Math.max(0, seconds));
    }
}
