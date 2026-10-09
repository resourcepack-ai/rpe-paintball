package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.Settings;
import ai.resourcepack.addons.paintball.Team;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The guns, the balls and the grenades of one match.
 *
 * <p>A ball is not a vanilla projectile: it is a position and a velocity the
 * plugin moves every tick, ray-traced from where it was to where it is going
 * against blocks and the hitboxes of the players it may hit, and drawn as a
 * glowing item display that the client slides between the two. That makes a
 * hit exactly where the ball was, at any speed, with none of a snowball's
 * habits (hitting the thrower, bouncing off leaves, knockback).
 */
final class Combat {
    private static final class Ball {
        final GamePlayer owner;
        final Team team;
        Vector pos;
        Vector vel;
        int age;
        ItemDisplay display;

        Ball(GamePlayer owner, Team team, Vector pos, Vector vel) {
            this.owner = owner;
            this.team = team;
            this.pos = pos;
            this.vel = vel;
        }
    }

    private static final class Grenade {
        final GamePlayer owner;
        final Team team;
        Vector pos;
        Vector vel;
        int fuse;
        float spin;
        ItemDisplay display;

        Grenade(GamePlayer owner, Team team, Vector pos, Vector vel, int fuse) {
            this.owner = owner;
            this.team = team;
            this.pos = pos;
            this.vel = vel;
            this.fuse = fuse;
        }
    }

    private final Game game;
    private final Content content;
    private final Fx fx;
    private final List<Ball> balls = new ArrayList<>();
    private final List<Grenade> grenades = new ArrayList<>();

    Combat(Game game, Content content, Fx fx) {
        this.game = game;
        this.content = content;
        this.fx = fx;
    }

    private Settings s() {
        return game.settings();
    }

    // ---- the marker ----------------------------------------------------------------

    /** Once a tick for each player in the match: the trigger, the hopper and the reload. */
    void trigger(GamePlayer gp, Player p, long now) {
        if (gp.reloading > 0) {
            if (--gp.reloading == 0) {
                gp.ammo = s().hopper;
                fx.reloadDone(p);
            }
            return;
        }
        boolean held = p.isHandRaised() && Loadout.isMarker(content, p.getActiveItem());
        if (!held || now < gp.nextShot) return;
        if (gp.ammo <= 0) {
            fx.empty(p);
            reload(gp, p);
            return;
        }
        fire(gp, p);
        gp.ammo--;
        gp.stats.shots++;
        gp.nextShot = now + s().fireIntervalTicks;
        if (gp.ammo == 0) reload(gp, p);
    }

    void reload(GamePlayer gp, Player p) {
        if (gp.reloading > 0 || gp.ammo >= s().hopper) return;
        game.aim(gp, p, false);
        gp.reloading = s().reloadTicks;
        fx.reloadStart(p);
    }

    /**
     * How far a shot may stray from the crosshair now, in degrees: none down the
     * sights, a little from the hip, more on the move or in the air, half of
     * either crouched. The HUD draws the same number as its spread ticks.
     */
    @SuppressWarnings("deprecation") // isOnGround: only widens the spread
    double spreadDegrees(GamePlayer gp, Player p) {
        if (gp.aiming) return s().aimSpread;
        double spread = s().spread;
        Vector v = p.getVelocity();
        boolean moving = Math.hypot(v.getX(), v.getZ()) > 0.09 || !p.isOnGround() || p.isSprinting();
        if (moving) spread += s().movingSpread;
        if (p.isSneaking()) spread *= 0.5;
        return spread;
    }

