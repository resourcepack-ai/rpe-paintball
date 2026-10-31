package ai.resourcepack.addons.paintball.arena;

import ai.resourcepack.addons.paintball.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One map: where players wait, where each team starts, the box the match is
 * played in. Positions are kept as numbers
 * and a world name, so an arena whose world is not loaded yet still loads.
 */
public final class Arena {
    public static final int MAX_SPAWNS = 5;

    private final String id;
    private String name;
    private String world;
    private Point waiting;
    private final Map<Team, List<Point>> spawns = new EnumMap<>(Team.class);
    /** Spawns for anybody: a free for all's, and a team's while it has none of its own. */
    private final List<Point> open = new ArrayList<>();
    /** Where each team's flag stands in capture the flag. */
    private final Map<Team, Point> flags = new EnumMap<>(Team.class);
    private BoundingBox bounds;
    private boolean enabled;
    /** config.yml's game options this arena sets for itself, by path ("match-seconds", "marker.speed"). */
    private final Map<String, Object> settings = new TreeMap<>();

    public Arena(String id, String name, String world) {
        this.id = id;
        this.name = name;
        this.world = world;
        for (Team t : Team.values()) spawns.put(t, new ArrayList<>());
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String worldName() {
        return world;
    }

    public World world() {
        return Bukkit.getWorld(world);
    }

    public Location waiting() {
        return waiting == null ? null : waiting.at(world());
    }

    public void waiting(Location at) {
        this.world = at.getWorld().getName();
        this.waiting = Point.of(at);
    }

    /** {@code team}'s spawns, or the spawns for anybody while it has none of its own; null for the spawns for anybody alone. */
    public List<Location> spawns(Team team) {
        World w = world();
        List<Location> out = new ArrayList<>();
        List<Point> list = team == null || spawns.get(team).isEmpty() ? open : spawns.get(team);
        for (Point p : list) out.add(p.at(w));
        return out;
    }

    /** Every spawn on the field, for a free for all: the spawns for anybody and both teams'. */
    public List<Location> allSpawns() {
        World w = world();
        List<Location> out = new ArrayList<>();
        for (Point p : open) out.add(p.at(w));
        for (Team t : Team.values()) for (Point p : spawns.get(t)) out.add(p.at(w));
        return out;
    }

    /** How many spawns {@code team} has of its own; null for the spawns for anybody. */
    public int spawnCount(Team team) {
        return team == null ? open.size() : spawns.get(team).size();
    }

    /** How many a team, or the spawns for anybody, may have. */
    public static int maxSpawns(Team team) {
        return team == null ? MAX_SPAWNS * 2 : MAX_SPAWNS;
    }

    /** Adds a spawn, for {@code team} or with null for anybody; past the most the oldest goes. */
    public void addSpawn(Team team, Location at) {
        List<Point> list = team == null ? open : spawns.get(team);
        if (list.size() >= maxSpawns(team)) list.remove(0);
        list.add(Point.of(at));
    }

    public void clearSpawns(Team team) {
        (team == null ? open : spawns.get(team)).clear();
    }

    /** Where {@code team}'s flag stands, or null when it has not been put anywhere. */
    public Location flag(Team team) {
        Point p = flags.get(team);
        return p == null ? null : p.at(world());
    }

    public void flag(Team team, Location at) {
        if (at == null) flags.remove(team);
        else flags.put(team, Point.of(at));
    }

    /** Whether both flags have a place, which capture the flag needs. */
    public boolean hasFlags() {
        for (Team t : Team.values()) if (!flags.containsKey(t)) return false;
        return true;
    }

    public BoundingBox bounds() {
        return bounds;
    }

    public void bounds(BoundingBox box) {
        this.bounds = box;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean on) {
        this.enabled = on;
    }

    /** The options this arena sets for itself; everything else is config.yml's. */
    public Map<String, Object> settings() {
        return Collections.unmodifiableMap(settings);
    }

    /** Sets one of config.yml's options for this arena alone, or with {@code null} goes back to config.yml's. */
    public void setting(String path, Object value) {
        if (value == null) settings.remove(path);
        else settings.put(path, value);
    }

    /** What the arena still needs before it can be enabled. Empty when it is ready. */
    public List<String> missing() {
        List<String> out = new ArrayList<>();
        if (world() == null) out.add("its world (" + world + ") is not loaded");
        if (waiting == null) out.add("a waiting spot");
        // A team with no spawns of its own uses the spawns for anybody.
        if (open.isEmpty()) for (Team t : Team.values()) if (spawns.get(t).isEmpty()) out.add("a " + t.display().toLowerCase() + " spawn");
        if (bounds == null) out.add("bounds");
        return out;
    }

    public boolean contains(Location at) {
        return bounds == null || (at.getWorld() != null && at.getWorld().getName().equals(world)
                && bounds.contains(at.toVector()));
    }

    // ---- storage -----------------------------------------------------------------

    void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("world", world);
        s.set("enabled", enabled);
        s.set("waiting", waiting == null ? null : waiting.toList());
        for (Team t : Team.values()) {
            List<List<Double>> out = new ArrayList<>();
            for (Point p : spawns.get(t)) out.add(p.toList());
            s.set("spawns." + t.id(), out);
            Point flag = flags.get(t);
            s.set("flags." + t.id(), flag == null ? null : flag.toList());
        }
        List<List<Double>> anybody = new ArrayList<>();
        for (Point p : open) anybody.add(p.toList());
        s.set("spawns.any", anybody.isEmpty() ? null : anybody);
        if (bounds != null) {
            s.set("bounds.min", List.of(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()));
            s.set("bounds.max", List.of(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()));
        } else {
            s.set("bounds", null);
        }
        if (settings.isEmpty()) {
            s.set("settings", null);
        } else {
            ConfigurationSection own = s.createSection("settings");
            for (Map.Entry<String, Object> e : settings.entrySet()) own.set(e.getKey(), e.getValue());
        }
    }

