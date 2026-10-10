package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Messages;
import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import ai.resourcepack.engine.api.Overlays;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything on a player's screen during a match.
 *
 * <ul>
 *   <li><b>The HUD</b> is the content folder's {@code huds/hud.json}: a
 *   full-screen shader object drawn in ResourcePack AI Studio, which RP Engine
 *   draws in the action bar. The score strip (top centre), the kill feed (top
 *   left), the marker (bottom right), the hit marker and the respawn countdown
 *   are all in it; this class only fills its values, and redraws it the moment
 *   one changes, because the engine's own redraw is every second and a half.</li>
 *   <li><b>The player list</b> (Tab) is the live scoreboard: the score and the
 *   clock in the header, both teams in order, each player's kills and deaths.</li>
 * </ul>
 *
 * <p>The HUD has a run per colour, because a shader run's colour is its
 * address: so a kill feed line is five values (the panel and the weapon, then
 * the killer and the victim, each in a red and a blue run of which one is
 * empty), laid out against each other with the engine's own measurements.
 */
final class Hud {
    private static final int FEED_LINES = 3;

    private static final String[] FEED_SUFFIXES = {"", "_kr", "_kb", "_vr", "_vb"};

    private final Game game;
    private final Scoreboard board;
    private final Objective tab;
    private final Map<UUID, String> drawn = new HashMap<>();

    Hud(Game game) {
        this.game = game;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        // Sorted by name, so red is listed above blue, as a scoreboard should be.
        for (Team t : Team.values()) {
            org.bukkit.scoreboard.Team st = board.registerNewTeam((t == Team.RED ? "a_" : "b_") + t.id());
            st.color(t == Team.RED ? NamedTextColor.RED : NamedTextColor.BLUE);
            // Enemies' names stay hidden: you find them by looking, not through walls.
            st.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM);
            st.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            st.setAllowFriendlyFire(false);
        }
        tab = board.registerNewObjective("paintball_kd", Criteria.DUMMY, Component.text("K / D"));
        tab.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    private Overlays overlays() {
        return game.plugin.content().engine().overlays();
    }

    private Messages messages() {
        return game.plugin.messages();
    }

    private String hudId() {
        return game.settings().hudId;
    }

    void join(Player p) {
        p.setScoreboard(board);
        drawn.remove(p.getUniqueId());
        refreshTeams();
    }

    void leave(Player p) {
        overlays().hide(p, hudId());
        for (String key : names()) overlays().set(p, key, null);
        drawn.remove(p.getUniqueId());
        board.resetScores(p.getName());
        for (org.bukkit.scoreboard.Team st : board.getTeams()) st.removeEntry(p.getName());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.playerListName(null);
        p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        p.sendActionBar(Component.empty());
    }

    void close() {
        drawn.clear();
    }

    /** Team colours on names (the player list, name tags) for everybody in the match. */
    void refreshTeams() {
        for (org.bukkit.scoreboard.Team st : board.getTeams()) {
            for (String e : new HashSet<>(st.getEntries())) st.removeEntry(e);
        }
        for (GamePlayer gp : game.active()) {
            Player p = gp.player();
            if (gp.team != null) {
                org.bukkit.scoreboard.Team st = board.getTeam((gp.team == Team.RED ? "a_" : "b_") + gp.team.id());
                if (st != null) st.addEntry(gp.name);
            }
            if (p != null) {
                p.playerListName(gp.team == null ? Text.plain(gp.name, Text.BODY)
                        : Component.text().append(Component.text("▍", gp.team.color())).append(Text.plain(" " + gp.name, Text.BODY)).build());
            }
        }
        refreshScores();
    }

    /** Each player's kills and deaths, right-aligned in the player list. */
    void refreshScores() {
        for (GamePlayer gp : game.active()) {
            var score = tab.getScore(gp.name);
            score.setScore(gp.stats.kills);
            score.numberFormat(NumberFormat.fixed(Component.text()
                    .append(Text.plain(String.valueOf(gp.stats.kills), Text.BODY).decorate(TextDecoration.BOLD))
                    .append(Text.plain(" / ", Text.DIM))
                    .append(Text.plain(String.valueOf(gp.stats.deaths), Text.MUTED)).build()));
        }
        drawn.clear();
    }

