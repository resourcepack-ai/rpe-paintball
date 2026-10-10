package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Messages;
import ai.resourcepack.addons.paintball.Settings;
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
import java.util.Set;
import java.util.UUID;

/**
 * Everything on a player's screen during a match. Nothing here is a title:
 * every moment of the match is drawn by the HUD.
 *
 * <ul>
 *   <li><b>The HUD</b> is the content folder's {@code huds/hud.json}: a
 *   full-screen shader object drawn in ResourcePack AI Studio, which RP Engine
 *   draws in the action bar. Its shapes are the score strip; everything else
 *   is a run whose value this class writes, and redraws the moment one
 *   changes, which is also what animates it (a value per two ticks).</li>
 *   <li><b>The player list</b> (Tab) is the live scoreboard: the score and the
 *   clock in the header, both teams in order, each player's kills and deaths.</li>
 * </ul>
 *
 * <p>A run is one colour, and that colour is its address in the shader, so
 * anything in more than one colour is a picture that arrives coloured, in a
 * white run: the weapon cluster (bottom right), the middle of the screen (the
 * countdown, GO!, the respawn ring, the result) and the line under it. Their
 * glyphs are tools/hud_art.py's, and the numbers below that place things inside
 * them are that file's too. A name stays a run of its team's colour.
 */
final class Hud {
    private static final int FEED_LINES = 3;

    private static final String[] FEED_SUFFIXES = {"", "_kr", "_kb", "_vr", "_vb"};

    // ---- tools/hud_art.py's numbers ----------------------------------------------------
    /** The weapon cluster: the grenade card, a gap, the ammo panel. */
    private static final int CARD_W = 26;
    private static final int CARD_GAP = 3;
    private static final int PANEL_W = 74;
    private static final int BAR_W = 62;
    private static final int CARD_FRAMES = 19;
    private static final double LOW_AMMO = 0.25;
    private static final int START_RING_FRAMES = 10;
    private static final int RESPAWN_RING_FRAMES = 30;
    private static final int SPLAT_FADE_FRAMES = 5;
    /** How long the paint stays on a splatted player's screen before it starts to fade, and each step of the fade. */
    private static final int SPLAT_HOLD = 10;
    private static final int SPLAT_STEP = 4;

