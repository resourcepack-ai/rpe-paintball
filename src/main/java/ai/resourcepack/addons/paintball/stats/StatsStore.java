package ai.resourcepack.addons.paintball.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.logging.Logger;

/**
 * stats.json: every player's totals over every match they finished or left,
 * and how many matches each arena has had. Written when a match ends, which
 * is the only time any of it changes. What PlaceholderAPI reads.
 */
public final class StatsStore {
    /** What a player is ranked by, and the names a placeholder may call it. */
    public enum Stat {
        KILLS(s -> s.kills, "kills"),
        DEATHS(s -> s.deaths, "deaths"),
        WINS(s -> s.wins, "wins", "won"),
        LOSSES(s -> s.losses, "losses", "lost"),
        MATCHES(s -> s.matches, "matches", "played", "games"),
        SHOTS(s -> s.shots, "shots"),
        HITS(s -> s.hits, "hits"),
        CAPTURES(s -> s.captures, "captures", "caps", "flags"),
        ACCURACY(StatLine::accuracy, "accuracy"),
        KD(s -> s.kills / (double) Math.max(1, s.deaths), "kd");

        private final ToDoubleFunction<StatLine> of;
        private final List<String> names;

        Stat(ToDoubleFunction<StatLine> of, String... names) {
            this.of = of;
            this.names = List.of(names);
        }

        public double of(StatLine s) {
            return of.applyAsDouble(s);
        }

        /** As a placeholder shows it: a whole number, a percentage, or kills a death to two places. */
        public String format(StatLine s) {
            return this == KD ? s.kd() : this == ACCURACY ? s.accuracy() + "%" : String.valueOf((long) of(s));
        }

        /** By any of its names, or null. */
        public static Stat parse(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            for (Stat s : values()) if (s.names.contains(n)) return s;
            return null;
        }
    }

    /** One player's line: the name they last played under, and their totals. */
    public static final class Entry {
        public String name;
        public StatLine total = new StatLine();
    }

    private static final class Data {
        Map<String, Entry> players = new LinkedHashMap<>();
        Map<String, Integer> arenas = new LinkedHashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger log;
    private Data data = new Data();
    /** Each leaderboard, best first; worked out when first asked for after a change. */
    private final Map<Stat, List<Entry>> boards = new EnumMap<>(Stat.class);

    public StatsStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        boards.clear();
        data = new Data();
        if (!file.exists()) return;
        try {
            Data read = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Data.class);
            if (read != null) {
                if (read.players == null) read.players = new LinkedHashMap<>();
                if (read.arenas == null) read.arenas = new LinkedHashMap<>();
                read.players.values().removeIf(e -> e == null || e.total == null);
                data = read;
            }
        } catch (IOException | JsonParseException e) {
            log.severe("Could not read stats.json, starting from nothing: " + e.getMessage());
        }
    }

    public void save() {
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Could not save stats.json: " + e.getMessage());
        }
    }

    /**
     * Adds one match to a player's totals: its kills, deaths, shots and hits,
     * and if they were there at the end, the match itself and a win or a loss
     * ({@code won} null for a draw).
     */
    public void record(UUID id, String name, StatLine match, boolean finished, Boolean won) {
        Entry e = data.players.computeIfAbsent(id.toString(), k -> new Entry());
        e.name = name;
        e.total.kills += match.kills;
        e.total.deaths += match.deaths;
        e.total.shots += match.shots;
        e.total.hits += match.hits;
        e.total.captures += match.captures;
        if (finished) {
            e.total.matches++;
            if (Boolean.TRUE.equals(won)) e.total.wins++;
            else if (Boolean.FALSE.equals(won)) e.total.losses++;
        }
        boards.clear();
    }

    /** One more match played on an arena. */
    public void played(String arenaId) {
        data.arenas.merge(arenaId, 1, Integer::sum);
    }

    /** A player's totals, or null if they have never played. */
    public Entry player(UUID id) {
        return data.players.get(id.toString());
    }

    public int plays(String arenaId) {
        return data.arenas.getOrDefault(arenaId, 0);
    }

    /** Everybody who has played, best first by {@code stat}; ties go to whoever got there first. */
    public List<Entry> top(Stat stat) {
        return boards.computeIfAbsent(stat, s -> {
            List<Entry> out = new ArrayList<>(data.players.values());
            out.sort(Comparator.comparingDouble((Entry e) -> s.of(e.total)).reversed());
            return out;
        });
    }
}
