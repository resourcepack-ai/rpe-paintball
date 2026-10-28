package ai.resourcepack.addons.paintball.paint;

import ai.resourcepack.addons.paintball.content.Content;
import ai.resourcepack.addons.paintball.team.Team;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The paint on the map: a flat splat model on an item display, laid on the
 * exact face a ball hit, turned and sized at random. Not persistent, so a crash
 * leaves nothing saved with the chunk, and the map's blocks are never touched.
 * There is a cap; the oldest splat goes first.
 *
 * <p><b>A splat never hangs off the surface it is on.</b> A splat is a flat
 * square, so one laid near the edge of a wall used to stick out past the edge
 * into the air, and two on either side of a corner crossed each other there.
 * So each one measures the flat stretch of surface around where it landed
 * (the faces in line with it that are exposed on the same side) and is moved
 * in from the edges, then made smaller, until its paint fits inside it.
 */
public final class PaintLayer {
    private static final BlockFace[] SIDES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    /** How far from its centre a splat's paint reaches, as a fraction of its size (measured off the textures). */
    private static final double PAINT_RADIUS = 0.47;
    /** How many blocks either way a surface is followed. A splat is at most a couple of blocks across. */
    private static final int REACH = 3;
    /** Smaller than this, a splat is a speck: it is not worth an entity. */
    private static final double SMALLEST = 0.12;

    private final Content content;
    private final int maxDecals;
    private final ArrayDeque<ItemDisplay> decals = new ArrayDeque<>();
    private int layer;

    public PaintLayer(Content content, int maxDecals) {
        this.content = content;
        this.maxDecals = maxDecals;
    }

    /** A ball of {@code team}'s paint hit {@code side} of {@code block} at {@code at}. */
    public void splat(Team team, Block block, BlockFace side, Location at, double size) {
        if (paintable(block)) decal(team, block, at, side, size);
    }

    /**
     * A ball of {@code team}'s paint hit a painting or an item frame at
     * {@code at}: a splat on its front, kept inside its edges.
     */
    public void splatOn(Team team, Hanging hanging, Location at, double size) {
        BoundingBox box = hanging.getBoundingBox();
        BlockFace side = hanging.getFacing();
        Axis normal = Axis.normalOf(side);
        Axis[] axes = Axis.of(side);
        Location spot = at.clone();
        normal.set(spot, outwards(side) ? normal.max(box) : normal.min(box));
        double radius = size * PAINT_RADIUS;
        for (Axis a : axes) radius = Math.min(radius, (a.max(box) - a.min(box)) / 2);
        for (Axis a : axes) a.set(spot, Math.max(a.min(box) + radius, Math.min(a.max(box) - radius, a.get(spot))));
        double s = radius / PAINT_RADIUS;
        if (s >= SMALLEST) place(team, spot, side, s);
    }

    /** Paintings and item frames: what a ball breaks on and paints rather than passing through. */
    public static boolean hangsPaint(org.bukkit.entity.Entity e) {
        return e instanceof Painting || e instanceof ItemFrame;
    }

