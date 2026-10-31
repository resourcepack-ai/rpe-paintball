package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.team.Team;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computer players, to fill a match up ({@code bots.fill}) or even its sides
 * ({@code bots.even-teams}).
 *
 * <p>A bot is a {@link GamePlayer} like anybody else: it is on a team (or has
 * its colour), it is in the kill feed, the scoreboard and the score, and the
 * balls it fires are the same balls, simulated the same way and landing where
 * they are aimed. What stands on the field is a mannequin (Minecraft 1.21.9 and
 * newer) in the bot's mask and suit with its marker in hand, kept for the whole
 * match: while the bot is out it is hidden from everybody, and it is put back at
 * a spawn when it comes back. It does not throw grenades or set mines.
 *
 * <p>It shoots whoever it can see that is nearest, once it has had them in sight
 * for {@code bots.reaction-ticks}, in bursts, with {@code bots.spread-degrees}
 * of spread, leading a moving target; sidesteps while it shoots; and otherwise
 * walks towards the nearest enemy (in capture the flag, half of each side goes
 * for the other flag and takes it home, and the rest stay by their own), going
 * round what is in its way. It is moved by its velocity, so walls stop it and it
 * jumps a step up.
 *
 * <p>It never counts towards stats.json, and a match with nobody left in it but
 * bots ends the way it does with nobody in it at all.
 */
final class Bots {
    /** A walk, blocks a tick: a player walking is 0.216. */
    private static final double SPEED = 0.2;
    private static final double JUMP = 0.42;
    /** How far it looks for somebody to shoot. */
    private static final double SIGHT = 48;
    /** The range it likes to fight at: closer it backs off, further it closes in. */
    private static final double NEAR = 6;
    private static final double FAR = 20;

    private final Game game;
    private boolean warnedNoFigure;

    Bots(Game game) {
        this.game = game;
    }

    private Settings s() {
        return game.settings();
    }

    // ---- who plays -------------------------------------------------------------------

    /**
     * Adds and takes away bots so the match has as many as config.yml asks
     * for: each side up to {@code bots.fill}, and with {@code bots.even-teams}
     * up to the other side's number of people; in a free for all, everybody up
     * to {@code bots.fill}. People come first: a bot gives its place up to
     * somebody who joins. A bot added during a match comes straight in.
     */
    void fill() {
        Settings s = s();
        List<GamePlayer> bots = new ArrayList<>();
        int[] people = new int[Team.values().length];
        int peopleInAll = 0;
        for (GamePlayer gp : game.active()) {
            if (gp.isBot()) {
                bots.add(gp);
            } else {
                peopleInAll++;
                if (gp.team != null) people[gp.team.ordinal()]++;
            }
        }
        if (peopleInAll == 0) {
            for (GamePlayer b : bots) remove(b);
            return;
        }
        if (game.mode.teams()) {
            int most = Math.max(people[0], people[1]);
            int target = Math.max(s.botsFill, s.botsEvenTeams ? most : 0);
            for (Team t : Team.values()) {
                List<GamePlayer> ours = new ArrayList<>();
                for (GamePlayer b : bots) if (b.team == t) ours.add(b);
                int want = Math.max(0, target - people[t.ordinal()]);
                trim(ours, want);
                for (int i = ours.size(); i < want; i++) add(t);
            }
            for (GamePlayer b : bots) if (b.team == null) remove(b);
        } else {
            int want = Math.max(0, s.botsFill - peopleInAll);
            trim(bots, want);
            for (int i = bots.size(); i < want; i++) add(null);
        }
    }

    /** Down to {@code keep} bots, the ones that are out going first. */
    private void trim(List<GamePlayer> bots, int keep) {
        bots.sort((a, b) -> Boolean.compare(a.alive, b.alive));
        while (bots.size() > keep) remove(bots.remove(0));
    }

    private void add(Team team) {
        GamePlayer gp = new GamePlayer(UUID.randomUUID(), name());
        gp.bot = new Bot();
        gp.team = team;
        gp.paint = team != null ? team.paint() : game.freestPaint();
        gp.reset();
        gp.minesLeft = 0;
        int attackers = 0;
        int all = 0;
        for (GamePlayer o : game.active()) {
            if (o.isBot() && o.team == team) {
                all++;
                if (o.bot.attacker) attackers++;
            }
        }
        gp.bot.attacker = attackers <= all / 2;
        game.players.put(gp.id, gp);
        if (game.live()) game.spawnBot(gp, game.phase == Game.Phase.FREEZE);
    }

    /** Off the field and out of the match. */
    void remove(GamePlayer gp) {
        game.flags.drop(gp);
        LivingEntity figure = gp.bot == null ? null : gp.bot.figure();
        if (figure != null) figure.remove();
        game.players.remove(gp.id);
    }