    void tick() {
        if (game.now % 2 == 0) {
            for (GamePlayer gp : game.active()) {
                Player p = gp.player();
                if (p == null) continue;
                if (game.phase == Game.Phase.WAITING) {
                    if (drawn.remove(p.getUniqueId()) != null) overlays().hide(p, hudId());
                    p.sendActionBar(messages().component("lobby-bar", "count", String.valueOf(game.size())));
                } else {
                    drawHud(gp, p);
                }
            }
        }
        if (game.now % 10 == 0) {
            Component header = header();
            Component footer = messages().component("tab-footer");
            for (Player p : game.online()) p.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    // ---- the HUD ---------------------------------------------------------------------

    private void drawHud(GamePlayer gp, Player p) {
        Map<String, String> values = values(gp, p);
        String signature = String.join("|", values.values());
        if (signature.equals(drawn.get(p.getUniqueId()))) return;
        drawn.put(p.getUniqueId(), signature);
        Overlays overlays = overlays();
        for (Map.Entry<String, String> e : values.entrySet()) overlays.set(p, e.getKey(), e.getValue());
        // show() draws at once; the engine alone would get to it within a second and a half.
        overlays.show(p, hudId());
    }

    private static List<String> names() {
        List<String> out = new java.util.ArrayList<>(List.of("pb_red", "pb_blue", "pb_time", "pb_status", "pb_status_hot",
                "pb_ammo", "pb_ammo_cap", "pb_ammo_n", "pb_ammo_max", "pb_gun", "pb_gun_hot", "pb_nade",
                "pb_respawn", "pb_hit", "pb_xhair"));
        for (int k = 1; k <= FEED_LINES; k++) for (String s : FEED_SUFFIXES) out.add("pb_f" + k + s);
        return out;
    }

    private Map<String, String> values(GamePlayer gp, Player p) {
        var s = game.settings();
        Messages m = messages();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("pb_red", digits(String.valueOf(game.kills(Team.RED))));
        v.put("pb_blue", digits(String.valueOf(game.kills(Team.BLUE))));
        v.put("pb_time", digits(game.clockText()));

        // The line under the score: gold when it is good news.
        String status = "";
        String hot = "";
        switch (game.phase) {
            case FREEZE -> status = m.plain("hud-get-ready");
            case ENDED -> {
                if (game.winner() == null) status = m.plain("hud-draw");
                else if (gp.team == game.winner()) hot = m.plain("hud-victory");
                else status = m.plain("hud-defeat");
            }
            case LIVE -> {
                if (!gp.alive) {
                    status = gp.splattedBy == null ? "" : m.plain("hud-splatted-by", "player", gp.splattedBy);
                } else if (gp.lastKill != null && game.now - gp.lastKillAt < s.killBannerTicks) {
                    hot = m.plain("hud-splatted", "player", gp.lastKill);
                } else if (game.now < gp.protectedUntil) {
                    status = m.plain("hud-protected");
                } else if (s.killLimit > 0) {
                    status = m.plain("hud-first-to", "kills", String.valueOf(s.killLimit));
                }
            }
            default -> {
            }
        }
        v.put("pb_status", status);
        v.put("pb_status_hot", hot);

        // The marker, bottom right.
        boolean armed = game.phase == Game.Phase.LIVE && gp.alive;
        boolean reloading = armed && gp.reloading > 0;
        v.put("pb_ammo", armed ? digits(String.valueOf(gp.ammo)) : "");
        v.put("pb_ammo_cap", armed ? "/ " + s.hopper : "");
        int fill = reloading ? Math.round(s.hopper * (1f - gp.reloading / (float) s.reloadTicks)) : gp.ammo;
        v.put("pb_ammo_n", armed ? String.valueOf(fill) : "0");
        v.put("pb_ammo_max", String.valueOf(s.hopper));
        v.put("pb_gun", armed && !reloading ? m.plain("hud-marker", "skin", gp.team == null ? "" : Loadout.skinName(s, gp)) : "");
        v.put("pb_gun_hot", reloading ? m.plain("hud-reloading") : "");
        // The grenade: its icon when it is ready, and the seconds left while it recharges.
        long recharge = gp.grenadeReadyAt - game.now;
        v.put("pb_nade", !armed ? "" : recharge > 0
                ? icon("grenade_icon") + overlays().space(2) + ((recharge + 19) / 20)
                : icon("grenade_icon") + overlays().space(4));

        // The middle of the screen: the countdown while out; while in, the hit
        // marker and the spread round the crosshair (the crosshair itself is the
        // HUD's own, drawn over the game's). Down the sights there is no spread.
        boolean out = game.phase == Game.Phase.LIVE && !gp.alive && gp.respawnAt > 0;
        v.put("pb_respawn", out ? digits(String.valueOf(Math.max(1, (gp.respawnAt - game.now + 19) / 20))) : "");
        v.put("pb_hit", armed && game.now < gp.hitUntil ? icon("hitmarker") : "");
        // The crosshair is the HUD's own, a plus drawn over the game's (which is
        // why it is there whenever the game's would be): from the hip it carries
        // the spread, down the sights it is the plus alone.
        int ticks = armed && !gp.aiming ? spreadTicks(game.combat.spreadDegrees(gp, p)) : 0;
        v.put("pb_xhair", p.getGameMode() != org.bukkit.GameMode.SPECTATOR ? icon("xhair_" + ticks) : "");

        int k = 1;
        for (Game.Kill kill : game.feed) {
            if (k > FEED_LINES || game.now - kill.at() > s.feedTicks) break;
            boolean mine = gp.name.equals(kill.killer()) || gp.name.equals(kill.victim());
            feedLine(v, k++, kill, mine);
        }
        for (; k <= FEED_LINES; k++) for (String suffix : FEED_SUFFIXES) v.put("pb_f" + k + suffix, "");
        return v;
    }

    /**
     * One line of the kill feed, laid out as Valorant's is: a dark panel with
     * the killer's colour down its left edge and the victim's down its right,
     * the killer's name, the weapon, the victim's name. The names are runs of
     * their own (a run is one colour), so each starts with the space that puts
     * it in its place, and the panel run goes back over itself to draw the
     * weapon. Every width is the engine's, the same one it lays the runs out
     * with, so the pieces meet wherever the names end.
     */
    private void feedLine(Map<String, String> v, int line, Game.Kill kill, boolean highlight) {
        Overlays o = overlays();
        String weapon = icon(kill.weapon() == Weapon.GRENADE ? "grenade_icon" : "feed_marker");
        int killerWidth = o.width(kill.killer()) - 1;
        int victimWidth = o.width(kill.victim()) - 1;
        int iconWidth = Math.max(0, o.width(weapon) - 1);
        int killerX = 6;
        int iconX = killerX + killerWidth + 5;
        int victimX = iconX + iconWidth + 5;
        int total = victimX + victimWidth + 6;

        // Each piece advances its width and one more; the step back closes the gap.
        String back = o.space(-1);
        StringBuilder panel = new StringBuilder();
        panel.append(icon("feed_l_" + kill.killerTeam().id())).append(back);
        int left = total - 6;
        for (int piece = 64; piece >= 1; piece >>= 1) {
            while (left >= piece) {
                panel.append(icon((highlight ? "feed_h" : "feed_m") + piece)).append(back);
                left -= piece;
            }
        }
        panel.append(icon("feed_r_" + kill.victimTeam().id())).append(back);
        panel.append(o.space(-(total - iconX))).append(weapon);

        String p = "pb_f" + line;
        v.put(p, panel.toString());
        String killer = o.space(killerX) + kill.killer();
        String victim = o.space(victimX) + kill.victim();
        v.put(p + "_kr", kill.killerTeam() == Team.RED ? killer : "");
        v.put(p + "_kb", kill.killerTeam() == Team.BLUE ? killer : "");
        v.put(p + "_vr", kill.victimTeam() == Team.RED ? victim : "");
        v.put(p + "_vb", kill.victimTeam() == Team.BLUE ? victim : "");
    }

    /** Which ring of spread ticks a spread draws: standing, moving, flying. */
    private static int spreadTicks(double degrees) {
        return degrees < 1.0 ? 1 : degrees < 2.4 ? 2 : 3;
    }

    /** Numbers in the HUD's big digits. Anything else is left as it is. */
    private String digits(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') out.append(icon("digit_" + c));
            else if (c == ':') out.append(icon("digit_colon"));
            else out.append(c);
        }
        return out.toString();
    }

    private String icon(String path) {
        return game.content.icon(path, "");
    }

    // ---- the player list (Tab) -------------------------------------------------------

    private Component header() {
        return messages().component("tab-header",
                "map", game.arena().name().toUpperCase(),
                "red", String.valueOf(game.kills(Team.RED)),
                "blue", String.valueOf(game.kills(Team.BLUE)),
                "time", game.clockText());
    }
}