    private void fire(GamePlayer gp, Player p) {
        Location eye = p.getEyeLocation();
        Vector aim = eye.getDirection();
        Vector origin = eye.toVector();
        // Where the crosshair is, then the direction that lands a ball there:
        // the drop is aimed off, so the crosshair is where the paint goes.
        Vector target = crosshairTarget(gp, eye);
        Vector dir = scatter(launchTowards(origin, target), Math.toRadians(spreadDegrees(gp, p)));

        // The simulation starts at the eye, so a ball goes where the crosshair is;
        // the ball you SEE starts at the barrel and slides onto that line.
        Vector right = aim.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        right.normalize();
        // Down the sights the barrel is under the crosshair rather than off to the right.
        Vector muzzle = eye.toVector().add(aim.clone().multiply(0.6))
                .add(right.multiply(gp.aiming ? 0.0 : 0.24)).add(new Vector(0, gp.aiming ? -0.12 : -0.2, 0));

        Ball b = new Ball(gp, gp.team, origin.clone(), dir.multiply(s().speed));
        World w = p.getWorld();
        b.display = w.spawn(muzzle.toLocation(w), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(content.item("ball_" + gp.team.id()));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setGlowing(true);
            e.setGlowColorOverride(gp.team.bukkitColor());
            e.setTeleportDuration(1);
            e.setViewRange(1.5f);
            e.addScoreboardTag("paintball");
        });
        balls.add(b);
        fx.shot(eye);
    }

    /**
     * What the crosshair is on: the first block, or the first enemy a ball
     * could hit, along the line of sight; or, with nothing in range, a point
     * that far out.
     */
    private Vector crosshairTarget(GamePlayer gp, Location eye) {
        Vector look = eye.getDirection();
        RayTraceResult hit = eye.getWorld().rayTrace(eye, look, s().range, FluidCollisionMode.ALWAYS, true, 0.12,
                e -> mayHit(gp, e));
        return hit != null ? hit.getHitPosition() : eye.toVector().add(look.clone().multiply(s().range));
    }

    /**
     * The direction to fire from {@code origin} so the ball, dropping and
     * slowing exactly as {@link #tickBalls} moves it, passes through
     * {@code target}. Aims at the target, flies the ball to its distance, and
     * aims off by however much it missed; three or four rounds and it is under
     * a hundredth of a block. A target out of the ball's reach gets the
     * straight line.
     */
    Vector launchTowards(Vector origin, Vector target) {
        Vector line = target.clone().subtract(origin);
        double distance = line.length();
        if (distance < 0.5) return line.lengthSquared() < 1e-9 ? new Vector(0, 0, 1) : line.normalize();
        Vector axis = line.clone().normalize();
        Vector aimAt = target.clone();
        Vector dir = axis.clone();
        for (int i = 0; i < 6; i++) {
            dir = aimAt.clone().subtract(origin).normalize();
            Vector at = flyTo(origin, dir, axis, distance);
            if (at == null) return axis;
            Vector miss = target.clone().subtract(at);
            if (miss.lengthSquared() < 1e-4) break;
            aimAt.add(miss);
        }
        return dir;
    }

    /** Where a ball fired along {@code dir} is when it has come {@code distance} along {@code axis}, or null if it never does. */
    private Vector flyTo(Vector origin, Vector dir, Vector axis, double distance) {
        Settings s = s();
        Vector pos = origin.clone();
        Vector vel = dir.clone().multiply(s.speed);
        for (int tick = 0; tick <= s.lifetimeTicks; tick++) {
            Vector next = pos.clone().add(vel);
            double before = pos.clone().subtract(origin).dot(axis);
            double after = next.clone().subtract(origin).dot(axis);
            if (after >= distance) {
                double t = after - before < 1e-9 ? 0 : (distance - before) / (after - before);
                return pos.add(vel.clone().multiply(t));
            }
            pos = next;
            vel.multiply(s.drag).subtract(new Vector(0, s.gravity, 0));
        }
        return null;
    }

    /** A direction nudged by up to {@code radians}, evenly over the cone. */
    private static Vector scatter(Vector dir, double radians) {
        if (radians <= 0) return dir.clone().normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = radians * Math.sqrt(r.nextDouble());
        double around = r.nextDouble() * Math.PI * 2;
        Vector d = dir.clone().normalize();
        Vector a = Math.abs(d.getY()) < 0.99 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector u = d.clone().crossProduct(a).normalize();
        Vector w = d.clone().crossProduct(u).normalize();
        return d.multiply(Math.cos(angle))
                .add(u.multiply(Math.sin(angle) * Math.cos(around)))
                .add(w.multiply(Math.sin(angle) * Math.sin(around)))
                .normalize();
    }

    // ---- balls in flight -------------------------------------------------------------

    void tick() {
        tickBalls();
        tickGrenades();
    }

    private void tickBalls() {
        World w = game.arena.world();
        if (w == null) return;
        BoundingBox fence = game.arena.bounds() == null ? null : game.arena.bounds().clone().expand(4);
        Settings s = s();
        // A copy: a hit can end the round, and ending the round clears this list.
        for (Ball b : new ArrayList<>(balls)) {
            if (!balls.contains(b)) continue;
            b.age++;
            double len = b.vel.length();
            RayTraceResult hit = len < 1e-4 ? null : w.rayTrace(b.pos.toLocation(w), b.vel.clone().normalize(), len,
                    FluidCollisionMode.ALWAYS, true, 0.12, e -> mayHit(b.owner, e));
            if (hit != null) {
                Location at = hit.getHitPosition().toLocation(w);
                if (hit.getHitEntity() instanceof Player victim) {
                    GamePlayer gv = game.players.get(victim.getUniqueId());
                    fx.splat(at, b.team, false);
                    if (gv != null) game.hit(b.owner, gv, at, Weapon.MARKER);
                } else if (hit.getHitBlock() != null) {
                    land(b, hit.getHitBlock(), hit.getHitBlockFace(), at);
                }
                kill(b);
                balls.remove(b);
                continue;
            }
            b.pos.add(b.vel);
            b.vel.multiply(s.drag).subtract(new Vector(0, s.gravity, 0));
            if (b.age > s.lifetimeTicks || (fence != null && !fence.contains(b.pos))) {
                kill(b);
                balls.remove(b);
                continue;
            }
            if (b.display != null && b.display.isValid()) b.display.teleport(b.pos.toLocation(w));
            fx.trail(b.pos.toLocation(w), b.team);
        }
    }

    private void land(Ball b, Block block, BlockFace face, Location at) {
        if (block.isLiquid()) {
            fx.splash(at);
            return;
        }
        if (face == null) face = BlockFace.UP;
        fx.splat(at, b.team, false);
        game.paint.splat(b.team, block, face, at, 0.9 + ThreadLocalRandom.current().nextDouble() * 0.6);
    }

    /** Whether a ball from {@code shooter} stops on {@code e}: only players of the other team still in the match. */
    private boolean mayHit(GamePlayer shooter, Entity e) {
        if (!(e instanceof Player p)) return false;
        GamePlayer gp = game.players.get(p.getUniqueId());
        return gp != null && !gp.left && gp.alive && gp.team != null && gp.team != shooter.team;
    }

    private static void kill(Ball b) {
        if (b.display != null && b.display.isValid()) b.display.remove();
    }

    // ---- grenades --------------------------------------------------------------------

    void throwGrenade(GamePlayer gp, Player p) {
        Location eye = p.getEyeLocation();
        Vector vel = eye.getDirection().multiply(s().throwSpeed).add(new Vector(0, 0.18, 0)).add(p.getVelocity().multiply(0.5));
        Grenade g = new Grenade(gp, gp.team, eye.toVector().add(eye.getDirection().multiply(0.4)), vel, s().fuseTicks);
        World w = p.getWorld();
        g.display = w.spawn(g.pos.toLocation(w), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(content.item("grenade_" + gp.team.id()));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setGlowing(true);
            e.setGlowColorOverride(gp.team.bukkitColor());
            e.setTeleportDuration(1);
            e.setInterpolationDuration(1);
            e.addScoreboardTag("paintball");
        });
        grenades.add(g);
        p.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.7f);
    }

    private void tickGrenades() {
        World w = game.arena.world();
        if (w == null) return;
        for (Grenade g : new ArrayList<>(grenades)) {
            if (!grenades.contains(g)) continue;
            if (--g.fuse <= 0) {
                grenades.remove(g);
                if (g.display != null && g.display.isValid()) g.display.remove();
                burst(g, w);
                continue;
            }
            double len = g.vel.length();
            if (len > 1e-3) {
                RayTraceResult hit = w.rayTraceBlocks(g.pos.toLocation(w), g.vel.clone().normalize(), len, FluidCollisionMode.NEVER, true);
                if (hit != null && hit.getHitBlockFace() != null) {
                    Vector n = hit.getHitBlockFace().getDirection();
                    g.pos = hit.getHitPosition().add(n.clone().multiply(0.08));
                    // Reflect off the face and lose most of the speed.
                    double dot = g.vel.dot(n);
                    g.vel.subtract(n.multiply(2 * dot)).multiply(0.42);
                    if (Math.abs(dot) > 0.12) w.playSound(g.pos.toLocation(w), Sound.BLOCK_SLIME_BLOCK_STEP, 0.6f, 1.7f);
                } else {
                    g.pos.add(g.vel);
                }
            }
            g.vel.multiply(0.98).subtract(new Vector(0, 0.045, 0));
            g.spin += 0.35f * (float) Math.min(1, g.vel.length() * 3);
            if (g.display != null && g.display.isValid()) {
                g.display.teleport(g.pos.toLocation(w));
                g.display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationXYZ(g.spin, g.spin * 0.6f, 0),
                        new Vector3f(1, 1, 1), new Quaternionf()));
            }
            if (g.fuse % 4 == 0) w.spawnParticle(org.bukkit.Particle.DUST, g.pos.toLocation(w), 2, 0.05, 0.05, 0.05, 0,
                    new org.bukkit.Particle.DustOptions(g.team.bukkitColor(), 0.9f));
            if (g.fuse == 10) w.playSound(g.pos.toLocation(w), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
        }
    }

    private void burst(Grenade g, World w) {
        Location at = g.pos.toLocation(w);
        fx.splat(at, g.team, true);
        at.getWorld().spawnParticle(org.bukkit.Particle.DUST_COLOR_TRANSITION, at, 50, 1.2, 0.8, 1.2, 0,
                new org.bukkit.Particle.DustTransition(g.team.bukkitColor(), Color.WHITE, 1.4f));
        game.paint.burst(g.team, at, 3.2);
        double r = s().grenadeRadius;
        for (GamePlayer gv : new ArrayList<>(game.players.values())) {
            if (gv.left || !gv.alive || gv.team == null || gv.team == g.team) continue;
            Player vp = gv.player();
            if (vp == null) continue;
            Location body = vp.getLocation().add(0, 1, 0);
            if (!body.getWorld().equals(w) || body.distance(at) > r) continue;
            Vector to = body.toVector().subtract(at.toVector());
            RayTraceResult wall = w.rayTraceBlocks(at, to.clone().normalize(), Math.max(0.01, to.length()), FluidCollisionMode.NEVER, true);
            if (wall != null) continue;
            game.hit(g.owner, gv, body, Weapon.GRENADE);
        }
    }

    /** Drops every ball and grenade in flight: between rounds and at the end. */
    void clear() {
        for (Ball b : balls) kill(b);
        balls.clear();
        for (Grenade g : grenades) if (g.display != null && g.display.isValid()) g.display.remove();
        grenades.clear();
    }
}
