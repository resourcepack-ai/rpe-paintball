package ai.resourcepack.addons.paintball.game;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * What a splatted player watches until they are back: whoever splatted them,
 * from a camera that starts on their own side of the killer and slowly circles
 * round, looking at them.
 *
 * <p>The camera is an empty item display the victim spectates. A display
 * because the client slides it between the places it is sent to
 * ({@code teleportDuration}), so the shot moves smoothly; spectating because
 * it is the only way the server has of putting somebody's eyes somewhere. A
 * spectator who presses shift stops spectating, so the camera is put back
 * every tick: while you are out, you watch.
 */
final class KillCam {
    /** How far from the killer the camera stands, and how far above their chest. */
    private static final double DISTANCE = 4.5;
    private static final double RISE = 1.2;
    /** How far round it goes each tick, in radians: about a quarter turn in three seconds. */
    private static final double TURN = 0.025;
    /**
     * Ticks between the camera appearing and the victim being put behind it.
     * A client told to look through an entity it has not been sent yet ignores
     * it, and the server would go on believing it had.
     */
    private static final int SETTLE = 2;

    private final Game game;

    KillCam(Game game) {
        this.game = game;
    }

    /** {@code victim} was splatted by {@code killer}: the camera goes up beside the killer, on the victim's side. */
    void start(GamePlayer victim, Player vp, Player killer) {
        stop(victim, vp);
        Location focus = focus(killer);
        Location from = vp.getLocation();
        victim.camAngle = Math.atan2(from.getX() - focus.getX(), from.getZ() - focus.getZ());
        victim.camOn = killer.getUniqueId();
        victim.camFrom = game.now;
        Location at = place(focus, victim.camAngle);
        victim.camera = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setTeleportDuration(2);
            e.addScoreboardTag("paintball");
        });
        // Over there already, so the client has the camera before it is asked to look through it.
        vp.teleport(at);
    }

    /** Once a tick while they are out: round a little further, and still looking through it. */
    void tick(GamePlayer gp, Player p) {
        if (gp.camera == null) return;
        Player killer = gp.camOn == null ? null : Bukkit.getPlayer(gp.camOn);
        if (killer == null || !gp.camera.isValid() || !killer.getWorld().equals(gp.camera.getWorld())) {
            // Gone: the rest of the wait is the free spectator it used to be.
            stop(gp, p);
            return;
        }
        long t = game.now - gp.camFrom;
        gp.camera.teleport(place(focus(killer), gp.camAngle + t * TURN));
        Entity watching = p.getSpectatorTarget();
        if (t >= SETTLE && (watching == null || !watching.equals(gp.camera))) p.setSpectatorTarget(gp.camera);
    }

    /** Back to their own eyes, and the camera goes. Safe to call when there is none. */
    void stop(GamePlayer gp, Player p) {
        if (gp.camera == null) return;
        Entity watching = p == null ? null : p.getSpectatorTarget();
        if (watching != null && watching.equals(gp.camera)) p.setSpectatorTarget(null);
        if (gp.camera.isValid()) gp.camera.remove();
        gp.camera = null;
        gp.camOn = null;
    }

    /** What the camera looks at: the killer's chest. */
    private static Location focus(Player killer) {
        return killer.getLocation().add(0, 1.2, 0);
    }

    /** Where the camera stands at {@code angle} round {@code focus}, looking at it, pulled in short of any wall between. */
    private static Location place(Location focus, double angle) {
        Vector out = new Vector(Math.sin(angle) * DISTANCE, RISE, Math.cos(angle) * DISTANCE);
        double length = out.length();
        Vector dir = out.multiply(1 / length);
        RayTraceResult wall = focus.getWorld().rayTraceBlocks(focus, dir, length, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? length : Math.max(0.6, wall.getHitPosition().distance(focus.toVector()) - 0.3);
        Location at = focus.clone().add(dir.clone().multiply(reach));
        at.setDirection(dir.multiply(-1));
        return at;
    }
}
