package ai.resourcepack.addons.paintball.team;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;

import java.util.Locale;

/**
 * The paint a player fires and wears: their balls, splats, grenade, mine, mask
 * and suit, and how they look in the kill feed and on a splatted player's
 * screen. In a team mode it is the team's (red or blue); in a free for all
 * every player has one of their own.
 *
 * <p>The palette is fixed because the pack is: every colour here has its own
 * textures (tools/make_assets.py) and its own address in the free-for-all HUD,
 * so a server picks from these and cannot add one.
 */
public enum Paint {
    RED("Red", 0xFF4655, 0xFF8A93, Skin.EMBER),
    BLUE("Blue", 0x2E9BFF, 0x8CC8FF, Skin.TIDE),
    GREEN("Green", 0x5BE37D, 0xA4F2B6, Skin.TOXIC),
    ORANGE("Orange", 0xFF8A3D, 0xFFB98A, Skin.SUNSET),
    YELLOW("Yellow", 0xFFD23F, 0xFFE58C, Skin.GOLD),
    PURPLE("Purple", 0xA45CFF, 0xCBA3FF, Skin.VIOLET),
    PINK("Pink", 0xFF5CC8, 0xFFA3E0, Skin.BUBBLEGUM),
    CYAN("Cyan", 0x3DE0E0, 0x9AF0F0, Skin.FROST);

    private final String display;
    private final int rgb;
    private final int lightRgb;
    private final Skin skin;

    Paint(String display, int rgb, int lightRgb, Skin skin) {
        this.display = display;
        this.rgb = rgb;
        this.lightRgb = lightRgb;
        this.skin = skin;
    }

    public String display() {
        return display;
    }

    /** Its name in item ids and config: "red", "purple". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public TextColor color() {
        return TextColor.color(rgb);
    }

    public TextColor light() {
        return TextColor.color(lightRgb);
    }

    public Color bukkitColor() {
        return Color.fromRGB(rgb);
    }

    /** The marker this colour carries unless config.yml says otherwise. */
    public Skin skin() {
        return skin;
    }

    public static Paint parse(String s) {
        if (s == null) return null;
        for (Paint p : values()) if (p.id().equalsIgnoreCase(s.trim())) return p;
        return null;
    }
}
