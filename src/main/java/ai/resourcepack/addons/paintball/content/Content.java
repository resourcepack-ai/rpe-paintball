package ai.resourcepack.addons.paintball.content;

import ai.resourcepack.engine.RPEnginePlugin;
import ai.resourcepack.engine.api.ContentId;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The paintball content folder, as the engine holds it: items, icon glyphs and
 * sounds by id. Each lookup has a vanilla fallback, so a pack that failed to
 * build costs looks, never a broken match.
 */
public final class Content {
    public static final String NS = "paintball";

    private final RPEnginePlugin engine;

    public Content(RPEnginePlugin engine) {
        this.engine = engine;
    }

    public RPEnginePlugin engine() {
        return engine;
    }

    public static ContentId id(String path) {
        return ContentId.parse(NS + ":" + path).orElseThrow();
    }

    private final java.util.Set<String> missing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A fresh stack of one of our items, or paper if the engine does not have
     * it, which is said once in the log: a match played with plain paper is
     * otherwise a mystery (nothing fires, nothing is aimed).
     */
    public ItemStack item(String path) {
        return engine.items().create(id(path)).orElseGet(() -> {
            if (missing.add(path)) {
                org.bukkit.Bukkit.getLogger().warning("[RPEPaintball] RP Engine has no item " + NS + ":" + path
                        + ". Is the paintball content installed (plugins/RPEngine/content/paintball) and loaded (/rp reload)?");
            }
            return new ItemStack(Material.PAPER);
        });
    }

    public boolean is(ItemStack stack, String path) {
        return stack != null && engine.items().is(stack, id(path));
    }

    /** The id of one of our items, without the namespace, or null. */
    public String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        return engine.items().idOf(stack)
                .filter(id -> id.namespace().equals(NS))
                .map(ContentId::path)
                .orElse(null);
    }

    /** An icon's character, or the fallback when the pack has none. */
    public String icon(String path, String fallback) {
        return engine.icons().character(id(path)).orElse(fallback);
    }

    public void sound(Location at, String path, float volume, float pitch, Sound fallback) {
        if (!engine.sounds().playAt(at, id(path), volume, pitch) && fallback != null) {
            at.getWorld().playSound(at, fallback, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    public void sound(Player to, String path, float volume, float pitch, Sound fallback) {
        if (!engine.sounds().play(to, id(path), volume, pitch) && fallback != null) {
            to.playSound(to.getLocation(), fallback, SoundCategory.PLAYERS, volume, pitch);
        }
    }
}
