package ai.resourcepack.addons.paintball.team;

import net.kyori.adventure.text.format.TextColor;

/**
 * The marker's looks: one model, a texture each, and an aimed twin of each
 * (items/markers.yml). The last three were added for free for all, so that
 * each of its paint colours has a marker of its own.
 */
public enum Skin {
    EMBER("Ember", 0xFF4655),
    TIDE("Tide", 0x2E9BFF),
    TOXIC("Toxic", 0x7CFF4F),
    SUNSET("Sunset", 0xFF8A3D),
    GOLD("Gold", 0xFFC94A),
    VIOLET("Violet", 0xA45CFF),
    BUBBLEGUM("Bubblegum", 0xFF5CC8),
    FROST("Frost", 0x3DE0E0);

    private final String display;
    private final int rgb;

    Skin(String display, int rgb) {
        this.display = display;
        this.rgb = rgb;
    }

    public String display() {
        return display;
    }

    public TextColor color() {
        return TextColor.color(rgb);
    }

    /** The RP Engine item id, under the paintball namespace. */
    public String item() {
        return "marker_" + name().toLowerCase();
    }

    /** The same marker aimed: everybody sees the marker, its holder sees down the sights. */
    public String aimedItem() {
        return item() + "_aim";
    }

    public static Skin parse(String s) {
        if (s == null) return EMBER;
        for (Skin k : values()) if (k.name().equalsIgnoreCase(s)) return k;
        return EMBER;
    }
}
