package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Messages;
import ai.resourcepack.addons.paintball.team.Paint;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import ai.resourcepack.engine.api.Overlays;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The player list (Tab) as the match's scoreboard ({@code tab.scoreboard}):
 * under the header's words, a tile for every player in their paint with their
 * name, kills and deaths. A team match has a column a team, red on the left;
 * a free for all is everybody best first, with their place; the lobby is
 * whoever is waiting, in the colour of the side or the paint they picked.
 *
 * <p>The game draws a player list as columns of names, and the only free text
 * in it is the header and the footer, so the tiles are header lines. A tile is
 * a glyph (tools/hud_art.py's {@code tab_*}) hung from five pixels above its
 * line, so the name written on that line sits in its middle, and a blank line
 * after each row leaves a pixel between it and the next. The game centres each
 * line of a header on that line's own width, so every row is padded with the
 * engine's spaces to the same width, measured as the engine measures, and the
 * tiles stack into a grid.
 *
 * <p>On Paper the match's players are also taken off the list under the tiles
 * ({@code tab.hide-players}), where they would only be said twice, and put
 * back when they leave. Spigot has no way to, so there they stay.
 */
final class TabList {
    /** tools/hud_art.py's tile: how wide it is drawn. */
    static final int TILE_W = 150;
    /** The room between a tile's edges and what is written on it. */
    private static final int PAD = 6;
    /** Between the two columns. */
    private static final int GAP = 6;
    /** The most rows in a column; past two full columns the rest is a line saying how many. */
    static final int ROWS = 8;

    /** Paper's way to take a player off somebody's list and put them back; null on Spigot. */
    private static final Method UNLIST = playerMethod("unlistPlayer");
    private static final Method LIST = playerMethod("listPlayer");

    private final Game game;
    private final Hud hud;
    /** Who this has taken off each viewer's list, so that only those are put back. */
    private final Map<UUID, Set<UUID>> unlisted = new HashMap<>();
    /** Each message as written with its values since the last refresh: every viewer's grid says the same words. */
    private final Map<String, String> words = new HashMap<>();

    TabList(Game game, Hud hud) {
        this.game = game;
        this.hud = hud;
    }

    private static Method playerMethod(String name) {
        try {
            return Player.class.getMethod(name, Player.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Overlays overlays() {
        return game.plugin.content().engine().overlays();
    }

    private Messages messages() {
        return game.plugin.messages();
    }

    private String icon(String path) {
        return game.content.icon(path, "");
    }

    /** Whether the pack has the tiles: without them the grid would be names in the air. */
    boolean ready() {
        return !icon("tab_none").isEmpty();
    }

    /** {@code {logo}} in the header: the name in the HUD's own lettering, a word in each team's colour. */
    String logo() {
        Messages m = messages();
        return hud.lettering("red", 2, m.plain("tab-logo-1")) + overlays().space(4) + hud.lettering("blue", 2, m.plain("tab-logo-2"));
    }

    // ---- the grid --------------------------------------------------------------------

    /** One tile: whose (null for an empty one), its paint, and what is written on it. */
    private record Cell(GamePlayer gp, String paint, String left, String right) {
    }

    /**
     * The grid as {@code viewer} sees it, their own tile lit: lines of the
     * header, each row followed by the blank line that keeps the next one off
     * it. Empty when there is nobody to show.
     */
    String grid(GamePlayer viewer) {
        List<List<Cell>> columns = columns();
        int rows = 0;
        for (List<Cell> c : columns) rows = Math.max(rows, c.size());
        if (rows == 0) return "";
        Overlays o = overlays();
        int tile = Math.max(1, o.width(icon("tab_none")));
        int width = columns.size() * tile + (columns.size() - 1) * GAP;
        StringBuilder out = new StringBuilder("§r");
        for (int r = 0; r < rows; r++) {
            Line line = new Line(o);
            for (int c = 0; c < columns.size(); c++) {
                List<Cell> column = columns.get(c);
                if (r < column.size()) cell(line, c * (tile + GAP), column.get(r), viewer);
            }
            // A blank line between rows, and after the last, for its tile's bottom.
            out.append(line.end(width)).append(r + 1 < rows ? "\n\n" : "\n");
        }
        int shown = 0;
        for (List<Cell> c : columns) for (Cell cell : c) if (cell.gp != null) shown++;
        int more = people().size() - shown;
        if (more > 0) out.append("\n").append(words("tab-more", "count", String.valueOf(more))).append("\n");
        return out.toString();
    }

    /** A new refresh: the words are written again, once, for whoever looks first. */
    void refresh() {
        words.clear();
    }

    /** A message as legacy text, written once a refresh however many viewers there are. */
    private String words(String key, String... pairs) {
        return words.computeIfAbsent(key + '\u0000' + String.join("\u0000", pairs),
                k -> Text.legacy(messages().component(key, pairs)));
    }

    /** What the grid shows, a list of tiles a column. */
    private List<List<Cell>> columns() {
        if (game.phase == Game.Phase.WAITING) {
            List<Cell> all = new ArrayList<>();
            for (GamePlayer gp : people()) all.add(new Cell(gp, lobbyPaint(gp), "", ""));
            return split(all);
        }
        if (game.mode.teams()) {
            List<Cell> red = new ArrayList<>();
            List<Cell> blue = new ArrayList<>();
            for (GamePlayer gp : game.roster(Team.RED)) if (!gp.left && red.size() < ROWS) red.add(matchCell(gp, ""));
            for (GamePlayer gp : game.roster(Team.BLUE)) if (!gp.left && blue.size() < ROWS) blue.add(matchCell(gp, ""));
            // Level columns, the short one made up with empty tiles.
            while (red.size() < blue.size()) red.add(new Cell(null, "none", "", ""));
            while (blue.size() < red.size()) blue.add(new Cell(null, "none", "", ""));
            return List.of(red, blue);
        }
        List<Cell> all = new ArrayList<>();
        for (GamePlayer gp : game.standings()) {
            if (!gp.left) all.add(matchCell(gp, words("tab-place", "place", String.valueOf(game.placeOf(gp)))));
        }
        return split(all);
    }

    /** Everybody in it who is not a bot, as the grid counts them. */
    private List<GamePlayer> people() {
        List<GamePlayer> out = new ArrayList<>();
        for (GamePlayer gp : game.active()) {
            if (game.phase == Game.Phase.WAITING ? !gp.isBot() : gp.paint != null) out.add(gp);
        }
        return out;
    }

    /** One column up to {@link #ROWS}, then two of half each. */
    private static List<List<Cell>> split(List<Cell> all) {
        if (all.size() <= ROWS) return List.of(all);
        int rows = Math.min(ROWS, (all.size() + 1) / 2);
        return List.of(new ArrayList<>(all.subList(0, rows)), new ArrayList<>(all.subList(rows, Math.min(all.size(), 2 * rows))));
    }

    /** A player in a match: their paint, and their kills and deaths (and captures, in capture the flag). */
    private Cell matchCell(GamePlayer gp, String left) {
        String right = words(game.mode == Mode.CTF ? "tab-score-ctf" : "tab-score",
                "kills", String.valueOf(gp.stats.kills),
                "deaths", String.valueOf(gp.stats.deaths),
                "captures", String.valueOf(gp.stats.captures));
        return new Cell(gp, gp.paint == null ? "none" : gp.paint.id(), left, right);
    }

    /** In the lobby: what they practise in, or the side or the colour they picked, or grey. */
    private String lobbyPaint(GamePlayer gp) {
        if (gp.practising && gp.paint != null) return gp.paint.id();
        if (game.mode().teams()) return gp.pick != null ? gp.pick.paint().id() : "none";
        Paint chosen = gp.colourPick != null && game.settings().colourPick ? gp.colourPick : null;
        return chosen != null ? chosen.id() : "none";
    }

    /** One tile on {@code line}, from {@code x}: the tile, its left and right words, and the name centred between. */
    private void cell(Line line, int x, Cell c, GamePlayer viewer) {
        boolean me = c.gp == viewer && c.gp != null;
        line.at(x, "§f" + icon("tab_" + c.paint + (me ? "_me" : "")));
        if (c.gp == null) return;
        Overlays o = overlays();
        int from = x + PAD;
        int to = x + TILE_W - PAD;
        if (!c.left.isEmpty()) {
            line.at(from, c.left + "§r");
            from += o.width(c.left) + 3;
        }
        if (!c.right.isEmpty()) {
            int w = o.width(c.right);
            to -= w - 1;
            line.at(to, c.right + "§r");
            to -= 4;
        }
        boolean out = game.phase == Game.Phase.LIVE && !c.gp.alive;
        String key = me ? "tab-name-you" : out ? "tab-name-out" : "tab-name";
        String name = fit(key, c.gp.name, to - from);
        line.at(from + Math.max(0, (to - from - o.width(name)) / 2), name + "§r");
    }

    /**
     * A name as {@code key} writes it, within {@code room} pixels: as written,
     * then without bold, then cut short, so a long name never runs into the
     * kills and deaths.
     */
    private String fit(String key, String name, int room) {
        Overlays o = overlays();
        String styled = words(key, "player", name);
        if (o.width(styled) <= room) return styled;
        String plain = styled.replace("§l", "");
        if (o.width(plain) <= room) return plain;
        String cut = name;
        while (cut.length() > 1 && o.width(plain.replace(name, cut + "…")) > room) cut = cut.substring(0, cut.length() - 1);
        return plain.replace(name, cut + "…");
    }

    /** One line of the grid: pieces put at an x each, the engine's spaces between them. */
    private static final class Line {
        private final Overlays o;
        private final StringBuilder out = new StringBuilder();
        private int x;

        Line(Overlays o) {
            this.o = o;
        }

        void at(int to, String piece) {
            out.append(o.space(to - x)).append(piece);
            x = to + o.width(piece);
        }

        /** The line, padded to {@code width} so that it centres where every other line does. */
        String end(int width) {
            out.append(o.space(width - x));
            return out.toString();
        }
    }

    // ---- the list under it -----------------------------------------------------------

    /**
     * On Paper, takes the match's players off each other's lists ({@code hide})
     * or puts back everybody this took off. Nothing on Spigot.
     */
    void hidePlayers(boolean hide) {
        if (UNLIST == null || LIST == null) return;
        List<Player> online = game.online();
        for (Player viewer : online) {
            Set<UUID> off = unlisted.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
            for (Player other : online) {
                if (hide && !off.contains(other.getUniqueId())) {
                    if (call(UNLIST, viewer, other)) off.add(other.getUniqueId());
                } else if (!hide && off.remove(other.getUniqueId())) {
                    call(LIST, viewer, other);
                }
            }
        }
    }

    /** {@code p} is leaving: back on everybody's list, and everybody back on theirs. */
    void release(Player p) {
        Set<UUID> off = unlisted.remove(p.getUniqueId());
        if (LIST == null) return;
        if (off != null) {
            for (UUID id : off) {
                Player other = Bukkit.getPlayer(id);
                if (other != null) call(LIST, p, other);
            }
        }
        for (Map.Entry<UUID, Set<UUID>> e : unlisted.entrySet()) {
            if (!e.getValue().remove(p.getUniqueId())) continue;
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer != null) call(LIST, viewer, p);
        }
    }

    private static boolean call(Method m, Player viewer, Player other) {
        try {
            return Boolean.TRUE.equals(m.invoke(viewer, other));
        } catch (ReflectiveOperationException | RuntimeException e) {
            // One the viewer cannot see (another plugin hid them), or a player on their way out.
            return false;
        }
    }
}
