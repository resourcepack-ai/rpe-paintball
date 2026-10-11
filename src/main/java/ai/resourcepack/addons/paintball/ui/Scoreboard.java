package ai.resourcepack.addons.paintball.ui;

import ai.resourcepack.engine.api.Dialogs;
import ai.resourcepack.addons.paintball.PaintballPlugin;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import ai.resourcepack.addons.paintball.game.Game;
import ai.resourcepack.addons.paintball.game.GamePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The match scoreboard: the content folder's {@code dialogs/scoreboard.json},
 * drawn in ResourcePack AI Studio (both teams' tables with faces, kills, deaths
 * and accuracy under the score and the clock), filled in for whoever opens it.
 * F opens it during a match, and everybody gets it when the match ends.
 *
 * <p>The tables are Studio lists named {@code red} and {@code blue}: row k reads
 * {@code {red_k}} (the name, and the face beside it), {@code {red_k_k}},
 * {@code {red_k_d}} and {@code {red_k_acc}}, and a row with no name is hidden.
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
        Map<String, String> values = new HashMap<>();
        values.put("pb_title", plugin.messages().plain(key));
        values.put("pb_title_colour", colour);
        values.put("pb_red", String.valueOf(game.kills(Team.RED)));
        values.put("pb_blue", String.valueOf(game.kills(Team.BLUE)));
        values.put("pb_time", game.clockText());
        values.put("pb_map", game.arena().name().toUpperCase());
        for (Team t : Team.values()) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (GamePlayer gp : game.roster(t)) {
                if (rows.size() == 5) break;
                Map<String, String> row = new HashMap<>();
                row.put("", gp.name());
                row.put("k", String.valueOf(gp.stats().kills));
                row.put("d", String.valueOf(gp.stats().deaths));
                row.put("acc", gp.stats().accuracy() + "%");
                rows.add(row);
            }
            dialogs.listRows(p, t.id(), rows);
        }
        if (!dialogs.show(p, plugin.settings().scoreboardId, values)) {
            plugin.getLogger().warning("Could not open the scoreboard dialog " + plugin.settings().scoreboardId
                    + " for " + p.getName() + ". Is it in RP Engine's content (/rp reload)?");
        }
    }
}
