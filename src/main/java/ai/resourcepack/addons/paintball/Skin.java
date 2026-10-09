package ai.resourcepack.addons.paintball;

import net.kyori.adventure.text.format.TextColor;

/** The marker's looks: one model, a texture each. */
public enum Skin {
    CLASSIC("Classic", "Black anodised, red trim. The one everybody starts with.", 0xFF4655),
    ARCTIC("Arctic", "Frosted white with ice-blue hardware.", 0x8FD8FF),
    TOXIC("Toxic", "Gunmetal and a glowing green you can see from the other base.", 0x7CFF4F),
    SUNSET("Sunset", "Violet body, orange and pink fade.", 0xFF8A3D),
    GOLD("Gold", "Black and gold. For the ones who never miss.", 0xFFC94A);

    private final String display;
    private final String blurb;
    private final int rgb;

    Skin(String display, String blurb, int rgb) {
        this.display = display;
        this.blurb = blurb;
        this.rgb = rgb;
    }

    public String display() {
        return display;
    }

    public String blurb() {
        return blurb;
    }

    public TextColor color() {
        return TextColor.color(rgb);
    }

    /** The RP Engine item id, under the paintball namespace. */
    public String item() {
        return "marker_" + name().toLowerCase();
    }

    public static Skin parse(String s) {
        if (s == null) return CLASSIC;
        for (Skin k : values()) if (k.name().equalsIgnoreCase(s)) return k;
        return CLASSIC;
    }
}
