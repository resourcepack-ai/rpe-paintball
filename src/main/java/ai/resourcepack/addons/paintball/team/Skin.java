package ai.resourcepack.addons.paintball.team;

import net.kyori.adventure.text.format.TextColor;

/** The marker's looks: one model, a texture each, and an aimed twin of each (items/markers.yml). */
public enum Skin {
    EMBER("Ember", 0xFF4655),
    TIDE("Tide", 0x2E9BFF),
    TOXIC("Toxic", 0x7CFF4F),
    SUNSET("Sunset", 0xFF8A3D),
    GOLD("Gold", 0xFFC94A);

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
