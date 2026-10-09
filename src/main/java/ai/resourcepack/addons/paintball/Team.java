package ai.resourcepack.addons.paintball;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

/** The two sides. Everything a team looks like lives here, so one edit recolours it everywhere. */
public enum Team {
    RED("Red", 0xFF4655, 0xFF8A93, Material.RED_CONCRETE, BossBar.Color.RED),
    BLUE("Blue", 0x2E9BFF, 0x8CC8FF, Material.BLUE_CONCRETE, BossBar.Color.BLUE);

    private final String display;
    private final int rgb;
    private final int lightRgb;
    private final Material block;
    private final BossBar.Color bar;

    Team(String display, int rgb, int lightRgb, Material block, BossBar.Color bar) {
        this.display = display;
        this.rgb = rgb;
        this.lightRgb = lightRgb;
        this.block = block;
        this.bar = bar;
    }

    public String display() {
        return display;
    }

    public String id() {
        return name().toLowerCase();
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

    public Material block() {
        return block;
    }

    public BossBar.Color bar() {
        return bar;
    }

    public Team other() {
        return this == RED ? BLUE : RED;
    }

    public static Team parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "red", "r" -> RED;
            case "blue", "b" -> BLUE;
            default -> null;
        };
    }
}