    private final Game game;
    private final Scoreboard board;
    private final Objective tab;
    /** What was last drawn for each player, so an unchanged HUD is not sent again. */
    private final Map<UUID, String> drawn = new HashMap<>();
    /** Who has the HUD on screen: kept apart from {@link #drawn}, which is cleared to force a redraw. */
    private final Set<UUID> shown = new HashSet<>();

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
        shown.remove(p.getUniqueId());
        board.resetScores(p.getName());
        for (org.bukkit.scoreboard.Team st : board.getTeams()) st.removeEntry(p.getName());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.playerListName(null);
        p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        p.sendActionBar(Component.empty());
    }

    void close() {
        drawn.clear();
        shown.clear();
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
                    // Off, every time: the engine redraws a shown HUD on its own
                    // every second and a half, so one left on would flash up
                    // between the lobby's action bars.
                    drawn.remove(p.getUniqueId());
                    if (shown.remove(p.getUniqueId())) overlays().hide(p, hudId());
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
        shown.add(p.getUniqueId());
        Overlays overlays = overlays();
        for (Map.Entry<String, String> e : values.entrySet()) overlays.set(p, e.getKey(), e.getValue());
        // show() draws at once; the engine alone would get to it within a second and a half.
        overlays.show(p, hudId());
    }

    private static List<String> names() {
        List<String> out = new java.util.ArrayList<>(List.of("pb_red", "pb_blue", "pb_time", "pb_status",
                "pb_weapon", "pb_gun", "pb_gun_hot", "pb_hit", "pb_xhair",
                "pb_center", "pb_center2", "pb_name_r", "pb_name_b", "pb_splat_r", "pb_splat_b"));
        for (int k = 1; k <= FEED_LINES; k++) for (String s : FEED_SUFFIXES) out.add("pb_f" + k + s);
        return out;
    }

    private Map<String, String> values(GamePlayer gp, Player p) {
        Settings s = game.settings();
        Messages m = messages();
        Map<String, String> v = new LinkedHashMap<>();
        v.put("pb_red", digits(String.valueOf(game.kills(Team.RED))));
        v.put("pb_blue", digits(String.valueOf(game.kills(Team.BLUE))));
        v.put("pb_time", digits(game.clockText()));

        boolean live = game.phase == Game.Phase.LIVE;
        boolean armed = live && gp.alive;

        // The line under the score.
        String status = "";
        if (game.phase == Game.Phase.FREEZE) status = m.plain("hud-get-ready");
        else if (armed && game.now < gp.protectedUntil) status = m.plain("hud-protected");
        else if (armed && s.killLimit > 0) status = m.plain("hud-first-to", "kills", String.valueOf(s.killLimit));
        v.put("pb_status", status);

        // The marker, bottom right, and what it is called above it.
        boolean reloading = armed && gp.reloading > 0;
        v.put("pb_weapon", armed ? weapon(gp, s, reloading) : "");
        v.put("pb_gun", armed && !reloading ? m.plain("hud-marker", "skin", gp.team == null ? "" : Loadout.skinName(s, gp)) : "");
        v.put("pb_gun_hot", reloading ? m.plain("hud-reloading") : "");

        // The crosshair is the HUD's own, a plus drawn over the game's: from the
        // hip it carries the spread, down the sights it is the plus alone.
        int ticks = armed && !gp.aiming ? spreadTicks(game.combat.spreadDegrees(gp, p)) : 0;
        v.put("pb_xhair", armed ? icon("xhair_" + ticks) : "");
        v.put("pb_hit", armed && game.now < gp.hitUntil ? icon("hitmarker") : "");

        middle(v, gp, s);

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
     * The middle of the screen: the paint on it, the picture in it, the line
     * under that and a name. What each phase puts there:
     *
     * <ul>
     *   <li>the countdown: a gold ring a second round, the number popping in,
     *   and which team you are on;</li>
     *   <li>going live: GO!, popping in;</li>
     *   <li>splatted: the paint in your splatter's colour, fading, the respawn
     *   ring round the seconds left, and who it was;</li>
     *   <li>a splat of your own: SPLATTED and who, under the crosshair;</li>
     *   <li>the end: VICTORY, DEFEAT or DRAW on a band, and the score.</li>
     * </ul>
     */
    private void middle(Map<String, String> v, GamePlayer gp, Settings s) {
        String center = "";
        String center2 = "";
        String name = "";
        Team nameTeam = null;
        String splat = "";
        Team splatTeam = null;

        switch (game.phase) {
            case FREEZE -> {
                int into = (s.freezeSeconds * 20 - game.timer) % 20;
                int seconds = Math.max(1, (game.timer + 19) / 20);
                String ring = icon("ring_start_" + Math.min(START_RING_FRAMES - 1, into / 2));
                center = over(ring, lettering("white", into < 2 ? 4 : 3, String.valueOf(seconds)));
                if (gp.team != null) {
                    center2 = lettering("grey", 1, "YOUR TEAM") + overlays().space(6)
                            + lettering(gp.team == Team.RED ? "red" : "blue", 2, gp.team == Team.RED ? "RED" : "BLUE");
                }
            }
            case LIVE -> {
                long sinceGo = game.now - game.liveAt;
                if (sinceGo < 24) center = lettering("green", sinceGo < 2 ? 6 : sinceGo < 4 ? 5 : 4, "GO!");
                if (!gp.alive) {
                    long out = game.now - gp.splattedAt;
                    int fade = out < SPLAT_HOLD ? 0 : (int) (1 + (out - SPLAT_HOLD) / SPLAT_STEP);
                    if (fade < SPLAT_FADE_FRAMES && gp.splattedByTeam != null) {
                        splat = icon("hud_splat_" + gp.splat + "_" + fade);
                        splatTeam = gp.splattedByTeam;
                    }
                    if (gp.respawnAt > 0) {
                        long left = Math.max(0, gp.respawnAt - game.now);
                        int total = Math.max(1, s.respawnTicks);
                        int frame = (int) Math.min(RESPAWN_RING_FRAMES - 1, (total - left) * RESPAWN_RING_FRAMES / total);
                        center = over(icon("ring_respawn_" + Math.max(0, frame)),
                                lettering("white", 2, String.valueOf(Math.max(1, (left + 19) / 20))));
                    }
                    if (gp.splattedBy != null) {
                        center2 = lettering("grey", 1, "SPLATTED BY");
                        name = gp.splattedBy;
                        nameTeam = gp.splattedByTeam;
                    }
                } else if (gp.lastKill != null && game.now - gp.lastKillAt < s.killBannerTicks) {
                    center2 = lettering("gold", 1, "SPLATTED");
                    name = gp.lastKill;
                    nameTeam = gp.lastKillTeam;
                }
            }
            case ENDED -> {
                long since = game.now - game.endedAt;
                Team w = game.winner();
                String look = w == null ? "draw" : gp.team == w ? "victory" : "defeat";
                String sheet = w == null ? "grey" : gp.team == w ? "gold" : "red";
                String word = look.toUpperCase();
                String band = icon("band_" + look + "_0") + overlays().space(-1) + icon("band_" + look + "_1");
                center = over(band, lettering(sheet, since < 2 ? 5 : since < 4 ? 4 : 3, word));
                Overlays o = overlays();
                center2 = lettering("red", 2, String.valueOf(game.kills(Team.RED))) + o.space(8)
                        + lettering("grey", 2, "-") + o.space(8)
                        + lettering("blue", 2, String.valueOf(game.kills(Team.BLUE)));
            }
            default -> {
            }
        }
        v.put("pb_center", center);
        v.put("pb_center2", center2);
        v.put("pb_name_r", nameTeam == Team.RED ? name : "");
        v.put("pb_name_b", nameTeam == Team.BLUE ? name : "");
        v.put("pb_splat_r", splatTeam == Team.RED ? splat : "");
        v.put("pb_splat_b", splatTeam == Team.BLUE ? splat : "");
    }

    /**
     * The weapon cluster, one picture in one run: the grenade card (its can
     * filling up as it recharges, gold when it is back) and the ammo panel (the
     * bar at the ammo's length, red when it is low), with the count and the
     * hopper's size drawn over the panel, right-aligned with a margin, between
     * the panel's top and its bar. Every piece hangs from the run's line, so
     * they all line up by construction.
     */
    private String weapon(GamePlayer gp, Settings s, boolean reloading) {
        Overlays o = overlays();
        String back = o.space(-1);
        StringBuilder out = new StringBuilder();

        int cooldown = Math.max(1, s.grenadeCooldownTicks);
        long recharge = Math.max(0, gp.grenadeReadyAt - game.now);
        int card = recharge <= 0 ? CARD_FRAMES - 1
                : (int) Math.min(CARD_FRAMES - 2, (cooldown - recharge) * (CARD_FRAMES - 1) / cooldown);
        out.append(icon("w_card_" + Math.max(0, card))).append(back).append(o.space(CARD_GAP));

        int hopper = Math.max(1, s.hopper);
        int shown = reloading ? Math.round(hopper * (1f - gp.reloading / (float) s.reloadTicks)) : gp.ammo;
        shown = Math.max(0, Math.min(hopper, shown));
        int level = Math.round(BAR_W * shown / (float) hopper);
        boolean low = !reloading && shown <= hopper * LOW_AMMO;
        out.append(icon((low ? "w_panel_low_" : "w_panel_") + level)).append(back);

        String count = glyphs("w_digit_", String.valueOf(shown));
        String cap = icon("w_small_slash") + glyphs("w_small_", String.valueOf(hopper));
        int countW = o.width(count) - 1;
        int capW = o.width(cap) - 1;
        int capX = PANEL_W - 6 - capW;
        int countX = capX - 3 - countW;
        // From the panel's right edge back to the count, then on to the end again.
        out.append(o.space(countX - PANEL_W)).append(count).append(o.space(2)).append(cap).append(o.space(5));
        return out.toString();
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

    /**
     * {@code top} drawn centred over {@code base}, the whole as wide as the
     * wider of the two, so a run centred on its width centres both.
     */
    private String over(String base, String top) {
        Overlays o = overlays();
        int bw = o.width(base);
        int tw = o.width(top);
        if (tw <= bw) {
            int back = bw - (bw - tw) / 2;
            return base + o.space(-back) + top + o.space(back - tw);
        }
        int lead = (tw - bw) / 2;
        return o.space(lead) + base + o.space(-(lead + bw)) + top;
    }

    /**
     * Words in the HUD's own lettering ({@code t_<sheet><scale>_<char>}): the
     * score digits' style, outlined, in a sheet per colour. A space is half a
     * letter; a character the sheets do not have is left out.
     */
    private String lettering(String sheet, int scale, String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toUpperCase().toCharArray()) {
            if (c == ' ') {
                out.append(overlays().space(5 * scale));
                continue;
            }
            String id = c == '!' ? "bang" : c == '-' ? "dash" : String.valueOf(Character.toLowerCase(c));
            out.append(icon("t_" + sheet + scale + "_" + id));
        }
        return out.toString();
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

    /** {@code text}'s digits as the icons {@code prefix + digit}. */
    private String glyphs(String prefix, String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) out.append(icon(prefix + c));
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
