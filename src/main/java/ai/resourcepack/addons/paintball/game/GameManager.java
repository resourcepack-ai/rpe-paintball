package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.Text;
import ai.resourcepack.addons.paintball.arena.Arena;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One {@link Game} per arena, which player is in which, and the tick that drives them all. */
public final class GameManager {
    private final PaintballPlugin plugin;
    private final Map<String, Game> games = new LinkedHashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();

    public GameManager(PaintballPlugin plugin) {
        this.plugin = plugin;
    }

    public Game game(Arena arena) {
        return games.computeIfAbsent(arena.id(), id -> new Game(plugin, this, arena));
    }

    public Game existing(String arenaId) {
        return games.get(arenaId);
    }

    public Game of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public boolean playing(Player p) {
        return byPlayer.containsKey(p.getUniqueId());
    }

    public Collection<Game> games() {
        return games.values();
    }

    /** The arena people play on when nobody names one: the first open one that is set up. */
    public Arena defaultArena() {
        for (Arena a : plugin.arenas().enabled()) if (a.missing().isEmpty()) return a;
        return null;
    }

    public boolean join(Player p, Arena arena) {
        if (playing(p)) {
            Text.send(p, plugin.messages().component("already-in"));
            return false;
        }
        if (arena == null) {
            Text.send(p, plugin.messages().component("no-arena"));
            return false;
        }
        if (!arena.missing().isEmpty()) {
            Text.send(p, plugin.messages().component("arena-not-ready", "arena", arena.name(), "missing", String.join(", ", arena.missing())));
            return false;
        }
        Game g = game(arena);
        byPlayer.put(p.getUniqueId(), g);
        g.join(p);
        return true;
    }

    public void leave(Player p) {
        Game g = byPlayer.remove(p.getUniqueId());
        if (g != null) g.leave(p);
    }

    /** Forgets a player without telling the game: the game is sending them home itself. */
    void forget(Player p) {
        byPlayer.remove(p.getUniqueId());
    }

    void reset(Arena arena) {
        Game g = games.remove(arena.id());
        if (g != null) byPlayer.values().removeIf(x -> x == g);
    }

    public void tick() {
        for (Game g : new ArrayList<>(games.values())) {
            try {
                g.tick();
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Paintball on " + g.arena().id() + " failed a tick: " + e);
                e.printStackTrace();
            }
        }
    }

    /** Server going down or plugin reloading: everybody home. */
    public void stopAll() {
        for (Game g : new ArrayList<>(games.values())) g.close();
        games.clear();
        byPlayer.clear();
    }
}