    /** Every bot gone: between matches, and when the arena closes. */
    void clear() {
        for (GamePlayer gp : new ArrayList<>(game.players.values())) if (gp.isBot()) remove(gp);
    }

    /** A name nobody in the match has: the next of bots.names, then "Bot 1", "Bot 2"... */
    private String name() {
        Set<String> taken = new HashSet<>();
        for (GamePlayer gp : game.players.values()) taken.add(gp.name.toLowerCase(Locale.ROOT));
        for (String n : s().botNames) if (taken.add(n.toLowerCase(Locale.ROOT))) return n;
        for (int i = 1; ; i++) if (taken.add(("bot " + i))) return "Bot " + i;
    }

    // ---- the figure ------------------------------------------------------------------

    /**
     * Its figure at {@code at}, dressed and in view: made the first time, moved
     * and shown after. True when it is a new figure, whose name the match's
     * scoreboard teams have to be told about.
     */
    boolean place(GamePlayer gp, Location at) {
        LivingEntity figure = gp.bot.figure();
        boolean made = false;
        if (figure == null || !figure.getWorld().equals(at.getWorld())) {
            if (figure != null) figure.remove();
            figure = summon(gp, at);
            if (figure == null) return false;
            gp.bot.figure = figure.getUniqueId();
            made = true;
        } else {
            Nametags.teleport(figure, at);
        }
        figure.setVelocity(new Vector());
        figure.setGlowing(false);
        dress(gp, figure);
        figure.setVisibleByDefault(true);
        Bot b = gp.bot;
        b.target = null;
        b.detour = null;
        b.lastPos = at.clone();
        b.lastPosAt = game.now;
        b.burst = 0;
        return made;
    }

    /** Out: hidden from everybody until it comes back. */
    void splat(GamePlayer gp) {
        LivingEntity figure = gp.bot.figure();
        if (figure == null) return;
        figure.setVelocity(new Vector());
        figure.setVisibleByDefault(false);
    }

    private LivingEntity summon(GamePlayer gp, Location at) {
        World w = at.getWorld();
        EntityType type;
        try {
            // Minecraft 1.21.9's figure that looks like a player; the plugin is
            // built against an API that has no name for it.
            type = EntityType.valueOf("MANNEQUIN");
        } catch (IllegalArgumentException e) {
            type = EntityType.ARMOR_STAND;
            if (!warnedNoFigure) {
                warnedNoFigure = true;
                game.plugin.getLogger().warning("This server has no mannequins (Minecraft 1.21.9 and newer), so bots are armour stands.");
            }
        }
        Class<? extends Entity> kind = type.getEntityClass();
        if (kind == null || !LivingEntity.class.isAssignableFrom(kind)) return null;
        Entity e;
        try {
            e = w.spawn(at, kind, made -> {
                made.setPersistent(false);
                made.addScoreboardTag("paintball");
                made.addScoreboardTag("paintball_bot");
                made.setInvulnerable(true);
                made.setSilent(true);
                made.setCustomName(gp.name);
                made.setCustomNameVisible(true);
                if (made instanceof LivingEntity living) {
                    living.setRemoveWhenFarAway(false);
                    living.setCollidable(false);
                }
                if (made instanceof ArmorStand stand) {
                    stand.setArms(true);
                    stand.setBasePlate(false);
                }
            });
        } catch (RuntimeException ex) {
            game.plugin.getLogger().warning("Could not put a bot on the field: " + ex);
            return null;
        }
        return (LivingEntity) e;
    }

    private void dress(GamePlayer gp, LivingEntity figure) {
        EntityEquipment eq = figure.getEquipment();
        if (eq == null) return;
        eq.setHelmet(Loadout.mask(game.content, gp.paint));
        eq.setChestplate(Loadout.armour(Material.LEATHER_CHESTPLATE, gp.paint));
        eq.setLeggings(Loadout.armour(Material.LEATHER_LEGGINGS, gp.paint));
        eq.setBoots(Loadout.armour(Material.LEATHER_BOOTS, gp.paint));
        eq.setItemInMainHand(Loadout.marker(game.content, game.skinOf(gp), false, false));
    }

    // ---- its mind --------------------------------------------------------------------

