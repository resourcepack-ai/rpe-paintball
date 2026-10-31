package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.team.Team;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Capture the flag: a flag at each base. Walk into the other team's to take
 * it; you glow and walk a little slower while you carry it. Bring it to your
 * own flag, while yours is at home, to score. Splatted, you drop it where you
 * fell; a teammate of the flag's who touches it sends it home, and otherwise
 * it goes home on its own after {@code ctf.flag-return-seconds}.
 *
 * <p>A flag is an item display of the flag model in its team's colour. It is
 * never a passenger of its carrier, because a player carrying a passenger
 * cannot be teleported and a match teleports people all the time: it follows
 * them every tick instead, sliding there over the tick as the client draws it.
 */
final class Flags {
    enum State { HOME, TAKEN, DROPPED }

    /** How big the flag stands at its base, and on its carrier's back. */
    private static final float STANDING = 2.2f;
    private static final float CARRIED = 1.3f;

    static final class Flag {
        final Team team;
        final Location home;
        State state = State.HOME;
        GamePlayer carrier;
        Location at;
        long returnAt;
        ItemDisplay display;

        Flag(Team team, Location home) {
            this.team = team;
            this.home = home;
            this.at = home.clone();
        }
    }

    /** The last thing that happened to a flag, for the HUD: what, whose flag, and when. */
    record Event(String key, Team flag, long at) {
    }

    private static NamespacedKey CARRY_KEY;

    private final Game game;
    private final Map<Team, Flag> flags = new EnumMap<>(Team.class);
    Event last;

    Flags(Game game) {
        this.game = game;
    }

    /**
     * Made on first use, not in the constructor: Game builds its Flags in a
     * field initialiser, which runs before Game's constructor sets
     * {@code plugin}.
     */
    private NamespacedKey carryKey() {
        if (CARRY_KEY == null) CARRY_KEY = new NamespacedKey(game.plugin, "carry");
        return CARRY_KEY;
    }

