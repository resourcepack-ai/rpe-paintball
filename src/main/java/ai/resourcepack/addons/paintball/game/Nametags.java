package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.config.Settings;
import ai.resourcepack.addons.paintball.text.Text;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Names over heads that blocks hide ({@code nametags.hide-behind-blocks}).
 *
 * <p>The game draws a name tag through walls (only a crouching player's is
 * hidden), and nothing a server can set changes that. So with this on the
 * game's tags are switched off for the match and each player gets a text
 * display over their head instead, which the world in front of it covers like
 * anything else, written in {@code nametags.format}. It follows its player
 * every tick rather than riding them: a player with a passenger cannot be
 * teleported, and a match teleports people all the time. Each tag is shown
 * only to who may read it: teammates, and enemies too with
 * {@code nametags.enemies}, never its own player.
 */
final class Nametags {
    /** How far over the top of a player's head the name sits, as the game's own does. */
    private static final double ABOVE = 0.3;

    private final Game game;
    private final Map<UUID, TextDisplay> tags = new HashMap<>();

    Nametags(Game game) {
        this.game = game;
    }

    /** Once a tick: a tag over everybody playing, in its place, and shown to the right people. */
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
            if (p == null || gp.team == null || !gp.alive || p.getGameMode() == GameMode.SPECTATOR) continue;
            wearing.add(gp.id);
            Location at = p.getLocation().add(0, p.getHeight() + ABOVE, 0);
            TextDisplay tag = tags.get(gp.id);
            if (tag == null || !tag.isValid() || !tag.getWorld().equals(p.getWorld())) {
                if (tag != null) tag.remove();
                tag = spawn(at, gp, s);
                tags.put(gp.id, tag);
            } else {
                tag.teleport(at);
            }
            for (GamePlayer other : game.active()) {
                Player viewer = other.player();
                if (viewer == null) continue;
                boolean may = other != gp && (other.team == gp.team || s.nametagsEnemies);
                if (may != viewer.canSee(tag)) {
                    if (may) viewer.showEntity(game.plugin, tag);
                    else viewer.hideEntity(game.plugin, tag);
                }
            }
        }
        for (Iterator<Map.Entry<UUID, TextDisplay>> it = tags.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, TextDisplay> e = it.next();
            if (wearing.contains(e.getKey())) continue;
            e.getValue().remove();
            it.remove();
        }
    }

    /** Every tag down: the match is over, or the option is off. */
    void clear() {
        for (TextDisplay tag : tags.values()) if (tag.isValid()) tag.remove();
        tags.clear();
    }

    private TextDisplay spawn(Location at, GamePlayer gp, Settings s) {
        String text = Text.legacy(Text.mm(s.nametagFormat
                .replace("{team_colour}", gp.team.color().asHexString())
                .replace("{player}", Text.MM.escapeTags(gp.name))));
        return at.getWorld().spawn(at, TextDisplay.class, e -> {
            e.setPersistent(false);
            // Shown to nobody until tick() decides who may read it.
            e.setVisibleByDefault(false);
            e.setText(text);
            e.setBillboard(Display.Billboard.CENTER);
            e.setSeeThrough(false);
            e.setShadowed(false);
            e.setDefaultBackground(false);
            // The game's own tag's backing: black at a quarter.
            e.setBackgroundColor(Color.fromARGB(64, 0, 0, 0));
            // Other players are drawn sliding over three ticks to where they are; so is their name.
            e.setTeleportDuration(3);
            e.addScoreboardTag("paintball");
        });
    }
}
