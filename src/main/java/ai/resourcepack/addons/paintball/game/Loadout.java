package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.Messages;
import ai.resourcepack.addons.paintball.Skin;
import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.UseEffects;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
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

    /** Each team carries its own colourway of the one marker model. */
    static Skin skin(Team team) {
        return team == Team.BLUE ? Skin.ARCTIC : Skin.CLASSIC;
    }

    public static boolean isMarker(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("marker_");
    }

    public static boolean isGrenade(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("grenade_");
    }

    /**
     * A marker in a skin. Right-click "eats" it for an hour with no animation and
     * no sound, which is how the server learns the trigger is held, every tick;
     * {@code use_effects} keeps the player at full speed while it is.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack marker(Content content, Skin skin) {
        // The name and the lore are the item's own (items/markers.yml); what is
        // added here is how it behaves.
        ItemStack item = content.item(skin.item());
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(3600)
                .animation(ItemUseAnimation.NONE)
                .sound(Key.key("minecraft", "intentionally_empty"))
                .hasConsumeParticles(false)
                .build());
        item.setData(DataComponentTypes.USE_EFFECTS, UseEffects.useEffects()
                .canSprint(true)
                .speedMultiplier(1.0f)
                .interactVibrations(false)
                .build());
        return item;
    }

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack mask(Content content, Team team) {
        ItemStack item = content.item("mask_" + team.id());
        // Worn on the head: the model is drawn there, and the goggles' frame is
        // drawn round the wearer's own view, like a carved pumpkin's.
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .cameraOverlay(Key.key("paintball", "misc/mask_overlay"))
                .swappable(false)
                .dispensable(false)
                .damageOnHurt(false)
                .equipSound(Key.key("minecraft", "intentionally_empty"))
                .build());
        return item;
    }

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack armour(Material material, Team team) {
        ItemStack item = new ItemStack(material);
        item.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(team.bukkitColor()));
        item.setData(DataComponentTypes.UNBREAKABLE);
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return item;
    }

    static ItemStack grenade(Content content, Team team, int count) {
        ItemStack item = content.item("grenade_" + team.id());
        item.setAmount(Math.max(1, count));
        return item;
    }

    /** Dresses a player for the match: marker in hand, grenade beside it, team mask and suit. */
    static void equip(Content content, Player p, GamePlayer gp) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, marker(content, skin(gp.team)));
        if (gp.grenades > 0) inv.setItem(1, grenade(content, gp.team, gp.grenades));
        inv.setHelmet(mask(content, gp.team));
        inv.setChestplate(armour(Material.LEATHER_CHESTPLATE, gp.team));
        inv.setLeggings(armour(Material.LEATHER_LEGGINGS, gp.team));
        inv.setBoots(armour(Material.LEATHER_BOOTS, gp.team));
        inv.setHeldItemSlot(0);
    }

    static void grenades(Content content, Player p, GamePlayer gp) {
        p.getInventory().setItem(1, gp.grenades > 0 ? grenade(content, gp.team, gp.grenades) : null);
    }

    // ---- the lobby -----------------------------------------------------------------

    public static final String LEAVE = "leave";
    public static final String SCOREBOARD = "scoreboard";

    /** The marker's name in the HUD, from its skin. */
    static String skinName(GamePlayer gp) {
        return skin(gp.team).display().toUpperCase();
    }

    /** In the lobby: the marker to look at, the last scoreboard, and the way out. */
    static void lobby(Content content, Messages messages, Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack marker = marker(content, Skin.CLASSIC);
        marker.unsetData(DataComponentTypes.CONSUMABLE);
        inv.setItem(0, marker);
        inv.setItem(7, lobbyItem(new ItemStack(Material.GOLD_INGOT), SCOREBOARD, messages, "item-scoreboard"));
        inv.setItem(8, lobbyItem(new ItemStack(Material.BARRIER), LEAVE, messages, "item-leave"));
        inv.setHeldItemSlot(0);
    }

    private static ItemStack lobbyItem(ItemStack item, String action, Messages messages, String key) {
        ItemMeta meta = item.getItemMeta();
        meta.itemName(messages.component(key));
        meta.lore(List.of(messages.component(key + "-lore"), Component.empty(), messages.component("item-use")));
        meta.getPersistentDataContainer().set(LOBBY_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    public static String lobbyAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(LOBBY_KEY, PersistentDataType.STRING);
    }
}