    /** Both flags up at their bases, for a new match. */
    void start() {
        clear();
        for (Team t : Team.values()) {
            Location home = game.arena.flag(t);
            if (home == null) continue;
            Flag f = new Flag(t, home);
            f.display = home.getWorld().spawn(home, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(game.content.item("flag_" + t.id()));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTeleportDuration(1);
                e.setBrightness(new Display.Brightness(15, 15));
                e.addScoreboardTag("paintball");
            });
            standing(f);
            flags.put(t, f);
        }
    }

    /** Every flag down and every carrier let go: the match is over. */
    void clear() {
        for (Flag f : flags.values()) {
            if (f.carrier != null) unburden(f.carrier);
            if (f.display != null && f.display.isValid()) f.display.remove();
        }
        flags.clear();
        last = null;
    }

    State state(Team t) {
        Flag f = flags.get(t);
        return f == null ? State.HOME : f.state;
    }

    /** Whether {@code gp} is carrying a flag. */
    boolean carrying(GamePlayer gp) {
        for (Flag f : flags.values()) if (f.carrier == gp) return true;
        return false;
    }

    /** {@code gp} was splatted or left: whatever flag they carried falls where they were. */
    void drop(GamePlayer gp) {
        for (Flag f : flags.values()) {
            if (f.carrier != gp) continue;
            LivingEntity p = gp.body();
            Location where = p != null ? p.getLocation() : f.at;
            unburden(gp);
            f.carrier = null;
            f.state = State.DROPPED;
            f.at = ground(where);
            f.returnAt = game.now + game.settings().flagReturnTicks;
            standing(f);
            announce("ctf-dropped", "hud-flag-dropped", f.team, gp, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f);
        }
    }

    /** Once a tick in a live match. */
    void tick() {
        Settings s = game.settings();
        for (Flag f : flags.values()) {
            if (f.state == State.TAKEN) {
                follow(f);
                continue;
            }
            if (f.state == State.DROPPED) {
                if (s.flagReturnTicks > 0 && game.now >= f.returnAt) {
                    home(f);
                    announce("ctf-returned-auto", "hud-flag-returned", f.team, null, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f);
                    continue;
                }
                if (game.now % 4 == 0) {
                    f.at.getWorld().spawnParticle(Particle.DUST, f.at.clone().add(0, 0.2, 0), 3, 0.25, 0.1, 0.25, 0,
                            new Particle.DustOptions(f.team.paint().bukkitColor(), 1.2f));
                }
            }
            spin(f);
            for (GamePlayer gp : game.active()) {
                if (!gp.alive || gp.team == null) continue;
                LivingEntity p = gp.body();
                if (p == null || !near(p.getLocation(), f.at, s.flagReach)) continue;
                if (gp.team != f.team) {
                    if (!carrying(gp)) take(f, gp, p);
                } else if (f.state == State.DROPPED) {
                    home(f);
                    announce("ctf-returned", "hud-flag-returned", f.team, gp, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f);
                } else {
                    Flag theirs = carried(gp);
                    if (theirs != null) capture(gp, theirs);
                }
                if (game.phase != Game.Phase.LIVE) return;
                if (f.state != State.HOME && f.state != State.DROPPED) break;
            }
        }
    }

    /** Where {@code team}'s flag is now: at home, dropped, or on somebody's back; null with no flags. */
    Location at(Team team) {
        Flag f = flags.get(team);
        return f == null ? null : f.at.clone();
    }

    /** Where {@code team}'s flag stands at home; null with no flags. */
    Location homeOf(Team team) {
        Flag f = flags.get(team);
        return f == null ? null : f.home.clone();
    }

    /** Who has {@code team}'s flag, or null. */
    GamePlayer carrierOf(Team team) {
        Flag f = flags.get(team);
        return f == null ? null : f.carrier;
    }

    /** The flag {@code gp} is carrying, or null. */
    private Flag carried(GamePlayer gp) {
        for (Flag f : flags.values()) if (f.carrier == gp) return f;
        return null;
    }

    private void take(Flag f, GamePlayer gp, LivingEntity p) {
        f.state = State.TAKEN;
        f.carrier = gp;
        Settings s = game.settings();
        if (s.carrierGlows) p.setGlowing(true);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && s.carrierSpeed != 1.0) {
            speed.addModifier(new AttributeModifier(carryKey(), s.carrierSpeed - 1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        // No sights while carrying: a flag on your back and a scope at your eye do not go together.
        if (p instanceof Player player) game.aim(gp, player, false);
        follow(f);
        announce("ctf-taken", "hud-flag-taken", f.team, gp, Sound.BLOCK_NOTE_BLOCK_BELL, 1.4f);
    }

    /** {@code gp} brought {@code theirs} to their own flag: a point, if their own is at home (or that is not asked). */
    private void capture(GamePlayer gp, Flag theirs) {
        Flag own = flags.get(gp.team);
        if (game.settings().needOwnFlag && own != null && own.state != State.HOME) return;
        unburden(gp);
        theirs.carrier = null;
        home(theirs);
        gp.stats.captures++;
        LivingEntity p = gp.body();
        if (p != null) game.fx.firework(p.getLocation().add(0, 1, 0), gp.paint);
        announce("ctf-captured", "hud-flag-captured", theirs.team, gp, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
        game.captured(gp.team);
    }

    /** Back to its base. */
    private void home(Flag f) {
        if (f.carrier != null) unburden(f.carrier);
        f.carrier = null;
        f.state = State.HOME;
        f.at = f.home.clone();
        standing(f);
    }

    /** Takes the carrier's glow and slowness off. */
    private void unburden(GamePlayer gp) {
        LivingEntity p = gp.body();
        if (p == null) return;
        p.setGlowing(false);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;
        for (AttributeModifier m : new java.util.ArrayList<>(speed.getModifiers())) {
            if (carryKey().equals(m.getKey())) speed.removeModifier(m);
        }
    }

    /** The carried flag, on the carrier's back and over their head; dropped if they can no longer carry it. */
    private void follow(Flag f) {
        GamePlayer gp = f.carrier;
        LivingEntity p = gp == null ? null : gp.body();
        if (p == null || gp.left || !gp.alive) {
            if (gp != null) drop(gp);
            return;
        }
        Location body = p.getLocation();
        Vector back = body.getDirection().setY(0);
        if (back.lengthSquared() < 1e-6) back = new Vector(0, 0, 1);
        back.normalize().multiply(-0.35);
        Location at = body.clone().add(back).add(0, 1.15, 0);
        at.setPitch(0);
        f.at = at;
        if (f.display == null || !f.display.isValid()) return;
        f.display.teleport(at);
        f.display.setTransformation(new Transformation(new Vector3f(0, CARRIED / 2, 0),
                new Quaternionf().rotationZ(-0.15f), new Vector3f(CARRIED, CARRIED, CARRIED), new Quaternionf()));
        if (game.now % 3 == 0) {
            p.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, CARRIED, 0), 2, 0.15, 0.15, 0.15, 0,
                    new Particle.DustOptions(f.team.paint().bukkitColor(), 1.0f));
        }
    }

    /** Stood upright where it is, at full size. */
    private void standing(Flag f) {
        if (f.display == null || !f.display.isValid()) return;
        Location at = f.at.clone();
        at.setPitch(0);
        f.display.teleport(at);
        f.display.setTransformation(new Transformation(new Vector3f(0, STANDING / 2, 0),
                new Quaternionf(), new Vector3f(STANDING, STANDING, STANDING), new Quaternionf()));
    }

    /** A standing flag turns slowly, so it reads from every side. */
    private void spin(Flag f) {
        if (f.display == null || !f.display.isValid() || game.now % 2 != 0) return;
        Location at = f.display.getLocation();
        at.setYaw(at.getYaw() + 3f);
        f.display.teleport(at);
    }

    /** Within {@code reach} across, and close enough up and down to be standing at it. */
    private static boolean near(Location who, Location flag, double reach) {
        if (who.getWorld() == null || !who.getWorld().equals(flag.getWorld())) return false;
        double dy = who.getY() - flag.getY();
        return dy > -1.5 && dy < 2.5 && Math.hypot(who.getX() - flag.getX(), who.getZ() - flag.getZ()) <= reach;
    }

    /** Where something let go at {@code at} comes to rest: on the first solid thing below it. */
    private static Location ground(Location at) {
        RayTraceResult hit = at.getWorld().rayTraceBlocks(at.clone().add(0, 0.5, 0), new Vector(0, -1, 0), 40,
                FluidCollisionMode.NEVER, true);
        Location out = hit != null ? hit.getHitPosition().toLocation(at.getWorld()) : at.clone();
        out.setYaw(at.getYaw());
        out.setPitch(0);
        return out;
    }

    /**
     * Tells everybody: a chat line ({@code chatKey}), the words in the middle
     * of the HUD ({@code hudKey}), and a sound, high for good news and low for
     * bad from where each player stands.
     */
    private void announce(String chatKey, String hudKey, Team flag, GamePlayer who, Sound sound, float pitch) {
        last = new Event(hudKey, flag, game.now);
        var m = game.messages();
        String flagColour = flag.color().asHexString();
        game.broadcast(m.component(chatKey,
                "player", who == null ? "" : who.name,
                "colour", who == null || who.paint == null ? "#ffffff" : who.paint.color().asHexString(),
                "flag", flag.display(), "flag_colour", flagColour));
        for (GamePlayer gp : game.active()) {
            Player p = gp.player();
            if (p == null) continue;
            boolean ours = gp.team == flag;
            boolean good = chatKey.startsWith("ctf-returned") ? ours : !ours;
            p.playSound(p.getLocation(), sound, 0.9f, good ? pitch : pitch * 0.7f);
        }
    }
}
