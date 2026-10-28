package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.command.Perms;
import ai.resourcepack.addons.paintball.content.Content;
import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
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
import org.bukkit.inventory.EquipmentSlotGroup;

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
    final KillCam killCam = new KillCam(this);
    private final GameManager manager;

    Phase phase = Phase.WAITING;
    /** Ticks left in FREEZE or ENDED. */
    int timer;
    /** Ticks left on the match clock while LIVE. */
    int clock;
    long now;
    /** When the match went live and when it ended: the HUD's GO! and result are timed from these. */
    long liveAt;
    long endedAt;
    /** The last of the clock's warnings: when it went off (-1 for none yet this match), and how many seconds were left. */
    long warnedAt = -1;
    int warnedSeconds;
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
        this.paint = new PaintLayer(content, plugin.settings(arena).maxDecals);
        this.combat = new Combat(this, content, fx);
        this.hud = new Hud(this);
        if (FREEZE_KEY == null) FREEZE_KEY = new NamespacedKey(plugin, "freeze");
        if (AIM_KEY == null) AIM_KEY = new NamespacedKey(plugin, "aim");
        for (Team t : Team.values()) kills.put(t, 0);
    }

    /** This arena's settings: its own options, then config.yml's. */
    Settings settings() {
        return plugin.settings(arena);
    }

    ai.resourcepack.addons.paintball.config.Messages messages() {
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
            toLobby(p, gp);
            broadcast(messages().component("joined-lobby", "player", p.getName(), "count", String.valueOf(size())));
            Text.send(p, messages().component(p.hasPermission(Perms.START) ? "start-hint-op" : "start-hint"));
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
        killCam.stop(gp, p);
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

    private void toLobby(Player p, GamePlayer gp) {
        unfreeze(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(arena.waiting());
        PlayerSnapshot.reset(p);
        Loadout.lobby(content, messages(), p, gp.pick);
    }

    /**
     * A team picked in the lobby, used from the next match dealt. With
     * {@code toggle} (the hotbar's team items) picking the same side again
     * leaves it to chance; {@code null} always does.
     */
    public void pick(Player p, Team team, boolean toggle) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null) return;
        gp.pick = team == null || (toggle && gp.pick == team) ? null : team;
        gp.pickedAt = now;
        if (gp.pick == null) {
            Text.send(p, messages().component("picked-random"));
        } else {
            Text.send(p, messages().component(live() ? "picked-next" : "picked",
                    "colour", gp.pick.color().asHexString(), "team", gp.pick.display()));
        }
        if (!live() && p.getGameMode() != GameMode.SPECTATOR) {
            int held = p.getInventory().getHeldItemSlot();
            Loadout.lobby(content, messages(), p, gp.pick);
            p.getInventory().setHeldItemSlot(held);
        }
    }

    /**
     * Teams for a new match. Everybody who picked a side gets it, as long as
     * the two stay within one player of each other: past that, whoever picked
     * the crowded side last crosses over. Everybody else fills in, the smaller
     * team first. Answers who was moved off their pick.
     */
    private List<GamePlayer> deal(List<GamePlayer> list) {
        Collections.shuffle(list);
        EnumMap<Team, List<GamePlayer>> sides = new EnumMap<>(Team.class);
        for (Team t : Team.values()) sides.put(t, new ArrayList<>());
        List<GamePlayer> open = new ArrayList<>();
        for (GamePlayer gp : list) {
            if (gp.pick != null) sides.get(gp.pick).add(gp);
            else open.add(gp);
        }
        int most = (list.size() + 1) / 2;
        List<GamePlayer> moved = new ArrayList<>();
        for (Team t : Team.values()) {
            List<GamePlayer> side = sides.get(t);
            side.sort(Comparator.comparingLong(gp -> gp.pickedAt));
            while (side.size() > most) {
                GamePlayer crossing = side.remove(side.size() - 1);
                sides.get(t.other()).add(crossing);
                moved.add(crossing);
            }
        }
        for (GamePlayer gp : open) {
            sides.get(sides.get(Team.RED).size() <= sides.get(Team.BLUE).size() ? Team.RED : Team.BLUE).add(gp);
        }
        for (Team t : Team.values()) for (GamePlayer gp : sides.get(t)) gp.team = t;
        return moved;
    }

    // ---- the clock -----------------------------------------------------------------

    void tick() {
        now++;
        switch (phase) {
            case WAITING -> {
            }
            case FREEZE -> {
                timer--;
                // The count itself is the HUD's; this is its sound.
                if (timer > 0 && timer % 20 == 0) {
                    for (Player p : online()) fx.tick(p, timer / 20);
                }
                if (timer <= 0) goLive();
            }
            case LIVE -> tickLive();
            case ENDED -> {
                timer--;
                // The result has the screen to itself for three seconds first.
                if (timer == settings().endSeconds * 20 - 60) {
                    for (Player p : online()) plugin.scoreboard().open(p, this);
                }
                if (timer % 20 == 0 && timer > settings().endSeconds * 20 - 120 && winner != null) {
                    List<GamePlayer> team = roster(winner);
                    if (!team.isEmpty()) {
                        Player w = team.get((int) (now / 20 % team.size())).player();
                        if (w != null) fx.firework(w.getLocation().add(0, 1, 0), winner);
                    }
                }
                if (timer <= 0 && !backToLobby()) return;
            }
        }
        hud.tick();
    }

    private void tickLive() {
        clock--;
        if (clock > 0 && clock % 20 == 0 && settings().timeWarnings.contains(clock / 20)) {
            // The sound is here; the words are the HUD's.
            warnedAt = now;
            warnedSeconds = clock / 20;
            for (Player p : online()) fx.timeWarning(p, warnedSeconds);
        }
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
            } else {
                killCam.tick(gp, p);
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
        if (live() || size() == 0 || !open()) return false;
        if (phase == Phase.ENDED && !backToLobby()) return false;
        players.values().removeIf(gp -> gp.left);
        List<GamePlayer> list = new ArrayList<>(active());
        List<GamePlayer> moved = deal(list);
        for (GamePlayer gp : list) gp.reset();
        for (GamePlayer gp : moved) {
            Player p = gp.player();
            if (p != null) {
                Text.send(p, messages().component("pick-moved", "from", gp.team.other().display(),
                        "from_colour", gp.team.other().color().asHexString(),
                        "team", gp.team.display(), "colour", gp.team.color().asHexString()));
            }
        }
        for (Team t : Team.values()) kills.put(t, 0);
        feed.clear();
        winner = null;
        warnedAt = -1;
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
            fx.tick(p, timer / 20);
        }
        return true;
    }

    private void goLive() {
        phase = Phase.LIVE;
        liveAt = now;
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            unfreeze(p);
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
        gp.protectedUntil = first ? 0 : now + s.protectionTicks;
        aim(gp, p, false);
        Location at = first ? slotFor(gp) : spawnFor(gp);
        killCam.stop(gp, p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(at);
        gp.lastInside = at;
        PlayerSnapshot.reset(p);
        Loadout.equip(content, s, p, gp);
        // The grenade keeps recharging through a death: dying is not a way to get it back sooner.
        if (gp.grenadeReadyAt > now) p.setCooldown(Loadout.grenade(content, gp.team, s.grenadeCooldownTicks), (int) (gp.grenadeReadyAt - now));
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
        victim.splattedByTeam = shooter.team;
        victim.splattedAt = now;
        victim.splat = 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(3);
        shooter.lastKill = victim.name;
        shooter.lastKillTeam = victim.team;
        shooter.lastKillAt = now;
        shooter.hitUntil = now + settings().hitMarkerTicks;

        Player vp = victim.player();
        Player sp = shooter.player();
        fx.splatted(vp);
        fx.hitConfirm(sp);
        if (vp != null) {
            // The paint on their screen and who did it are both the HUD's.
            aim(victim, vp, false);
            fx.knockedOut(vp);
            vp.getInventory().clear();
            vp.setGameMode(GameMode.SPECTATOR);
            if (settings().killerCamera && sp != null && sp.getWorld().equals(vp.getWorld())) killCam.start(victim, vp, sp);
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
        endedAt = now;
        timer = settings().endSeconds * 20;
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p == null) continue;
            aim(gp, p, false);
            unfreeze(p);
            killCam.stop(gp, p);
            if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            // The result and the score are the HUD's.
            fx.fanfare(p, winner != null && gp.team == winner);
        }
        hud.refreshScores();
    }

    /**
     * Whether this arena can still be played: enabled, and still the arena of
     * that id (not deleted or replaced since this game began).
     */
    boolean open() {
        return arena.enabled() && plugin.arenas().get(arena.id()) == arena;
    }

    /**
     * The scoreboard has been up long enough: everybody back to the lobby, paint
     * off the walls. If the arena was closed or deleted meanwhile, everybody goes
     * home instead, and this answers false: the game is over.
     */
    private boolean backToLobby() {
        if (!open()) {
            broadcast(messages().component("closed"));
            close();
            return false;
        }
        phase = Phase.WAITING;
        combat.clear();
        paint.clear();
        players.values().removeIf(gp -> gp.left);
        for (GamePlayer gp : active()) {
            Player p = gp.player();
            if (p != null) toLobby(p, gp);
        }
        hud.refreshTeams();
        return true;
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
            killCam.stop(gp, p);
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
            if (inst == null || modifier(inst, FREEZE_KEY) != null) continue;
            inst.addModifier(new AttributeModifier(FREEZE_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    static void unfreeze(Player p) {
        if (FREEZE_KEY == null) return;
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = p.getAttribute(a);
            AttributeModifier mine = inst == null ? null : modifier(inst, FREEZE_KEY);
            if (mine != null) inst.removeModifier(mine);
        }
    }

    /** One of our modifiers on an attribute, by key, or null. */
    private static AttributeModifier modifier(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) if (key.equals(m.getKey())) return m;
        return null;
    }

    /**
     * Takes off every modifier this plugin puts on a player. Spigot has no
     * transient modifiers, so the freeze and the aim's slowdown are saved with
     * the player like any other; a server that goes down mid-match would
     * otherwise leave somebody frozen, so they come off on every join.
     */
    public static void clearModifiers(Player p, org.bukkit.plugin.Plugin plugin) {
        String ns = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        for (Attribute a : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey() != null && m.getKey().getNamespace().equals(ns)) inst.removeModifier(m);
            }
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
        for (Player p : online()) Text.send(p, c);
    }

    /** Throws a grenade for a player, if they have one. */
    public void throwGrenade(Player p) {
        GamePlayer gp = players.get(p.getUniqueId());
        if (gp == null || phase != Phase.LIVE || !gp.alive || now < gp.grenadeReadyAt) return;
        // The grenade stays in the hotbar and recharges, greyed out by the game's own cooldown sweep.
        int cooldown = settings().grenadeCooldownTicks;
        gp.grenadeReadyAt = now + cooldown;
        combat.throwGrenade(gp, p);
        if (cooldown > 0) p.setCooldown(Loadout.grenade(content, gp.team, cooldown), cooldown);
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
                inv.setItem(slot, Loadout.marker(content, Loadout.skin(settings(), gp.team), aimed));
            }
        }
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            AttributeModifier slowed = modifier(speed, AIM_KEY);
            if (slowed != null) speed.removeModifier(slowed);
            if (on) {
                speed.addModifier(new AttributeModifier(AIM_KEY, -settings().aimSlowdown,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
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
