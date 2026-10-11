package ai.resourcepack.addons.paintball.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * The look of every word the plugin says, and how it reaches a player on
 * Spigot and Paper alike.
 *
 * <p>Text is MiniMessage, through the plugin's own copy of Adventure (shaded
 * and relocated: Spigot has none, and Paper's must not meet it). It leaves
 * here one of two ways, both of which both servers have: chat and the action
 * bar as BungeeCord components, which carry everything, and item names, lore
 * and the player list as legacy text with hex colours, which carries
 * everything the plugin writes there except a key binding (that becomes the
 * key's default, {@link #KEYS}).
 */
public final class Text {
    private Text() {
    }

    public static final MiniMessage MM = MiniMessage.miniMessage();

    public static final TextColor BODY = TextColor.color(0xE8EAED);
    public static final TextColor MUTED = TextColor.color(0x9AA0A6);
    public static final TextColor DIM = TextColor.color(0x5F6368);

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

    public static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ---- out -----------------------------------------------------------------------

    /** A line of chat, to a player or the console. */
    public static void send(CommandSender to, Component text) {
        to.spigot().sendMessage(bungee(text));
    }

    /** The line above the hotbar. */
    public static void actionBar(Player to, Component text) {
        to.spigot().sendMessage(ChatMessageType.ACTION_BAR, bungee(text));
    }

    public static BaseComponent bungee(Component text) {
        return ComponentSerializer.deserialize(GsonComponentSerializer.gson().serialize(text));
    }

    /**
     * What a key binding reads as where a binding cannot go (legacy text): the
     * key it is on unless somebody moved it.
     */
    private static final Map<String, String> KEYS = Map.of(
            "key.swapOffhand", "F", "key.drop", "Q", "key.inventory", "E", "key.playerlist", "Tab",
            "key.attack", "Left Click", "key.use", "Right Click", "key.jump", "Space", "key.sneak", "Shift");

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .flattener(ComponentFlattener.basic().toBuilder()
                    .mapper(KeybindComponent.class, k -> KEYS.getOrDefault(k.keybind(), k.keybind()))
                    .build())
            .build();

    /** As legacy text with hex colours: an item's name or lore line, the player list. */
    public static String legacy(Component text) {
        return LEGACY.serialize(text);
    }
}
