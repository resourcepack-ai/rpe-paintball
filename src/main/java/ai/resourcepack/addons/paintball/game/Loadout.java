package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.content.Content;
import ai.resourcepack.addons.paintball.config.Messages;
import ai.resourcepack.addons.paintball.team.Paint;
import ai.resourcepack.addons.paintball.team.Skin;
import ai.resourcepack.addons.paintball.team.Team;
import ai.resourcepack.addons.paintball.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** What a player carries: in a match, and in the lobby. */
public final class Loadout {
    private Loadout() {
    }

    /** Marks a lobby item with what clicking it does. */
    public static NamespacedKey LOBBY_KEY;

    public static void init(org.bukkit.plugin.Plugin plugin) {
        LOBBY_KEY = new NamespacedKey(plugin, "lobby");
    }


    /** The grenade's cooldown group: its recharge greys out the grenade and nothing else (the marker is paper too). */
    public static final NamespacedKey GRENADE_COOLDOWN = new NamespacedKey("paintball", "grenade");

    /**
     * How a marker is held, as item components. Bukkit has no API for these
     * (they are 1.21.11's), so they go in as the string /give would take,
     * parsed by the server itself, on Spigot and Paper alike. A left click aims
     * rather than swings: no swing animation, and no reach to hit anybody with,
     * so every left click is a miss the server hears.
     */
    private static final String MARKER_HANDS = "minecraft:swing_animation={type:\"none\"},"
            + "minecraft:attack_range={min_reach:0f,max_reach:0f,min_creative_reach:0f,max_creative_reach:0f,hitbox_margin:0f}";

    /**
     * How it fires. Right-click "eats" it for an hour with no animation and no
     * sound, which is how the server learns the trigger is held, every tick;
     * use_effects keeps the player at full speed while it is.
     */
    private static final String MARKER_TRIGGER = "minecraft:consumable={consume_seconds:3600f,animation:\"none\","
            + "sound:\"minecraft:intentionally_empty\",has_consume_particles:false},"
            + "minecraft:use_effects={can_sprint:true,speed_multiplier:1.0f,interact_vibrations:false}";

    private static boolean warnedComponents;

    /**
     * {@code item} with {@code extra} components added, through the server's
     * own item parser: the item's components as a string, and these after them.
     * An item the parser refuses comes back unchanged, said once in the log,
     * because a marker without them still fires, only with a swing.
     */
    static ItemStack withComponents(ItemStack item, String extra) {
        ItemMeta meta = item.getItemMeta();
        String own = meta == null ? "[]" : meta.getAsComponentString();
        String inner = own.length() >= 2 ? own.substring(1, own.length() - 1) : "";
        String spec = "minecraft:" + item.getType().name().toLowerCase(java.util.Locale.ROOT) + "[" + (inner.isEmpty() ? extra : inner + "," + extra) + "]";
        try {
            ItemStack out = Bukkit.getItemFactory().createItemStack(spec);
            out.setAmount(item.getAmount());
            return out;
        } catch (IllegalArgumentException e) {
            if (!warnedComponents) {
                warnedComponents = true;
                Bukkit.getLogger().warning("[RPEPaintball] The server would not take the marker's components ("
                        + e.getMessage() + "). It still fires, but a left click swings instead of aiming.");
            }
            return item;
        }
    }

    public static boolean isMarker(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("marker_");
    }

    /** Whether a marker is the aimed twin. */
    public static boolean isAimed(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("marker_") && id.endsWith("_aim");
    }

    public static boolean isMine(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("mine_");
    }

    public static boolean isGrenade(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("grenade_");
    }

    /** A marker in a skin, from the hip. */
    public static ItemStack marker(Content content, Skin skin) {
        return marker(content, skin, false);
    }

    /**
     * The marker, hip or aimed. Both are PAPER, so swapping one for the other in
     * the hand does not stop a held trigger: the game only drops an item in use
     * when the item itself changes, and its components are not the item.
     */
    public static ItemStack marker(Content content, Skin skin, boolean aimed) {
        return marker(content, skin, aimed, true);
    }

