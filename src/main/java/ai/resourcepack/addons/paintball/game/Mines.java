package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.team.Paint;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Paint mines: each player gets {@code mine.per-match} a match, set on the
 * ground with a right click. Once armed, the first enemy to step on one sets
 * it off, and it splats every enemy within {@code mine.radius} that it can
 * see, as a grenade does. A mine is the grenade's model squashed flat, in its
 * team's colour.
 */
final class Mines {
    /** How close an enemy's feet have to come, across and up, to set one off. */
    private static final double TRIGGER_ACROSS = 0.8;
    private static final double TRIGGER_UP = 1.0;

    private static final class Mine {
        final GamePlayer owner;
        final Paint paint;
        final Location at;
        final long armedAt;
        final ItemDisplay display;

        Mine(GamePlayer owner, Paint paint, Location at, long armedAt, ItemDisplay display) {
            this.owner = owner;
            this.paint = paint;
            this.at = at;
            this.armedAt = armedAt;
            this.display = display;
        }
    }

    private final Game game;
    private final List<Mine> mines = new ArrayList<>();

    Mines(Game game) {
        this.game = game;
    }

    /**
     * {@code gp} sets a mine on top of {@code block}, if it is the top of
     * something with room above it and they have one left. Answers whether
     * it went down.
     */
    boolean place(GamePlayer gp, Player p, Block block, BlockFace face) {
        if (gp.minesLeft <= 0 || block == null || face != BlockFace.UP) return false;
        if (block.isPassable() || !block.getRelative(BlockFace.UP).isPassable()) return false;
        Location at = new Location(block.getWorld(), block.getX() + 0.5, block.getBoundingBox().getMaxY(), block.getZ() + 0.5);
        if (!game.arena.contains(at)) return false;
        Settings s = game.settings();
        float spin = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2);
        ItemDisplay display = at.getWorld().spawn(at.clone().add(0, 0.06, 0), ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(game.content.item("mine_" + gp.paint.id()));
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationY(spin),
                    new Vector3f(0.75f, 0.28f, 0.75f), new Quaternionf()));
            e.addScoreboardTag("paintball");
        });
        mines.add(new Mine(gp, gp.paint, at, game.now + s.mineArmTicks, display));
        gp.minesLeft--;
        at.getWorld().playSound(at, Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.8f, 1.4f);
        return true;
    }

    /** Once a tick while live: anybody on the other side standing on an armed mine sets it off. */
    void tick() {
        for (Mine m : new ArrayList<>(mines)) {
            if (!mines.contains(m) || game.now < m.armedAt) continue;
            if (game.now == m.armedAt) m.at.getWorld().playSound(m.at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
            for (GamePlayer gv : game.active()) {
                if (!gv.alive || !game.enemies(m.owner, gv)) continue;
                Player vp = gv.player();
                if (vp == null || !vp.getWorld().equals(m.at.getWorld())) continue;
                Location feet = vp.getLocation();
                double dy = feet.getY() - m.at.getY();
                if (dy < -0.2 || dy > TRIGGER_UP) continue;
                if (Math.hypot(feet.getX() - m.at.getX(), feet.getZ() - m.at.getZ()) > TRIGGER_ACROSS) continue;
                explode(m);
                break;
            }
            // A hit can end the match, which clears every mine.
            if (game.phase != Game.Phase.LIVE) return;
        }
    }

    /** Bang: the paint, the sound, and a splat for every enemy in range it can see. */
    private void explode(Mine m) {
        mines.remove(m);
        if (m.display.isValid()) m.display.remove();
        World w = m.at.getWorld();
        Location center = m.at.clone().add(0, 0.4, 0);
        game.fx.splat(center, m.paint, true);
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
        game.paint.burst(m.paint, center, 3.2);
        double r = game.settings().mineRadius;
        for (GamePlayer gv : new ArrayList<>(game.players.values())) {
            if (gv.left || !gv.alive || !game.enemies(m.owner, gv)) continue;
            Player vp = gv.player();
            if (vp == null || !vp.getWorld().equals(w)) continue;
            Location body = vp.getLocation().add(0, 1, 0);
            if (body.distance(center) > r) continue;
            Vector to = body.toVector().subtract(center.toVector());
            RayTraceResult wall = w.rayTraceBlocks(center, to.clone().normalize(), Math.max(0.01, to.length()), FluidCollisionMode.NEVER, true);
            if (wall != null) continue;
            game.hit(m.owner, gv, body, Weapon.MINE);
        }
    }

    /** Every mine up: between matches and at the end. */
    void clear() {
        for (Mine m : mines) if (m.display.isValid()) m.display.remove();
        mines.clear();
    }
}
