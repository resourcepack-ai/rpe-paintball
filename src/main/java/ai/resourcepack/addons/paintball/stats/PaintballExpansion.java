package ai.resourcepack.addons.paintball.stats;

import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.arena.Arena;
import ai.resourcepack.addons.paintball.game.Game;
import ai.resourcepack.addons.paintball.game.GamePlayer;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Paintball's PlaceholderAPI placeholders, {@code %paintball_...%}. Registered
 * only when PlaceholderAPI is on the server; this class is never loaded
 * otherwise.
 *
 * <ul>
 *   <li>{@code arena_status_<arena>}, {@code arena_players_<arena>},
 *   {@code arena_plays_<arena>}, {@code arena_name_<arena>},
 *   {@code arena_red_<arena>}, {@code arena_blue_<arena>},
 *   {@code arena_time_<arena>}</li>
 *   <li>{@code player_<stat>}: the player's own totals; {@code player_arena},
 *   {@code player_team}</li>
 *   <li>{@code leaderboard_<stat>_<n>}: who is n-th, and
 *   {@code leaderboard_<stat>_<n>_value} their number</li>
 * </ul>
 *
 * A stat is kills, deaths, wins (won), losses (lost), matches (played),
 * shots, hits, accuracy or kd.
 */
public final class PaintballExpansion extends PlaceholderExpansion {
    private final PaintballPlugin plugin;

    public PaintballExpansion(PaintballPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "paintball";
    }

    @Override
    public String getAuthor() {
        return "ResourcePack AI";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Kept through /papi reload: it is this plugin's, not a downloaded one. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("arena_")) return arena(p.substring("arena_".length()));
        if (p.startsWith("leaderboard_")) return leaderboard(p.substring("leaderboard_".length()));
        if (p.startsWith("player_")) return player == null ? "" : player(player, p.substring("player_".length()));
        return null;
    }

    private String arena(String rest) {
        int cut = rest.indexOf('_');
        if (cut < 0) return null;
        String what = rest.substring(0, cut);
        Arena arena = plugin.arenas().get(rest.substring(cut + 1));
        if (arena == null) return "";
        Game game = plugin.games().existing(arena.id());
        return switch (what) {
            case "status" -> status(arena, game);
            case "players" -> String.valueOf(game == null ? 0 : game.size());
            case "plays" -> String.valueOf(plugin.stats().plays(arena.id()));
            case "name" -> arena.name();
            case "red" -> String.valueOf(game == null || !game.played() ? 0 : game.kills(Team.RED));
            case "blue" -> String.valueOf(game == null || !game.played() ? 0 : game.kills(Team.BLUE));
            case "time" -> game == null ? Text.clock(plugin.settings(arena).matchSeconds) : game.clockText();
            default -> null;
        };
    }

    /** Maintenance, Not Configured, Ready, Starting, In Game or Ending, in the server's words. */
    private String status(Arena arena, Game game) {
        String key;
        if (!arena.missing().isEmpty()) key = "status-not-configured";
        else if (!arena.enabled()) key = "status-maintenance";
        else if (game == null) key = "status-ready";
        else key = switch (game.phase()) {
            case FREEZE -> "status-starting";
            case LIVE -> "status-in-game";
            case ENDED -> "status-ending";
            default -> "status-ready";
        };
        return Text.legacy(plugin.messages().component(key));
    }

    private String player(OfflinePlayer player, String what) {
        if (what.equals("arena") || what.equals("team")) {
            Player online = player.getPlayer();
            Game game = online == null ? null : plugin.games().of(online);
            if (game == null) return "";
            if (what.equals("arena")) return game.arena().name();
            GamePlayer gp = game.get(online.getUniqueId());
            return gp == null || gp.team() == null ? "" : gp.team().display();
        }
        StatsStore.Stat stat = StatsStore.Stat.parse(what);
        if (stat == null) return null;
        StatsStore.Entry e = plugin.stats().player(player.getUniqueId());
        return stat.format(e == null ? new StatLine() : e.total);
    }

    /** {@code <stat>_<n>} or {@code <stat>_<n>_value}. */
    private String leaderboard(String rest) {
        boolean value = rest.endsWith("_value");
        if (value) rest = rest.substring(0, rest.length() - "_value".length());
        int cut = rest.lastIndexOf('_');
        if (cut < 0) return null;
        StatsStore.Stat stat = StatsStore.Stat.parse(rest.substring(0, cut));
        int place;
        try {
            place = Integer.parseInt(rest.substring(cut + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (stat == null || place < 1) return null;
        List<StatsStore.Entry> top = plugin.stats().top(stat);
        if (place > top.size()) return value ? "0" : "";
        StatsStore.Entry e = top.get(place - 1);
        return value ? stat.format(e.total) : e.name;
    }
}