    /** The marker; {@code fires} false for the lobby's, which is only to look at. */
    static ItemStack marker(Content content, Skin skin, boolean aimed, boolean fires) {
        // The name and the lore are the item's own (items/markers.yml); what is
        // added here is how it behaves.
        ItemStack item = content.item(aimed ? skin.aimedItem() : skin.item());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(1);
            item.setItemMeta(meta);
        }
        return withComponents(item, fires ? MARKER_HANDS + "," + MARKER_TRIGGER : MARKER_HANDS);
    }

    static ItemStack mask(Content content, Paint paint) {
        ItemStack item = content.item("mask_" + paint.id());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        // Worn on the head: the model is drawn there, and the goggles' frame is
        // drawn round the wearer's own view, like a carved pumpkin's.
        EquippableComponent worn = meta.getEquippable();
        worn.setSlot(EquipmentSlot.HEAD);
        worn.setCameraOverlay(new NamespacedKey("paintball", "misc/mask_overlay"));
        worn.setSwappable(false);
        worn.setDispensable(false);
        worn.setDamageOnHurt(false);
        worn.setEquipSound(Sound.INTENTIONALLY_EMPTY);
        meta.setEquippable(worn);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack armour(Material material, Paint paint) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(paint.bukkitColor());
            meta.setUnbreakable(true);
            meta.setHideTooltip(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack grenade(Content content, Paint paint, int cooldownTicks) {
        ItemStack item = content.item("grenade_" + paint.id());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setMaxStackSize(1);
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(Math.max(0.05f, cooldownTicks / 20f));
        cooldown.setCooldownGroup(GRENADE_COOLDOWN);
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
        return item;
    }

    /** The mines a player has still to set, as one stack. */
    static ItemStack mine(Content content, Paint paint, int left) {
        ItemStack item = content.item("mine_" + paint.id());
        item.setAmount(Math.max(1, Math.min(99, left)));
        return item;
    }

    /** Dresses a player for the match: marker in hand, grenade beside it, any mines left, mask and suit in their paint. */
    static void equip(Content content, ai.resourcepack.addons.paintball.config.Settings s, Player p, GamePlayer gp, Skin skin) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, marker(content, skin));
        inv.setItem(1, grenade(content, gp.paint, s.grenadeCooldownTicks));
        if (gp.minesLeft > 0) inv.setItem(2, mine(content, gp.paint, gp.minesLeft));
        inv.setHelmet(mask(content, gp.paint));
        inv.setChestplate(armour(Material.LEATHER_CHESTPLATE, gp.paint));
        inv.setLeggings(armour(Material.LEATHER_LEGGINGS, gp.paint));
        inv.setBoots(armour(Material.LEATHER_BOOTS, gp.paint));
        inv.setHeldItemSlot(0);
    }


    // ---- the lobby -----------------------------------------------------------------

    public static final String LEAVE = "leave";
    public static final String SCOREBOARD = "scoreboard";
    public static final String PICK_RED = "pick_red";
    public static final String PICK_BLUE = "pick_blue";
    public static final String COLOUR = "colour";

    /**
     * In the lobby: the marker, a side to pick (lit while picked; again for
     * neither) when the next match has teams, the last scoreboard, and the way
     * out. With {@code colours} the marker is the colour picker, in the colour
     * chosen ({@code colour}, null for none yet) and carrying that colour's
     * marker ({@code skin}); a right-click moves it on.
     */
    static void lobby(Content content, Messages messages, Player p, Team pick, boolean teams,
                      boolean colours, Paint colour, Skin skin) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        if (colours) {
            ItemStack picker = marker(content, skin, false, false);
            ItemMeta meta = picker.getItemMeta();
            if (meta != null) {
                String hex = colour == null ? "#E8EAED" : colour.color().asHexString();
                String name = colour == null ? "Any" : colour.display();
                // The marker's own name would win over the item name.
                meta.setDisplayName(null);
                meta.setItemName(Text.legacy(messages.component("item-colour", "colour", hex, "name", name)));
                meta.setLore(List.of(Text.legacy(messages.component("item-colour-lore")), "", Text.legacy(messages.component("item-use"))));
                meta.getPersistentDataContainer().set(LOBBY_KEY, PersistentDataType.STRING, COLOUR);
                picker.setItemMeta(meta);
            }
            inv.setItem(0, picker);
        } else {
            inv.setItem(0, marker(content, Skin.EMBER, false, false));
        }
        if (teams) {
            inv.setItem(3, picked(lobbyItem(new ItemStack(Material.RED_CONCRETE), PICK_RED, messages, "item-team-red"), pick == Team.RED));
            inv.setItem(5, picked(lobbyItem(new ItemStack(Material.BLUE_CONCRETE), PICK_BLUE, messages, "item-team-blue"), pick == Team.BLUE));
        }
        inv.setItem(7, lobbyItem(new ItemStack(Material.GOLD_INGOT), SCOREBOARD, messages, "item-scoreboard"));
        inv.setItem(8, lobbyItem(new ItemStack(Material.BARRIER), LEAVE, messages, "item-leave"));
        inv.setHeldItemSlot(0);
    }

    private static ItemStack lobbyItem(ItemStack item, String action, Messages messages, String key) {
        ItemMeta meta = item.getItemMeta();
        meta.setItemName(Text.legacy(messages.component(key)));
        meta.setLore(List.of(Text.legacy(messages.component(key + "-lore")), "", Text.legacy(messages.component("item-use"))));
        meta.getPersistentDataContainer().set(LOBBY_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack picked(ItemStack item, boolean on) {
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(on);
        item.setItemMeta(meta);
        return item;
    }

    public static String lobbyAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(LOBBY_KEY, PersistentDataType.STRING);
    }
}