    /** Once a tick while it is in a live match: look, shoot, move. */
    void tick(GamePlayer gp) {
        LivingEntity me = gp.body();
        if (me == null) return;
        Bot b = gp.bot;
        Location at = me.getLocation();
        if (!game.arena.contains(at)) {
            // Strayed off the field, as a player would be put back.
            if (place(gp, gp.lastInside != null ? gp.lastInside : game.spawnFor(gp))) game.hud.refreshTeams();
            return;
        }
        if (game.now % 10 == 0 && me.isOnGround()) gp.lastInside = at;

        reload(gp);
        if (game.now >= b.nextLook) {
            b.nextLook = game.now + 5;
            GamePlayer seen = nearestInSight(gp, me);
            if (seen != b.target) {
                b.target = seen;
                b.seenSince = game.now;
            }
        }
        GamePlayer target = b.target;
        LivingEntity them = target == null || !target.alive || target.left ? null : target.body();
        if (them == null) {
            b.target = null;
            Location goal = objective(gp, me);
            if (goal != null) {
                Vector way = goal.toVector().subtract(at.toVector()).setY(0);
                walk(gp, me, way.lengthSquared() < 1.5 ? new Vector() : way);
                face(me, at.clone().add(way.clone().normalize().multiply(4)).add(0, me.getEyeHeight(), 0));
            } else {
                walk(gp, me, new Vector());
            }
            return;
        }

        Location aim = chest(them);
        Vector across = aim.toVector().subtract(at.toVector()).setY(0);
        double distance = across.length();
        face(me, aim);
        shoot(gp, me, them, aim);

        // Sidestep while shooting, closing in from far and backing off from close.
        if (game.now >= b.strafeUntil) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            b.strafe = r.nextBoolean() ? 1 : -1;
            b.strafeUntil = game.now + 15 + r.nextInt(30);
        }
        Vector to = distance < 1e-3 ? new Vector(1, 0, 0) : across.clone().multiply(1 / distance);
        Vector move = new Vector(-to.getZ(), 0, to.getX()).multiply(b.strafe * 0.8);
        if (onAnErrand(gp)) {
            // Going for the flag, or home with it: shooting on the way, not stopping to.
            Location goal = objective(gp, me);
            Vector way = goal == null ? new Vector() : goal.toVector().subtract(at.toVector()).setY(0);
            if (way.lengthSquared() > 1.5) move = way.normalize().add(move.multiply(0.3));
        } else if (distance > FAR) {
            move.add(to);
        } else if (distance < NEAR) {
            move.subtract(to.clone().multiply(0.7));
        }
        walk(gp, me, move);
    }

    /** In capture the flag: carrying a flag, or an attacker after theirs. */
    private boolean onAnErrand(GamePlayer gp) {
        if (game.mode != Mode.CTF || gp.team == null) return false;
        return game.flags.carrying(gp) || (gp.bot.attacker && game.flags.carrierOf(gp.team.other()) == null);
    }

    private void reload(GamePlayer gp) {
        if (gp.reloading > 0 && --gp.reloading == 0) gp.ammo = s().hopper;
    }

    private void shoot(GamePlayer gp, LivingEntity me, LivingEntity them, Location aim) {
        Bot b = gp.bot;
        Settings s = s();
        if (gp.reloading > 0 || game.now < gp.nextShot || game.now - b.seenSince < s.botReactionTicks) return;
        if (b.burst <= 0) {
            if (game.now < b.burstAt) return;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            b.burst = 4 + r.nextInt(6);
        }
        // Where they will be when the ball gets there, roughly.
        Vector lead = them.getVelocity().setY(0).multiply(aim.distance(me.getEyeLocation()) / Math.max(0.5, s.speed));
        Vector target = aim.toVector().add(lead);
        boolean moving = me.getVelocity().setY(0).lengthSquared() > 0.008;
        game.combat.fireFrom(gp, me.getEyeLocation(), target, s.botSpread + (moving ? s.movingSpread * 0.5 : 0));
        gp.stats.shots++;
        gp.nextShot = game.now + s.fireIntervalTicks;
        if (--b.burst <= 0) b.burstAt = game.now + 8 + ThreadLocalRandom.current().nextInt(16);
        if (--gp.ammo <= 0) {
            gp.reloading = s.reloadTicks;
            b.burst = 0;
        }
    }

    /** The nearest enemy it can see, or null. */
    private GamePlayer nearestInSight(GamePlayer gp, LivingEntity me) {
        Location eye = me.getEyeLocation();
        GamePlayer best = null;
        double nearest = SIGHT * SIGHT;
        for (GamePlayer o : game.active()) {
            if (!o.alive || !game.enemies(gp, o) || game.isProtected(o)) continue;
            LivingEntity them = o.body();
            if (them == null || !them.getWorld().equals(eye.getWorld())) continue;
            if (them instanceof Player p && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Location chest = chest(them);
            double d = chest.distanceSquared(eye);
            if (d >= nearest || !clear(eye, chest)) continue;
            nearest = d;
            best = o;
        }
        return best;
    }

    /** Whether nothing solid is between two points. */
    private static boolean clear(Location from, Location to) {
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length < 1e-3) return true;
        RayTraceResult wall = from.getWorld().rayTraceBlocks(from, line.multiply(1 / length), length,
                FluidCollisionMode.NEVER, true);
        return wall == null;
    }

    private static Location chest(LivingEntity e) {
        return e.getLocation().add(0, e.getHeight() * 0.6, 0);
    }

    /**
     * Where it is heading with nobody in sight: the nearest enemy, wherever
     * they are; in capture the flag, the other flag (and home with it) for an
     * attacker, its own for a guard, or wherever its own flag has been taken.
     */
    private Location objective(GamePlayer gp, LivingEntity me) {
        Location at = me.getLocation();
        if (game.mode == Mode.CTF && gp.team != null) {
            Team ours = gp.team;
            Team theirs = ours.other();
            if (game.flags.carrying(gp)) return game.flags.homeOf(ours);
            GamePlayer thief = game.flags.carrierOf(ours);
            LivingEntity thiefBody = thief == null ? null : thief.body();
            if (thiefBody != null) return thiefBody.getLocation();
            if (gp.bot.attacker) {
                GamePlayer mate = game.flags.carrierOf(theirs);
                LivingEntity escort = mate == null || mate == gp ? null : mate.body();
                return escort != null ? escort.getLocation() : game.flags.at(theirs);
            }
            Location home = game.flags.at(ours);
            if (home != null && home.distanceSquared(at) > 36) return home;
        }
        Location best = null;
        double nearest = Double.MAX_VALUE;
        for (GamePlayer o : game.active()) {
            if (!o.alive || !game.enemies(gp, o)) continue;
            LivingEntity them = o.body();
            if (them == null || !them.getWorld().equals(at.getWorld())) continue;
            double d = them.getLocation().distanceSquared(at);
            if (d < nearest) {
                nearest = d;
                best = them.getLocation();
            }
        }
        if (best != null) return best;
        BoundingBox box = game.arena.bounds();
        return box == null ? null : new Location(at.getWorld(), box.getCenterX(), at.getY(), box.getCenterZ());
    }

    /**
     * Moves it by its velocity, at walking pace, in {@code way}: round anything
     * that stops it, and up a step.
     */
    private void walk(GamePlayer gp, LivingEntity me, Vector way) {
        Bot b = gp.bot;
        Location at = me.getLocation();
        Vector v = me.getVelocity();
        if (way.lengthSquared() < 1e-6) {
            me.setVelocity(new Vector(0, v.getY(), 0));
            return;
        }
        Vector dir = way.clone().setY(0).normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (b.detour != null && game.now < b.detourUntil) dir = b.detour.clone();

        // Getting nowhere: try another way for a second or two.
        if (game.now - b.lastPosAt >= 20) {
            if (b.lastPos != null && b.lastPos.getWorld().equals(at.getWorld()) && b.lastPos.distanceSquared(at) < 0.25) {
                b.detour = new Vector(-dir.getZ(), 0, dir.getX()).multiply(r.nextBoolean() ? 1 : -1)
                        .add(dir.clone().multiply(-0.3)).normalize();
                b.detourUntil = game.now + 20 + r.nextInt(25);
                dir = b.detour.clone();
            }
            b.lastPos = at.clone();
            b.lastPosAt = game.now;
        }

        double y = v.getY();
        Block ahead = at.clone().add(dir.clone().multiply(0.7)).getBlock();
        if (ahead.getType().isSolid()) {
            boolean step = !ahead.getRelative(0, 1, 0).getType().isSolid() && !ahead.getRelative(0, 2, 0).getType().isSolid()
                    && !at.getBlock().getRelative(0, 2, 0).getType().isSolid();
            if (step && me.isOnGround()) {
                y = JUMP;
            } else if (b.detour == null || game.now >= b.detourUntil) {
                // A wall: along it, one way or the other.
                b.detour = new Vector(-dir.getZ(), 0, dir.getX()).multiply(r.nextBoolean() ? 1 : -1);
                b.detourUntil = game.now + 15 + r.nextInt(20);
                dir = b.detour.clone();
            }
        }
        double speed = SPEED;
        if (game.flags.carrying(gp)) speed *= s().carrierSpeed;
        me.setVelocity(new Vector(dir.getX() * speed, y, dir.getZ() * speed));
    }

    /** Turns it, head and all, to look at {@code target}. */
    private static void face(LivingEntity me, Location target) {
        Location eye = me.getEyeLocation();
        Vector d = target.toVector().subtract(eye.toVector());
        if (d.lengthSquared() < 1e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.getY(), Math.hypot(d.getX(), d.getZ())));
        me.setRotation(yaw, pitch);
    }

    /** Whether {@code e} is one of these figures, for a sweep of leftovers. */
    static boolean isFigure(Entity e) {
        return e.getScoreboardTags().contains("paintball_bot");
    }

    /** The bot whose figure {@code e} is, or null. */
    GamePlayer of(Entity e) {
        UUID id = e.getUniqueId();
        for (GamePlayer gp : game.players.values()) {
            if (gp.bot != null && id.equals(gp.bot.figure)) return gp;
        }
        return null;
    }
}
