package ai.resourcepack.addons.paintball.ui;

import ai.resourcepack.engine.api.Dialogs;
import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import ai.resourcepack.addons.paintball.game.Game;
import ai.resourcepack.addons.paintball.game.GamePlayer;
import ai.resourcepack.addons.paintball.game.Mode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The match scoreboard, a dialog drawn in ResourcePack AI Studio per mode,
 * filled in for whoever opens it. F opens it during a match, and everybody
 * gets it when the match ends.
 *
 * <ul>
 *   <li>Team deathmatch, {@code dialogs/scoreboard.json}: both teams' tables
 *   with faces, kills, deaths and accuracy under the score and the clock.</li>
 *   <li>Capture the flag, {@code dialogs/scoreboard_ctf.json}: the same with
 *   captures where accuracy was, and the captures as the score.</li>
 *   <li>Free for all, {@code dialogs/scoreboard_ffa.json}: one table of
 *   everybody, best first, under your kills and the leader's.</li>
 * </ul>
 *
 * <p>The tables are Studio lists: {@code red} and {@code blue}, or {@code all}.
 * Row k reads {@code {red_k}} (the name, and the face beside it),
 * {@code {red_k_k}}, {@code {red_k_d}}, {@code {red_k_acc}},
 * {@code {red_k_cap}} and {@code {all_k_place}}, and a row with no name is
 * hidden. The column headings are values too ({@code pb_h_*}), from config.yml.
 */
public final class Scoreboard {
    private final PaintballPlugin plugin;

    public Scoreboard(PaintballPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, Game game) {
        Dialogs dialogs = plugin.content().engine().dialogs();
        GamePlayer me = game.get(p.getUniqueId());
        String key = switch (game.phase()) {
            case ENDED -> game.winner() == null ? "scoreboard-draw"
                    : me != null && me.team() == game.winner() ? "scoreboard-victory" : "scoreboard-defeat";
            case WAITING -> game.played() ? "scoreboard-last" : "scoreboard-live";
            default -> "scoreboard-live";
        };
        // The heading's colour is a value too: the dialog draws {pb_title_colour}.
        String colour = switch (key) {
            case "scoreboard-victory" -> "#ffd23f";
            case "scoreboard-defeat" -> "#ff4655";
            default -> "#ffffff";
        };
        var m = plugin.messages();
        Mode mode = game.mode();
        Map<String, String> values = new HashMap<>();
        values.put("pb_title", m.plain(key));
        values.put("pb_title_colour", colour);
        values.put("pb_time", game.clockText());
        values.put("pb_map", game.arena().name().toUpperCase());
        values.put("pb_mode", m.plain("mode-" + mode.id()));
        for (String h : List.of("red", "blue", "kills", "deaths", "accuracy", "captures", "players", "place")) {
            values.put("pb_h_" + h, m.plain("scoreboard-" + h));
        }
        if (mode == Mode.FFA) {
            List<GamePlayer> all = game.standings();
            values.put("pb_you", String.valueOf(me == null ? 0 : me.stats().kills));
            values.put("pb_leader", String.valueOf(all.isEmpty() ? 0 : all.get(0).stats().kills));
            values.put("pb_leader_name", all.isEmpty() ? "" : all.get(0).name());
            List<Map<String, String>> rows = new ArrayList<>();
            for (GamePlayer gp : all) {
                if (rows.size() == 8) break;
                Map<String, String> row = row(gp);
                row.put("place", String.valueOf(game.placeOf(gp)));
                rows.add(row);
            }
            dialogs.listRows(p, "all", rows);
        } else {
            values.put("pb_red", String.valueOf(game.score(Team.RED)));
            values.put("pb_blue", String.valueOf(game.score(Team.BLUE)));
            for (Team t : Team.values()) {
                List<Map<String, String>> rows = new ArrayList<>();
                for (GamePlayer gp : game.roster(t)) {
                    if (rows.size() == 5) break;
                    rows.add(row(gp));
                }
                dialogs.listRows(p, t.id(), rows);
            }
        }
        String id = plugin.settings().scoreboard(mode);
        if (!dialogs.show(p, id, values)) {
            plugin.getLogger().warning("Could not open the scoreboard dialog " + id
                    + " for " + p.getName() + ". Is it in RP Engine's content (/rp reload)?");
        }
    }

    /** One player's row: name (and face), kills, deaths, accuracy and captures. */
    private static Map<String, String> row(GamePlayer gp) {
        Map<String, String> row = new HashMap<>();
        row.put("", gp.name());
        row.put("k", String.valueOf(gp.stats().kills));
        row.put("d", String.valueOf(gp.stats().deaths));
        row.put("acc", gp.stats().accuracy() + "%");
        row.put("cap", String.valueOf(gp.stats().captures));
        return row;
    }
}