    /**
     * A grenade went off: splats on the faces it can see around it.
     *
     * <p>Every face is a face of one of a block's real boxes (a fence's post,
     * a slab's top), and it counts only if a line from the grenade reaches it
     * first. Measuring off the whole cube instead put paint on the cube's
     * faces in the air beside a fence post or above a slab.
     */
    public void burst(Team team, Location center, double radius) {
        World w = center.getWorld();
        int r = (int) Math.ceil(radius);
        Vector c = center.toVector();
        List<Spot> seen = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = w.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (!paintable(b)) continue;
                    boolean cube = full(b);
                    for (BoundingBox box : boxes(b)) {
                        for (BlockFace side : SIDES) {
                            // Against a whole cube, nothing of the face shows.
                            if (cube && full(b.getRelative(side))) continue;
                            Vector mid = faceCenter(box, side);
                            if (mid.distance(c) > radius) continue;
                            RayTraceResult hit = sight(w, center, mid);
                            if (hit == null || !b.equals(hit.getHitBlock()) || hit.getHitBlockFace() != side) continue;
                            seen.add(new Spot(b, null, side, hit.getHitPosition()));
                        }
                    }
                }
            }
        }
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(center, radius, radius, radius, PaintLayer::hangsPaint)) {
            Hanging h = (Hanging) e;
            Vector mid = faceCenter(h.getBoundingBox(), h.getFacing());
            if (mid.distance(c) > radius) continue;
            RayTraceResult hit = sight(w, center, mid);
            if (hit != null && h.equals(hit.getHitEntity())) seen.add(new Spot(null, h, h.getFacing(), mid));
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < Math.min(14, seen.size()); i++) {
            Spot pick = seen.get(rnd.nextInt(seen.size()));
            double size = 1.3 + rnd.nextDouble() * 1.1;
            if (pick.hanging != null) {
                splatOn(team, pick.hanging, pick.at.toLocation(w), size);
                continue;
            }
            // Spread round the face of a whole cube; a smaller box's face is its own, and fit() centres on it.
            Vector at = full(pick.block) ? pick.at.clone().add(tangentJitter(pick.side, 0.35)) : pick.at;
            decal(team, pick.block, at.toLocation(w), pick.side, size);
        }
    }

    /** One face a grenade can see: of a block, or of a painting or frame. */
    private record Spot(Block block, Hanging hanging, BlockFace side, Vector at) {
    }

    /** What a line from {@code from} to a little past {@code to} reaches first, paintings and frames included. */
    private static RayTraceResult sight(World w, Location from, Vector to) {
        Vector dir = to.clone().subtract(from.toVector());
        double d = dir.length();
        if (d < 1e-6) return null;
        return w.rayTrace(from, dir.multiply(1 / d), d + 0.05, FluidCollisionMode.NEVER, true, 0, PaintLayer::hangsPaint);
    }

    /** Takes every splat down. */
    public void clear() {
        for (ItemDisplay d : decals) if (d.isValid()) d.remove();
        decals.clear();
    }

    private void decal(Team team, Block block, Location at, BlockFace side, double size) {
        Location fitted = at.clone();
        double s = fit(block, side, fitted, size);
        if (s >= SMALLEST) place(team, fitted, side, s);
    }

    /** Lays a splat of size {@code s} flat on a surface facing {@code side}, centred on {@code fitted}. */
    private void place(Team team, Location fitted, BlockFace side, double s) {
        World w = fitted.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Vector n = side.getDirection();
        // Each splat sits a hair further out than the last, cycling, so overlapping
        // ones layer instead of flickering into each other.
        double lift = 0.004 + (layer++ % 12) * 0.0012;
        Location spot = fitted.add(n.clone().multiply(lift));
        ItemStack item = content.item("splat_" + team.id() + "_" + (1 + rnd.nextInt(3)));
        Quaternionf q = new Quaternionf().rotationTo(0, 0, 1, (float) n.getX(), (float) n.getY(), (float) n.getZ())
                .rotateZ((float) (rnd.nextDouble() * Math.PI * 2));
        float scale = (float) s;
        ItemDisplay d = w.spawn(spot, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setTransformation(new Transformation(new Vector3f(), q, new Vector3f(scale, scale, scale), new Quaternionf()));
            e.setViewRange(0.8f);
            e.addScoreboardTag("paintball");
        });
        decals.addLast(d);
        while (decals.size() > maxDecals) {
            ItemDisplay old = decals.pollFirst();
            if (old != null && old.isValid()) old.remove();
        }
    }

    // ---- fitting a splat to its surface ------------------------------------------------

    /**
     * Moves {@code at} (in place) and returns the size the splat may be so its
     * paint stays on the flat surface around it.
     */
    private static double fit(Block block, BlockFace side, Location at, double size) {
        Axis[] axes = Axis.of(side);
        double radius = size * PAINT_RADIUS;
        double[] lo = new double[2];
        double[] hi = new double[2];
        if (full(block)) {
            for (int i = 0; i < 2; i++) {
                double[] range = extent(block, side, axes[i]);
                lo[i] = range[0];
                hi[i] = range[1];
            }
        } else {
            // Stairs, slabs, anything made of boxes: the face of the box that
            // was hit, less whatever sits on it, and nothing past it. Laid on
            // that face, too: a ball can stop on a fence's collision above
            // the post you see.
            double[] rect = faceOfHit(block, side, at);
            lo[0] = rect[0];
            hi[0] = rect[1];
            lo[1] = rect[2];
            hi[1] = rect[3];
            Axis.normalOf(side).set(at, rect[4]);
        }
        for (int i = 0; i < 2; i++) radius = Math.min(radius, (hi[i] - lo[i]) / 2);
        double[] wanted = {axes[0].get(at), axes[1].get(at)};
        // The scans above follow the two lines through the block that was hit;
        // the corners between them (an L-shaped edge, a hole) are checked here,
        // and the splat shrinks until everything under it is surface.
        for (int tries = 0; tries < 8; tries++) {
            for (int i = 0; i < 2; i++) {
                axes[i].set(at, Math.max(lo[i] + radius, Math.min(hi[i] - radius, wanted[i])));
            }
            if (!full(block) || covered(block, side, axes, at, radius)) break;
            radius *= 0.8;
        }
        return radius / PAINT_RADIUS;
    }

    /** Whether every block under the square {@code radius} round {@code at} is flat, open surface. */
    private static boolean covered(Block block, BlockFace side, Axis[] axes, Location at, double radius) {
        int u0 = (int) Math.floor(axes[0].get(at) - radius);
        int u1 = (int) Math.floor(axes[0].get(at) + radius - 1e-6);
        int v0 = (int) Math.floor(axes[1].get(at) - radius);
        int v1 = (int) Math.floor(axes[1].get(at) + radius - 1e-6);
        int bu = axes[0].coord(block);
        int bv = axes[1].coord(block);
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                if (u == bu && v == bv) continue;
                Block b = axes[1].step(axes[0].step(block, u - bu), v - bv);
                if (!full(b) || !paintable(b) || !b.getRelative(side).isPassable()) return false;
            }
        }
        return true;
    }

    /**
     * The stretch of flat, exposed surface through {@code block}'s {@code side}
     * along one axis of it: the block's own face, then each neighbour in line
     * whose face is level with it and open on the same side, up to
     * {@link #REACH} either way. A block that is not a full cube is its own face
     * and nothing more, since its neighbours' faces are not level with it.
     */
    private static double[] extent(Block block, BlockFace side, Axis axis) {
        BoundingBox box = block.getBoundingBox();
        double lo = axis.min(box);
        double hi = axis.max(box);
        for (int dir = -1; dir <= 1; dir += 2) {
            for (int k = 1; k <= REACH; k++) {
                Block next = axis.step(block, dir * k);
                if (!full(next) || !paintable(next) || !next.getRelative(side).isPassable()) break;
                if (dir < 0) lo -= 1;
                else hi += 1;
            }
        }
        return new double[]{lo, hi};
    }

    /**
     * The face a ball landed on, on a block made of more than one box, as
     * {lo, hi} along each of the face's two axes and then the face's plane:
     * the box whose face is nearest the hit, trimmed back from any box of the
     * same block sitting on that face (a stair's upper step on its lower one),
     * to the side the hit is on.
     */
    private static double[] faceOfHit(Block block, BlockFace side, Location at) {
        Axis[] axes = Axis.of(side);
        Axis normal = Axis.normalOf(side);
        boolean outwards = outwards(side);
        List<BoundingBox> boxes = boxes(block);
        BoundingBox hit = null;
        double nearest = Double.MAX_VALUE;
        for (BoundingBox b : boxes) {
            double plane = outwards ? normal.max(b) : normal.min(b);
            double d = Math.abs(plane - normal.get(at)) + outside(axes[0], b, at) + outside(axes[1], b, at);
            if (d < nearest) {
                nearest = d;
                hit = b;
            }
        }
        if (hit == null) hit = block.getBoundingBox();
        double plane = outwards ? normal.max(hit) : normal.min(hit);
        double lo0 = axes[0].min(hit), hi0 = axes[0].max(hit), lo1 = axes[1].min(hit), hi1 = axes[1].max(hit);
        double p0 = axes[0].get(at), p1 = axes[1].get(at);
        for (BoundingBox b : boxes) {
            if (b == hit) continue;
            double touches = outwards ? normal.min(b) : normal.max(b);
            if (Math.abs(touches - plane) > 1e-3) continue;
            double bl0 = axes[0].min(b), bh0 = axes[0].max(b), bl1 = axes[1].min(b), bh1 = axes[1].max(b);
            if (bh0 <= lo0 || bl0 >= hi0 || bh1 <= lo1 || bl1 >= hi1) continue;
            if (bl0 <= lo0 + 1e-3 && bh0 >= hi0 - 1e-3) {
                if (p1 < bl1) hi1 = Math.min(hi1, bl1);
                else lo1 = Math.max(lo1, bh1);
            } else if (bl1 <= lo1 + 1e-3 && bh1 >= hi1 - 1e-3) {
                if (p0 < bl0) hi0 = Math.min(hi0, bl0);
                else lo0 = Math.max(lo0, bh0);
            }
        }
        return new double[]{lo0, Math.max(lo0, hi0), lo1, Math.max(lo1, hi1), plane};
    }

    /** How far {@code at} is outside {@code b} along one axis; 0 inside. */
    private static double outside(Axis axis, BoundingBox b, Location at) {
        double v = axis.get(at);
        return Math.max(0, Math.max(axis.min(b) - v, v - axis.max(b)));
    }

    /**
     * A block's collision boxes, in world coordinates, cut down to the block
     * you see: a fence's or a wall's collision reaches half a block above its
     * post, and paint there would hang in the air.
     */
    private static List<BoundingBox> boxes(Block block) {
        BoundingBox seen = block.getBoundingBox();
        List<BoundingBox> out = new ArrayList<>();
        for (BoundingBox b : block.getCollisionShape().getBoundingBoxes()) {
            // Relative to the block, as Bukkit hands them out; shifted unless already placed.
            BoundingBox placed = b.getMaxX() <= 1.5 && b.getMaxY() <= 1.5 && b.getMaxZ() <= 1.5
                    ? b.clone().shift(block.getX(), block.getY(), block.getZ()) : b.clone();
            if (seen.getVolume() > 0 && placed.overlaps(seen)) placed.intersection(seen);
            out.add(placed);
        }
        return out;
    }

    /** Whether {@code side} points along its axis rather than against it. */
    private static boolean outwards(BlockFace side) {
        return side == BlockFace.UP || side == BlockFace.SOUTH || side == BlockFace.EAST;
    }

    /** One whole cube of collision: what a splat may spread across into its neighbours. */
    private static boolean full(Block b) {
        var shape = b.getCollisionShape().getBoundingBoxes();
        if (shape.size() != 1) return false;
        BoundingBox box = shape.iterator().next();
        return box.getWidthX() > 0.999 && box.getHeight() > 0.999 && box.getWidthZ() > 0.999;
    }

    /** One of the two directions across a face. */
    private enum Axis {
        X, Y, Z;

        static Axis normalOf(BlockFace side) {
            return switch (side) {
                case UP, DOWN -> Y;
                case NORTH, SOUTH -> Z;
                default -> X;
            };
        }

        static Axis[] of(BlockFace side) {
            return switch (side) {
                case UP, DOWN -> new Axis[]{X, Z};
                case NORTH, SOUTH -> new Axis[]{X, Y};
                default -> new Axis[]{Z, Y};
            };
        }

        double get(Location l) {
            return this == X ? l.getX() : this == Y ? l.getY() : l.getZ();
        }

        void set(Location l, double v) {
            if (this == X) l.setX(v);
            else if (this == Y) l.setY(v);
            else l.setZ(v);
        }

        double min(BoundingBox b) {
            return this == X ? b.getMinX() : this == Y ? b.getMinY() : b.getMinZ();
        }

        double max(BoundingBox b) {
            return this == X ? b.getMaxX() : this == Y ? b.getMaxY() : b.getMaxZ();
        }

        int coord(Block b) {
            return this == X ? b.getX() : this == Y ? b.getY() : b.getZ();
        }

        Block step(Block b, int by) {
            return b.getRelative(this == X ? by : 0, this == Y ? by : 0, this == Z ? by : 0);
        }
    }

    /**
     * Whether paint stays on a block: anything a ball stops on. Not only what
     * the game calls solid, which leaves out ladders: a ball breaks on a ladder
     * (it has a collision box), so a ladder that took no paint swallowed it.
     */
    private static boolean paintable(Block b) {
        Material m = b.getType();
        if (m == Material.BARRIER || m == Material.LIGHT || m.name().endsWith("_SIGN")) return false;
        return m.isSolid() || !b.getCollisionShape().getBoundingBoxes().isEmpty();
    }

    /** The middle of a box's face on {@code side}. */
    private static Vector faceCenter(BoundingBox b, BlockFace side) {
        Vector n = side.getDirection();
        return b.getCenter().add(new Vector(n.getX() * b.getWidthX(), n.getY() * b.getHeight(), n.getZ() * b.getWidthZ()).multiply(0.5));
    }

    private static Vector tangentJitter(BlockFace side, double amount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double a = (rnd.nextDouble() * 2 - 1) * amount;
        double b = (rnd.nextDouble() * 2 - 1) * amount;
        return switch (side) {
            case UP, DOWN -> new Vector(a, 0, b);
            case NORTH, SOUTH -> new Vector(a, b, 0);
            default -> new Vector(0, b, a);
        };
    }
}
