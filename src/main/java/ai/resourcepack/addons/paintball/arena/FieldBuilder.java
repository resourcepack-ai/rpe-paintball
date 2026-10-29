package ai.resourcepack.addons.paintball.arena;

import ai.resourcepack.addons.paintball.team.Team;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.util.BoundingBox;

/**
 * Builds a speedball field where an op stands and sets an arena up on it:
 * a pale floor (paint shows on it), a netted fence, a base at each end, and
 * inflatable-style bunkers placed with point symmetry, so both teams play the
 * same field. Above the middle, a glass box to wait in that looks down on it.
 *
 * <p>Coordinates below are relative to the centre of the floor: x across
 * (-15..15), z along (-28..28), y up from the floor.
 */
public final class FieldBuilder {
    private static final int HALF_X = 15;
    private static final int HALF_Z = 28;
    private static final int HEIGHT = 11;

    private final World w;
    private final int cx;
    private final int cy;
    private final int cz;

    public FieldBuilder(Location centre) {
        this.w = centre.getWorld();
        this.cx = centre.getBlockX();
        this.cy = centre.getBlockY();
        this.cz = centre.getBlockZ();
    }

    private void set(int x, int y, int z, Material m) {
        w.getBlockAt(cx + x, cy + y, cz + z).setType(m, false);
    }

    private void set(int x, int y, int z, BlockData data) {
        w.getBlockAt(cx + x, cy + y, cz + z).setBlockData(data, false);
    }

    /** Sets a block and its mirror through the centre (x,z) -> (-x,-z). */
    private void sym(int x, int y, int z, Material m) {
        set(x, y, z, m);
        set(-x, y, -z, m);
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, m);
    }

    private void symBox(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
        box(x0, y0, z0, x1, y1, z1, m);
        box(-x0, y0, -z0, -x1, y1, -z1, m);
    }

    public void build(Arena arena, ArenaSetup setup) {
        // Clear the air above, lay the floor and a foundation under it.
        box(-HALF_X, 0, -HALF_Z, HALF_X, HEIGHT, HALF_Z, Material.AIR);
        box(-HALF_X, -2, -HALF_Z, HALF_X, -2, HALF_Z, Material.STONE);
        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int z = -HALF_Z; z <= HALF_Z; z++) {
                boolean line = z == 0 || Math.abs(z) == 20 || Math.abs(x) == 13;
                boolean check = ((x + 100) / 3 + (z + 100) / 3) % 2 == 0;
                set(x, -1, z, line ? Material.WHITE_CONCRETE : check ? Material.LIGHT_GRAY_CONCRETE : Material.SMOOTH_STONE);
            }
        }
        // The fence: a dark kerb and netting to head height, placed with physics so the panes join up.
        for (int x = -HALF_X; x <= HALF_X; x++) {
            for (int z = -HALF_Z; z <= HALF_Z; z++) {
                if (Math.abs(x) != HALF_X && Math.abs(z) != HALF_Z) continue;
                set(x, 0, z, Material.GRAY_CONCRETE);
                for (int y = 1; y <= 4; y++) w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.BLACK_STAINED_GLASS_PANE, true);
            }
        }
        // Invisible light over the whole field, so a night match is still a match.
        for (int x = -12; x <= 12; x += 6)
            for (int z = -24; z <= 24; z += 6) set(x, 7, z, Material.LIGHT);

        // Bases: a team-coloured pad at each end with a low wall behind it.
        for (Team t : Team.values()) {
            int s = t == Team.RED ? -1 : 1;
            box(-6, -1, s * 26, 6, -1, s * 22, t.block());
            box(-6, 0, s * 27, 6, 1, s * 27, t.block());
            box(-7, 0, s * 27, -7, 2, s * 27, Material.WHITE_CONCRETE);
            box(7, 0, s * 27, 7, 2, s * 27, Material.WHITE_CONCRETE);
        }

        // Bunkers on the red half; each is mirrored onto the blue half through the centre.
        Material shell = Material.WHITE_CONCRETE;
        Material trim = Material.YELLOW_CONCRETE;
        // Base cover, just in front of each base.
        symBox(-2, 0, -19, 2, 1, -19, shell);
        symBox(-2, 2, -19, 2, 2, -19, trim);
        // Snakes: long low walls down the left side.
        symBox(-12, 0, -16, -12, 0, -6, shell);
        symBox(-11, 0, -16, -11, 0, -16, trim);
        // Temples: tall wide cover, right of centre.
        symBox(7, 0, -14, 9, 2, -14, shell);
        symBox(7, 3, -14, 9, 3, -14, trim);
        // Cans: 2x2 pillars.
        symBox(-4, 0, -12, -3, 2, -11, shell);
        symBox(-4, 3, -12, -3, 3, -11, trim);
        symBox(11, 0, -8, 12, 2, -7, shell);
        // Mini walls.
        symBox(1, 0, -8, 3, 1, -8, shell);
        symBox(-8, 0, -9, -6, 1, -9, shell);
        // Doritos: little ridges of stairs, low enough to dive behind.
        dorito(-7, -16, BlockFace.SOUTH);
        dorito(7, 16, BlockFace.NORTH);
        dorito(5, -4, BlockFace.SOUTH);
        dorito(-5, 4, BlockFace.NORTH);
        // The middle: a cake, and a can on each wing.
        box(-1, 0, -1, 1, 1, 1, shell);
        box(-1, 2, -1, 1, 2, 1, trim);
        symBox(-10, 0, 0, -9, 2, 0, shell);

        // The waiting box above the middle: glass floor, invisible walls.
        int wy = HEIGHT + 2;
        box(-4, wy, -4, 4, wy, 4, Material.LIGHT_BLUE_STAINED_GLASS);
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++)
                if (Math.abs(x) == 5 || Math.abs(z) == 5) box(x, wy + 1, z, x, wy + 3, z, Material.BARRIER);
        box(-4, wy + 4, -4, 4, wy + 4, 4, Material.BARRIER);

        // Set the arena up on what was just built.
        Location waiting = new Location(w, cx + 0.5, cy + wy + 1, cz + 0.5, 0, 30);
        arena.waiting(waiting);
        for (Team t : Team.values()) {
            arena.clearSpawns(t);
            int s = t == Team.RED ? -1 : 1;
            float yaw = t == Team.RED ? 0 : 180; // red looks toward +z, blue toward -z
            for (int i = -2; i <= 2; i++) {
                arena.addSpawn(t, new Location(w, cx + i * 2 + 0.5, cy, cz + s * 24 + 0.5, yaw, 0));
            }
            // Capture the flag's flag: at the back of the base, in front of its wall.
            arena.flag(t, new Location(w, cx + 0.5, cy, cz + s * 26 + 0.5, yaw, 0));
        }
        arena.enabled(true);
        setup.bounds(arena, new BoundingBox(cx - HALF_X + 1, cy - 1, cz - HALF_Z + 1, cx + HALF_X, cy + HEIGHT, cz + HALF_Z));
    }

    private void dorito(int x, int z, BlockFace facing) {
        for (int dx = -1; dx <= 1; dx++) {
            Stairs st = (Stairs) Material.QUARTZ_STAIRS.createBlockData();
            st.setFacing(facing);
            set(x + dx, 0, z, st);
            Stairs back = (Stairs) Material.QUARTZ_STAIRS.createBlockData();
            back.setFacing(facing.getOppositeFace());
            set(x + dx, 0, z + (facing == BlockFace.SOUTH ? -1 : 1), back);
        }
        Block top = w.getBlockAt(cx + x, cy + 1, cz + z);
        top.setType(Material.QUARTZ_SLAB, false);
    }
}