    static Arena load(String id, ConfigurationSection s) {
        Arena a = new Arena(id, s.getString("name", id), s.getString("world", "world"));
        a.enabled = s.getBoolean("enabled", false);
        a.waiting = Point.fromList(s.getList("waiting"));
        for (Team t : Team.values()) {
            List<?> raw = s.getList("spawns." + t.id());
            if (raw == null) continue;
            for (Object o : raw) {
                Point p = Point.fromList(o instanceof List<?> l ? l : null);
                if (p != null) a.spawns.get(t).add(p);
            }
        }
        List<?> anybody = s.getList("spawns.any");
        if (anybody != null) {
            for (Object o : anybody) {
                Point p = Point.fromList(o instanceof List<?> l ? l : null);
                if (p != null) a.open.add(p);
            }
        }
        for (Team t : Team.values()) {
            Point flag = Point.fromList(s.getList("flags." + t.id()));
            if (flag != null) a.flags.put(t, flag);
        }
        List<?> min = s.getList("bounds.min");
        List<?> max = s.getList("bounds.max");
        if (min != null && max != null && min.size() == 3 && max.size() == 3) {
            a.bounds = new BoundingBox(num(min.get(0)), num(min.get(1)), num(min.get(2)),
                    num(max.get(0)), num(max.get(1)), num(max.get(2)));
        }
        ConfigurationSection own = s.getConfigurationSection("settings");
        if (own != null) {
            for (Map.Entry<String, Object> e : own.getValues(true).entrySet()) {
                if (!(e.getValue() instanceof ConfigurationSection)) a.settings.put(e.getKey(), e.getValue());
            }
        }
        return a;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    /** A position and a facing, without a world. */
    record Point(double x, double y, double z, float yaw, float pitch) {
        static Point of(Location l) {
            return new Point(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        Location at(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }

        List<Double> toList() {
            return List.of(round(x), round(y), round(z), round(yaw), round(pitch));
        }

        static Point fromList(List<?> l) {
            if (l == null || l.size() < 3) return null;
            return new Point(num(l.get(0)), num(l.get(1)), num(l.get(2)),
                    l.size() > 3 ? (float) num(l.get(3)) : 0f, l.size() > 4 ? (float) num(l.get(4)) : 0f);
        }

        private static double round(double d) {
            return Math.round(d * 100) / 100.0;
        }
    }
}
