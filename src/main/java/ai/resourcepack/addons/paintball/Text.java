package ai.resourcepack.addons.paintball;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.UUID;

/**
 * The look of every word the plugin says: one palette, one prefix, and the
 * arithmetic for lining text up in a font that is not monospaced.
 */
public final class Text {
    private Text() {
    }

    public static final MiniMessage MM = MiniMessage.miniMessage();

    public static final TextColor ACCENT = TextColor.color(0xFFD23F);
    public static final TextColor BODY = TextColor.color(0xE8EAED);
    public static final TextColor MUTED = TextColor.color(0x9AA0A6);
    public static final TextColor DIM = TextColor.color(0x5F6368);
    public static final TextColor GOOD = TextColor.color(0x5BE37D);
    public static final TextColor BAD = TextColor.color(0xFF5C5C);
    public static final TextColor GOLD = TextColor.color(0xFFC94A);
    public static final TextColor SILVER = TextColor.color(0xC9D3DD);
    public static final TextColor BRONZE = TextColor.color(0xD08A4E);

    /** "PAINTBALL" in a red-to-blue sweep, the two teams meeting in the middle. */
    public static final String BRAND = "<bold><gradient:#FF4655:#FFD23F:#2E9BFF>PAINTBALL</gradient></bold>";

    public static Component mm(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    /** A chat line: the brand, a thin divider and the message in the body colour. */
    public static Component msg(String miniMessage, TagResolver... resolvers) {
        return mm(BRAND + " <#5F6368>▏</#5F6368> <#E8EAED>" + miniMessage, resolvers);
    }

    public static Component error(String miniMessage, TagResolver... resolvers) {
        return mm(BRAND + " <#5F6368>▏</#5F6368> <#FF5C5C>" + miniMessage, resolvers);
    }

    public static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** A player's face as a glyph: the game draws it from their skin (1.21.9 and newer). */
    public static Component head(UUID id, String name) {
        return Component.object(ObjectContents.playerHead().id(id).name(name).hat(true).build());
    }

    /** A team's name in its colour. */
    public static Component team(Team team) {
        return Component.text(team.display().toUpperCase(), team.color()).decorate(TextDecoration.BOLD);
    }

    /** A name in its team's colour, with their face in front. */
    public static Component player(UUID id, String name, Team team) {
        return Component.text().append(head(id, name)).append(Component.text(" "))
                .append(Component.text(name, team == null ? BODY : team.color())).build();
    }

    // ---- width -------------------------------------------------------------------

    /** Vanilla's ascii font: the glyphs narrower than the usual five pixels. */
    private static final String NARROW = "!.,:;i|'`lI[]\"* ()t{}fk<>@~";
    private static final int[] NARROW_WIDTH = {1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 6, 6};

    /** How many pixels a run of plain ASCII text advances (a head glyph is {@link #HEAD_WIDTH}). */
    public static int width(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int at = NARROW.indexOf(s.charAt(i));
            w += (at < 0 ? 5 : NARROW_WIDTH[at]) + 1 + (bold ? 1 : 0);
        }
        return w;
    }

    /** A head glyph is eight pixels and the gap after it. */
    public static final int HEAD_WIDTH = 9;

    /**
     * Blank space as close to {@code px} wide as the font allows. A space is 4
     * pixels and a bold space 5, and every width from 12 up is a sum of fours
     * and fives, so above that this is exact.
     */
    public static Component pad(int px) {
        if (px <= 0) return Component.empty();
        int fives = 0;
        while (fives * 5 <= px && (px - fives * 5) % 4 != 0) fives++;
        int rest = px - fives * 5;
        if (rest < 0 || rest % 4 != 0) {
            // Under 12 not every width can be made; round to the nearest four.
            return Component.text(" ".repeat(Math.max(0, Math.round(px / 4f))));
        }
        TextComponent.Builder b = Component.text();
        if (rest > 0) b.append(Component.text(" ".repeat(rest / 4)));
        if (fives > 0) b.append(Component.text(" ".repeat(fives)).decorate(TextDecoration.BOLD));
        return b.build();
    }

    public static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    public static TextColor rankColor(int rank) {
        return switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            case 3 -> BRONZE;
            default -> MUTED;
        };
    }

    public static Component white(String s) {
        return Component.text(s, NamedTextColor.WHITE);
    }
}
