package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Messages;
import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.team.Paint;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import ai.resourcepack.engine.api.Overlays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
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
    /** The free-for-all HUD's feed: the panel, then the killer and the victim, each one white run. */
    private static final String[] FFA_FEED_SUFFIXES = {"", "_k", "_v"};
    /**
     * The free-for-all HUD's screen paint is two runs, because a run carries at
     * most four colours besides its own: this one is red with blue, green,
     * orange and yellow as states, the other purple with pink and cyan.
     */
    private static final Set<Paint> FFA_SPLAT_A = java.util.EnumSet.of(Paint.RED, Paint.BLUE, Paint.GREEN, Paint.ORANGE, Paint.YELLOW);

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
            st.setColor(t == Team.RED ? ChatColor.RED : ChatColor.BLUE);
            // Enemies' names stay hidden: you find them by looking, not through walls.
            st.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM);
            st.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            st.setAllowFriendlyFire(false);
        }
        // A free for all has one side per player, which a scoreboard team cannot
        // say, so everybody is in one whose names nobody sees.
        org.bukkit.scoreboard.Team all = board.registerNewTeam("c_ffa");
        all.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        all.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
    }

    /** What the game's own name tags were last set to: shown or not, and to enemies too. */
    private Boolean tagsShown;
    private boolean tagsToEnemies;

    /**
     * The game's own name tags: shown (to teammates, or to everybody with
     * {@code enemies}) or off, while {@link Nametags} draws its own.
     */
    void gameNameTags(boolean shown, boolean enemies) {
        if (tagsShown != null && tagsShown == shown && tagsToEnemies == enemies) return;
        tagsShown = shown;
        tagsToEnemies = enemies;
        org.bukkit.scoreboard.Team.OptionStatus status = !shown ? org.bukkit.scoreboard.Team.OptionStatus.NEVER
                : enemies ? org.bukkit.scoreboard.Team.OptionStatus.ALWAYS : org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM;
        for (org.bukkit.scoreboard.Team st : board.getTeams()) {
            // In the free-for-all team "own team" would be everybody.
            boolean everybody = st.getName().equals("c_ffa");
            st.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, everybody && status == org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM
                    ? org.bukkit.scoreboard.Team.OptionStatus.NEVER : status);
        }
    }

    private Overlays overlays() {
        return game.plugin.content().engine().overlays();
    }

    private Messages messages() {
        return game.plugin.messages();
    }

    private String hudId() {
        return game.settings().hud(game.mode);
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
        for (org.bukkit.scoreboard.Team st : board.getTeams()) st.removeEntry(p.getName());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        p.setPlayerListName(null);
        p.setPlayerListHeaderFooter("", "");
        Text.actionBar(p, Component.empty());
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
            org.bukkit.scoreboard.Team st = gp.team != null ? board.getTeam((gp.team == Team.RED ? "a_" : "b_") + gp.team.id())
                    : gp.paint != null ? board.getTeam("c_ffa") : null;
            if (st == null) continue;
            if (!gp.isBot()) {
                st.addEntry(gp.name);
            } else {
                // A figure is in a team by its UUID, so its name has the team's colour and visibility.
                org.bukkit.entity.LivingEntity figure = gp.body();
                if (figure != null) st.addEntry(figure.getUniqueId().toString());
            }
        }
        refreshScores();
    }

    /**
     * Each player's line in the player list: their team's colour, their name,
     * and in a match their kills and deaths after it. In the name rather than a
     * score beside it, because a score is a number and Spigot has no way to
     * show one as "3 / 1".
     */
    void refreshScores() {
        for (GamePlayer gp : game.active()) {
            Player p = gp.player();
            if (p == null) continue;
            if (gp.paint == null) {
                p.setPlayerListName(Text.legacy(Text.plain(gp.name, Text.BODY)));
                continue;
            }
            p.setPlayerListName(Text.legacy(Component.text()
                    .append(Text.plain("▍", gp.paint.color()))
                    .append(Text.plain(" " + gp.name + "  ", Text.BODY))
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
                    Text.actionBar(p, lobbyBar(gp));
                } else {
                    drawHud(gp, p);
                }
            }
        }
        if (game.now % 10 == 0) {
            String header = Text.legacy(header());
            String footer = Text.legacy(messages().component("tab-footer"));
            for (Player p : game.online()) p.setPlayerListHeaderFooter(header, footer);
        }
    }

    /** The lobby's line: the mode next up, how each side is picking, and what this player picked. */
    private Component lobbyBar(GamePlayer gp) {
        Messages m0 = messages();
        Mode mode = game.mode();
        if (!mode.teams()) {
            return m0.component("lobby-ffa", "count", String.valueOf(game.size()), "mode", m0.plain("mode-" + mode.id()));
        }
        int red = 0;
        int blue = 0;
        for (GamePlayer o : game.active()) {
            if (o.pick == Team.RED) red++;
            else if (o.pick == Team.BLUE) blue++;
        }
        Messages m = messages();
        return m.component("lobby-teams", "mode", m.plain("mode-" + mode.id()), "count", String.valueOf(game.size()),
                "red", String.valueOf(red), "blue", String.valueOf(blue),
                "pick", gp.pick == null ? m.plain("pick-none") : gp.pick.display().toUpperCase(),
                "pick_colour", gp.pick == null ? "#9AA0A6" : gp.pick.color().asHexString());
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

    /** Every value either HUD prints, so leaving clears all of them. */
    private static List<String> names() {
        List<String> out = new java.util.ArrayList<>(List.of("pb_red", "pb_blue", "pb_time", "pb_status",
                "pb_weapon", "pb_gun", "pb_gun_hot", "pb_hit", "pb_xhair",
                "pb_center", "pb_center2", "pb_name_r", "pb_name_b", "pb_splat_r", "pb_splat_b",
                "pb_you", "pb_leader", "pb_you_tag", "pb_lead_tag", "pb_name",
                "pb_splat_a", "pb_splat_a_c", "pb_splat_z", "pb_splat_z_c"));
        for (int k = 1; k <= FEED_LINES; k++) {
            for (String s : FEED_SUFFIXES) out.add("pb_f" + k + s);
            for (String s : FFA_FEED_SUFFIXES) out.add("pb_f" + k + s);
        }
        return out;
    }

    private Map<String, String> values(GamePlayer gp, Player p) {
        Settings s = game.settings();
        Messages m = messages();
        Map<String, String> v = new LinkedHashMap<>();
        boolean ffa = game.mode == Mode.FFA;
        if (ffa) {
            List<GamePlayer> standings = game.standings();
            GamePlayer leader = standings.isEmpty() ? null : standings.get(0);
            v.put("pb_you", digits(String.valueOf(gp.stats.kills)));
            v.put("pb_leader", digits(String.valueOf(leader == null ? 0 : leader.stats.kills)));
            v.put("pb_you_tag", m.plain("hud-ffa-you"));
            v.put("pb_lead_tag", leader == null ? "" : leader == gp ? m.plain("hud-ffa-leading") : leader.name);
        } else {
            v.put("pb_red", score(Team.RED));
            v.put("pb_blue", score(Team.BLUE));
        }
        v.put("pb_time", digits(game.clockText()));

        boolean live = game.phase == Game.Phase.LIVE;
        boolean armed = live && gp.alive;

        // The line under the score.
        String status = "";
        if (game.phase == Game.Phase.FREEZE) status = m.plain("hud-get-ready");
        else if (armed && game.now < gp.protectedUntil) status = m.plain("hud-protected");
        else if (armed && game.mode == Mode.CTF) {
            status = game.flags.carrying(gp) ? m.plain("hud-ctf-carrying")
                    : m.plain("hud-ctf-first-to", "captures", String.valueOf(s.captures));
        } else if (live && ffa) {
            status = m.plain(s.killLimit > 0 ? "hud-ffa-place-limit" : "hud-ffa-place",
                    "place", place(m, game.placeOf(gp)), "players", String.valueOf(game.standings().size()),
                    "kills", String.valueOf(s.killLimit));
        } else if (armed && s.killLimit > 0) status = m.plain("hud-first-to", "kills", String.valueOf(s.killLimit));
        v.put("pb_status", status);

        // The marker, bottom right, and what it is called above it.
        boolean reloading = armed && gp.reloading > 0;
        v.put("pb_weapon", armed ? weapon(gp, s, reloading) : "");
        v.put("pb_gun", armed && !reloading ? m.plain("hud-marker", "skin", gp.paint == null ? "" : game.skinOf(gp).display().toUpperCase()) : "");
        v.put("pb_gun_hot", reloading ? m.plain("hud-reloading") : "");

        // The crosshair is the HUD's own, a plus drawn over the game's: from the
        // hip it carries the spread, down the sights it is the plus alone.
        int ticks = armed && !gp.aiming ? spreadTicks(game.combat.spreadDegrees(gp, p)) : 0;
        v.put("pb_xhair", armed ? icon("xhair_" + ticks) : "");
        v.put("pb_hit", armed && game.now < gp.hitUntil ? icon("hitmarker") : "");

        middle(v, gp, s, ffa);

        int k = 1;
        for (Game.Kill kill : game.feed) {
            if (k > FEED_LINES || game.now - kill.at() > s.feedTicks) break;
            boolean mine = gp.name.equals(kill.killer()) || gp.name.equals(kill.victim());
            feedLine(v, k++, kill, mine, ffa);
        }
        for (; k <= FEED_LINES; k++) for (String suffix : ffa ? FFA_FEED_SUFFIXES : FEED_SUFFIXES) v.put("pb_f" + k + suffix, "");
        return v;
    }

    /** A team's number in the strip; in capture the flag, its flag's state beside its captures. */
    private String score(Team t) {
        String n = digits(String.valueOf(game.score(t)));
        if (game.mode != Mode.CTF) return n;
        String state = switch (game.flags.state(t)) {
            case TAKEN -> "taken";
            case DROPPED -> "dropped";
            default -> "home";
        };
        return icon("flag_" + t.id() + "_" + state) + overlays().space(2) + n;
    }

    /** 1ST, 2ND, 3RD, 4TH... in the server's words. */
    private static String place(Messages m, int n) {
        if (n >= 1 && n <= 3) return m.plain("hud-place-" + n);
        return m.plain("hud-place-n", "place", String.valueOf(n));
    }

    /**
     * The middle of the screen: the paint on it, the picture in it, the line
     * under that and a name. What each phase puts there:
     *
     * <ul>
     *   <li>the countdown: a gold ring a second round, the number popping in,
     *   and which team you are on (or that it is every player for themselves);</li>
     *   <li>going live: GO!, popping in;</li>
     *   <li>splatted: the paint in your splatter's colour, fading, the respawn
     *   ring round the seconds left, and who it was;</li>
     *   <li>a splat of your own: SPLATTED and who, under the crosshair;</li>
     *   <li>capture the flag: what just happened to a flag;</li>
     *   <li>the clock's warnings: the time left in words, or from ten seconds
     *   down the number alone;</li>
     *   <li>the end: VICTORY, DEFEAT or DRAW on a band, and the score; in a
     *   free for all, VICTORY or your place, and your kills.</li>
     * </ul>
     */
    private void middle(Map<String, String> v, GamePlayer gp, Settings s, boolean ffa) {
        Messages m = messages();
        String center = "";
        String center2 = "";
        String name = "";
        Paint namePaint = null;
        String splat = "";
        Paint splatPaint = null;

        switch (game.phase) {
            case FREEZE -> {
                int into = (s.freezeSeconds * 20 - game.timer) % 20;
                int seconds = Math.max(1, (game.timer + 19) / 20);
                String ring = icon("ring_start_" + Math.min(START_RING_FRAMES - 1, into / 2));
                center = over(ring, lettering("white", into < 2 ? 4 : 3, String.valueOf(seconds)));
                if (ffa) {
                    center2 = lettering("gold", 1, m.plain("hud-ffa-banner"));
                } else if (gp.team != null) {
                    center2 = lettering("grey", 1, m.plain("hud-your-team")) + overlays().space(6)
                            + lettering(gp.team == Team.RED ? "red" : "blue", 2, m.plain(gp.team == Team.RED ? "hud-team-red" : "hud-team-blue"));
                }
            }
            case LIVE -> {
                long sinceGo = game.now - game.liveAt;
                if (sinceGo < 24) center = lettering("green", sinceGo < 2 ? 6 : sinceGo < 4 ? 5 : 4, m.plain("hud-go"));
                if (!gp.alive) {
                    long out = game.now - gp.splattedAt;
                    int fade = out < SPLAT_HOLD ? 0 : (int) (1 + (out - SPLAT_HOLD) / SPLAT_STEP);
                    if (fade < SPLAT_FADE_FRAMES && gp.splattedByPaint != null) {
                        splat = icon("hud_splat_" + gp.splat + "_" + fade);
                        splatPaint = gp.splattedByPaint;
                    }
                    if (gp.respawnAt > 0) {
                        long left = Math.max(0, gp.respawnAt - game.now);
                        int total = Math.max(1, s.respawnTicks);
                        int frame = (int) Math.min(RESPAWN_RING_FRAMES - 1, (total - left) * RESPAWN_RING_FRAMES / total);
                        center = over(icon("ring_respawn_" + Math.max(0, frame)),
                                lettering("white", 2, String.valueOf(Math.max(1, (left + 19) / 20))));
                    }
                    if (gp.splattedBy != null) {
                        center2 = lettering("grey", 1, m.plain("hud-splatted-by"));
                        name = gp.splattedBy;
                        namePaint = gp.splattedByPaint;
                    }
                } else if (gp.lastKill != null && game.now - gp.lastKillAt < s.killBannerTicks) {
                    center2 = lettering("gold", 1, m.plain("hud-splatted"));
                    name = gp.lastKill;
                    namePaint = gp.lastKillPaint;
                }
                // Capture the flag: what just happened, for a couple of seconds.
                Flags.Event e = game.flags.last;
                if (e != null && game.now - e.at() < 50 && center2.isEmpty()) {
                    boolean ours = gp.team == e.flag();
                    String sheet = e.key().equals("hud-flag-captured") || e.key().equals("hud-flag-taken")
                            ? (ours ? "grey" : "gold") : (ours ? "gold" : "grey");
                    center2 = lettering(sheet, 1, m.plain(e.key(), "team", e.flag().display().toUpperCase()));
                }
                // The clock's warnings, wherever nothing else is: the last ten
                // seconds as the number alone, popping in, and longer in words.
                long warned = game.now - game.warnedAt;
                if (game.warnedAt >= 0 && game.warnedSeconds <= 10 && warned < 20 && center.isEmpty()) {
                    center = lettering("red", warned < 2 ? 4 : 3, String.valueOf(game.warnedSeconds));
                } else if (game.warnedAt >= 0 && game.warnedSeconds > 10 && warned < 50 && center2.isEmpty()) {
                    center2 = lettering("gold", 1, timeLeft(m, game.warnedSeconds));
                }
            }
            case ENDED -> {
                long since = game.now - game.endedAt;
                Boolean won = game.won(gp);
                String look = won == null ? "draw" : won ? "victory" : "defeat";
                String sheet = won == null ? "grey" : won ? "gold" : "red";
                String word = m.plain("hud-" + look);
                if (ffa && won == Boolean.FALSE) {
                    // Not first: your place, on the plain band.
                    look = "draw";
                    sheet = "grey";
                    word = place(m, game.placeOf(gp));
                }
                String band = icon("band_" + look + "_0") + overlays().space(-1) + icon("band_" + look + "_1");
                center = over(band, lettering(sheet, since < 2 ? 5 : since < 4 ? 4 : 3, word));
                Overlays o = overlays();
                if (ffa) {
                    center2 = lettering("grey", 2, m.plain("hud-ffa-kills", "kills", String.valueOf(gp.stats.kills)));
                } else {
                    center2 = lettering("red", 2, String.valueOf(game.score(Team.RED))) + o.space(8)
                            + lettering("grey", 2, "-") + o.space(8)
                            + lettering("blue", 2, String.valueOf(game.score(Team.BLUE)));
                }
            }
            default -> {
            }
        }
        v.put("pb_center", center);
        v.put("pb_center2", center2);
        if (ffa) {
            // One name run in white; the paint on the screen in the splatter's own colour, by state.
            v.put("pb_name", name);
            boolean first = splatPaint != null && FFA_SPLAT_A.contains(splatPaint);
            v.put("pb_splat_a", first ? splat : "");
            v.put("pb_splat_a_c", first ? splatPaint.id() : "");
            boolean second = splatPaint != null && !first;
            v.put("pb_splat_z", second ? splat : "");
            v.put("pb_splat_z_c", second ? splatPaint.id() : "");
        } else {
            v.put("pb_name_r", namePaint == Paint.RED ? name : "");
            v.put("pb_name_b", namePaint == Paint.BLUE ? name : "");
            v.put("pb_splat_r", splatPaint == Paint.RED ? splat : "");
            v.put("pb_splat_b", splatPaint == Paint.BLUE ? splat : "");
        }
    }

    /** A warning's words: one minute, some minutes, or some seconds left. */
    private static String timeLeft(Messages m, int seconds) {
        if (seconds % 60 != 0) return m.plain("hud-seconds-left", "seconds", String.valueOf(seconds));
        return seconds == 60 ? m.plain("hud-minute-left") : m.plain("hud-minutes-left", "minutes", String.valueOf(seconds / 60));
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
    private void feedLine(Map<String, String> v, int line, Game.Kill kill, boolean highlight, boolean ffa) {
        Overlays o = overlays();
        String weapon = icon(kill.weapon() == Weapon.MARKER ? "feed_marker" : "grenade_icon");
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
        panel.append(icon("feed_l_" + kill.killerPaint().id())).append(back);
        int left = total - 6;
        for (int piece = 64; piece >= 1; piece >>= 1) {
            while (left >= piece) {
                panel.append(icon((highlight ? "feed_h" : "feed_m") + piece)).append(back);
                left -= piece;
            }
        }
        panel.append(icon("feed_r_" + kill.victimPaint().id())).append(back);
        panel.append(o.space(-(total - iconX))).append(weapon);

        String p = "pb_f" + line;
        v.put(p, panel.toString());
        String killer = o.space(killerX) + kill.killer();
        String victim = o.space(victimX) + kill.victim();
        if (ffa) {
            // Each player's colour is the panel's edge; the names are white and grey.
            v.put(p + "_k", killer);
            v.put(p + "_v", victim);
            return;
        }
        v.put(p + "_kr", kill.killerPaint() == Paint.RED ? killer : "");
        v.put(p + "_kb", kill.killerPaint() == Paint.BLUE ? killer : "");
        v.put(p + "_vr", kill.victimPaint() == Paint.RED ? victim : "");
        v.put(p + "_vb", kill.victimPaint() == Paint.BLUE ? victim : "");
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
     * letter; a character the sheets do not have (anything but A-Z, 0-9 and
     * {@code ! - '}) is left out, which is what a server's own wording for
     * these, in config.yml, is drawn with.
     */
    private String lettering(String sheet, int scale, String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toUpperCase().toCharArray()) {
            if (c == ' ') {
                out.append(overlays().space(5 * scale));
                continue;
            }
            if (c == '\'') {
                // A narrow mark in a full-width cell: drawn closer to its neighbours.
                out.append(overlays().space(-2 * scale)).append(icon("t_" + sheet + scale + "_apos"))
                        .append(overlays().space(-2 * scale));
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
        Messages m = messages();
        if (game.mode() == Mode.FFA) {
            List<GamePlayer> top = game.standings();
            GamePlayer leader = top.isEmpty() ? null : top.get(0);
            return m.component("tab-header-ffa",
                    "map", game.arena().name().toUpperCase(),
                    "mode", m.plain("mode-ffa"),
                    "leader", leader == null ? "-" : leader.name,
                    "leader_kills", String.valueOf(leader == null ? 0 : leader.stats.kills),
                    "time", game.clockText());
        }
        return m.component("tab-header",
                "map", game.arena().name().toUpperCase(),
                "mode", m.plain("mode-" + game.mode().id()),
                "red", String.valueOf(game.score(Team.RED)),
                "blue", String.valueOf(game.score(Team.BLUE)),
                "time", game.clockText());
    }
}
