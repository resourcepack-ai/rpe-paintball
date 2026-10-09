package ai.resourcepack.addons.paintball;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Every word a player reads, from the {@code messages:} section of config.yml.
 *
 * <p>MiniMessage, with {@code {name}} placeholders filled from the pairs each
 * call passes. A value is escaped before it goes in, so a player called
 * {@code <red>} is a name and not a colour. A key the server's file does not
 * have falls back to the one in the jar's config.yml, so an older config keeps
 * working after an update adds a message.
 *
 * <p>{@code {prefix}} anywhere in a message is the {@code prefix} message, so
 * the brand in front of chat lines is changed in one place.
 */
public final class Messages {
    private final ConfigurationSection own;
    private final ConfigurationSection defaults;

    public Messages(FileConfiguration config) {
        this.own = config.getConfigurationSection("messages");
        this.defaults = config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection("messages");
    }

    /** The message as written, or the key itself when there is none anywhere. */
    public String raw(String key) {
        String s = own == null ? null : own.getString(key);
        if (s == null && defaults != null) s = defaults.getString(key);
        return s == null ? key : s;
    }

    /** As a component: MiniMessage, placeholders filled with escaped values. */
    public Component component(String key, String... pairs) {
        return Text.mm(fill(key, true, pairs));
    }

    /**
     * As plain text, for an overlay value: the HUD draws its runs in their own
     * colours, so any formatting here is taken out rather than drawn.
     */
    public String plain(String key, String... pairs) {
        return PlainTextComponentSerializer.plainText().serialize(Text.mm(fill(key, true, pairs)));
    }

    private String fill(String key, boolean escape, String... pairs) {
        String s = raw(key);
        if (s.contains("{prefix}") && !key.equals("prefix")) s = s.replace("{prefix}", raw("prefix"));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = pairs[i + 1] == null ? "" : pairs[i + 1];
            s = s.replace("{" + pairs[i] + "}", escape ? Text.MM.escapeTags(value) : value);
        }
        return s;
    }
}
