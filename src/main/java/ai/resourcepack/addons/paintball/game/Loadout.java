package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.Messages;
import ai.resourcepack.addons.paintball.Skin;
import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.UseCooldown;
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
    static Skin skin(ai.resourcepack.addons.paintball.Settings s, Team team) {
        return team == Team.BLUE ? s.blueSkin : s.redSkin;
    }

    /** The grenade's cooldown group: its recharge greys out the grenade and nothing else (the marker is paper too). */
    public static final Key GRENADE_COOLDOWN = Key.key("paintball", "grenade");

    public static boolean isMarker(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("marker_");
    }

    /** Whether a marker is the aimed twin. */
    public static boolean isAimed(Content content, ItemStack stack) {
        String id = content.idOf(stack);
        return id != null && id.startsWith("marker_") && id.endsWith("_aim");
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
        return marker(content, skin, false);
    }

    /**
     * The marker, hip or aimed. Both are PAPER, so swapping one for the other in
     * the hand does not stop a held trigger: the game only drops an item in use
     * when the item itself changes, and its components are not the item.
     *
     * <p>A left click aims rather than swings: no swing animation, and no reach
     * to hit anybody with, so every left click is a miss the server hears.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack marker(Content content, Skin skin, boolean aimed) {
        // The name and the lore are the item's own (items/markers.yml); what is
        // added here is how it behaves.
        ItemStack item = content.item(aimed ? skin.aimedItem() : skin.item());
        item.setData(DataComponentTypes.SWING_ANIMATION, SwingAnimation.swingAnimation()
                .type(SwingAnimation.Animation.NONE).build());
        item.setData(DataComponentTypes.ATTACK_RANGE, AttackRange.attackRange()
                .minReach(0f).maxReach(0f).minCreativeReach(0f).maxCreativeReach(0f).hitboxMargin(0f).build());
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

    @SuppressWarnings("UnstableApiUsage")
    static ItemStack grenade(Content content, Team team, int cooldownTicks) {
        ItemStack item = content.item("grenade_" + team.id());
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(Math.max(0.05f, cooldownTicks / 20f))
                .cooldownGroup(GRENADE_COOLDOWN).build());
        return item;
    }

    /** Dresses a player for the match: marker in hand, grenade beside it, team mask and suit. */
    static void equip(Content content, ai.resourcepack.addons.paintball.Settings s, Player p, GamePlayer gp) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setItem(0, marker(content, skin(s, gp.team)));
        inv.setItem(1, grenade(content, gp.team, s.grenadeCooldownTicks));
        inv.setHelmet(mask(content, gp.team));
        inv.setChestplate(armour(Material.LEATHER_CHESTPLATE, gp.team));
        inv.setLeggings(armour(Material.LEATHER_LEGGINGS, gp.team));
        inv.setBoots(armour(Material.LEATHER_BOOTS, gp.team));
        inv.setHeldItemSlot(0);
    }


    // ---- the lobby -----------------------------------------------------------------

    public static final String LEAVE = "leave";
    public static final String SCOREBOARD = "scoreboard";
    public static final String PICK_RED = "pick_red";
    public static final String PICK_BLUE = "pick_blue";

    /** The marker's name in the HUD, from its skin. */
    static String skinName(ai.resourcepack.addons.paintball.Settings s, GamePlayer gp) {
        return skin(s, gp.team).display().toUpperCase();
    }

    /** In the lobby: the marker to look at, a side to pick (lit while picked; again for neither), the last scoreboard, and the way out. */
    static void lobby(Content content, Messages messages, Player p, Team pick) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack marker = marker(content, Skin.EMBER);
        marker.unsetData(DataComponentTypes.CONSUMABLE);
        inv.setItem(0, marker);
        inv.setItem(3, picked(lobbyItem(new ItemStack(Material.RED_CONCRETE), PICK_RED, messages, "item-team-red"), pick == Team.RED));
        inv.setItem(5, picked(lobbyItem(new ItemStack(Material.BLUE_CONCRETE), PICK_BLUE, messages, "item-team-blue"), pick == Team.BLUE));
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
