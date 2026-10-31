package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.text.Text;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Names over heads that blocks hide ({@code nametags.hide-behind-blocks}).
 *
 * <p>The game draws a name tag through walls (only a crouching player's is
 * hidden), and nothing a server can set changes that. So with this on each
 * player gets a text display over their head instead, which the world in front
 * of it covers like anything else, written in {@code nametags.format}. Each tag
 * is shown only to who may read it: teammates, and enemies too with
 * {@code nametags.enemies}, never its own player.
 *
 * <p><b>The game's own tag is hidden by a passenger, not by a team.</b> The
 * client never draws the name of an entity something is riding, whoever's
 * scoreboard teams it is in, so every player in the match carries two
 * displays: their tag, and an empty one that every client is sent (the tag is
 * sent only to its readers, and a passenger a client was never sent does not
 * count). Switching tags off through the match's scoreboard teams, which this
 * did first, lost to any tab or name tag plugin: those put each player in a
 * team of their own, and a player is in one team at a time.
 *
 * <p>The cost is that Spigot refuses to teleport a player who has a passenger,
 * so every teleport here goes through {@link #teleport(Player, Location)},
 * which takes both off first; the next tick puts them back.
 */
public final class Nametags {
    /** How far over the top of a player's head the name sits, as the game's own does. */
    private static final float ABOVE = 0.3f;
    /** On every display this puts up, so {@link #teleport} knows its own. */
    private static final String MARK = "paintball_nametag";

    private final Game game;
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private final Map<UUID, TextDisplay> blockers = new HashMap<>();

    Nametags(Game game) {
        this.game = game;
    }

    /**
     * Teleports a player, first taking off whatever this class has riding them:
     * Spigot will not move a player who carries a passenger.
     */
    public static boolean teleport(Player p, Location to) {
        for (Entity rider : p.getPassengers()) {
            if (rider.getScoreboardTags().contains(MARK)) p.removePassenger(rider);
        }
        return p.teleport(to);
    }

    /** Once a tick: a tag over everybody playing, riding them, and shown to the right people. */
    void tick() {
        Settings s = game.settings();
        boolean on = s.nametagsBehindBlocks && (game.phase == Game.Phase.FREEZE || game.phase == Game.Phase.LIVE);
        game.hud.gameNameTags(!on, s.nametagsEnemies);
        if (!on) {
            clear();
            return;
        }
        Set<UUID> wearing = new HashSet<>();
        for (GamePlayer gp : game.active()) {
            Player p = gp.player();
            // Out (a spectator, whom nobody can see) is no name either.
            if (p == null || gp.paint == null || !gp.alive || p.getGameMode() == GameMode.SPECTATOR) continue;
            wearing.add(gp.id);
            TextDisplay tag = ride(tags, gp, p, s, true);
            ride(blockers, gp, p, s, false);
            for (GamePlayer other : game.active()) {
                Player viewer = other.player();
                if (viewer == null) continue;
                boolean may = other != gp && (game.allies(other, gp) || s.nametagsEnemies);
                if (may != viewer.canSee(tag)) {
                    if (may) viewer.showEntity(game.plugin, tag);
                    else viewer.hideEntity(game.plugin, tag);
                }
            }
        }
        drop(tags, wearing);
        drop(blockers, wearing);
    }

    /** Every tag down: the match is over, or the option is off. */
    void clear() {
        for (TextDisplay tag : tags.values()) if (tag.isValid()) tag.remove();
        for (TextDisplay blocker : blockers.values()) if (blocker.isValid()) blocker.remove();
        tags.clear();
        blockers.clear();
    }

    /** {@code gp}'s display in {@code from}, made if it is missing and put back on them if it came off. */
    private TextDisplay ride(Map<UUID, TextDisplay> from, GamePlayer gp, Player p, Settings s, boolean named) {
        TextDisplay display = from.get(gp.id);
        if (display == null || !display.isValid()) {
            display = spawn(p.getLocation(), named ? text(gp, s) : "", named);
            from.put(gp.id, display);
        }
        if (!p.getPassengers().contains(display)) {
            // Off since a teleport: brought over first, as a passenger in another world cannot be added.
            if (display.isInsideVehicle()) display.leaveVehicle();
            if (!display.getWorld().equals(p.getWorld()) || display.getLocation().distanceSquared(p.getLocation()) > 4) {
                display.teleport(p.getLocation());
            }
            p.addPassenger(display);
        }
        return display;
    }

    private static void drop(Map<UUID, TextDisplay> from, Set<UUID> keep) {
        for (Iterator<Map.Entry<UUID, TextDisplay>> it = from.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TextDisplay> e = it.next();
            if (keep.contains(e.getKey())) continue;
            e.getValue().remove();
            it.remove();
        }
    }

    /** A bare {@code {team_colour}}, not already inside a tag as {@code <{team_colour}>} or {@code <color:{team_colour}>}. */
    private static final Pattern BARE_COLOUR = Pattern.compile("(?<![<:/])\\{team_colou?r}");

    private static String text(GamePlayer gp, Settings s) {
        String colour = gp.paint.color().asHexString();
        // Written as the colour on its own ("{team_colour}{player}"), it would
        // print as "#FF4655" in front of the name: that is a colour tag too.
        String format = BARE_COLOUR.matcher(s.nametagFormat).replaceAll("<" + colour + ">");
        return Text.legacy(Text.mm(format
                .replace("{team_colour}", colour)
                .replace("{team_color}", colour)
                .replace("{player}", Text.MM.escapeTags(gp.name))));
    }

    private TextDisplay spawn(Location at, String text, boolean named) {
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag("paintball");
            e.addScoreboardTag(MARK);
            e.setText(text);
            e.setDefaultBackground(false);
            if (!named) {
                // The blocker: nothing to draw, and every client is sent it.
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                return;
            }
            // Shown to nobody until tick() decides who may read it.
            e.setVisibleByDefault(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(false);
            // The game's own tag's backing: black at a quarter.
            e.setBackgroundColor(Color.fromARGB(64, 0, 0, 0));
            // Riding, it sits on top of the head; the game's own name is a little higher.
            e.setTransformation(new Transformation(new Vector3f(0, ABOVE, 0), new AxisAngle4f(),
                    new Vector3f(1, 1, 1), new AxisAngle4f()));
        });
    }
}
