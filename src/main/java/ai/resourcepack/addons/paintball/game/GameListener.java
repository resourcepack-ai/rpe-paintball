package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.PaintballPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** The rules of being in a match that are not the match itself: no damage, no building, no dropping your marker. */
public final class GameListener implements Listener {
    /** Commands a player may still run while in a match. */
    private static final Set<String> ALLOWED = Set.of("paintball", "pb", "msg", "tell", "w", "r", "reply", "me", "list");

    private final PaintballPlugin plugin;
    private final GameManager games;
    private final Content content;

    public GameListener(PaintballPlugin plugin) {
        this.plugin = plugin;
        this.games = plugin.games();
        this.content = plugin.content();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        games.leave(e.getPlayer());
    }

    /** Somebody who was in a match when the server went down gets their things back. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.snapshots().has(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !games.playing(p) && plugin.snapshots().restore(p)) {
                p.sendMessage(plugin.messages().component("restored"));
            }
        }, 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && games.playing(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && games.playing(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (games.playing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (games.playing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && games.playing(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && games.playing(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && games.playing(p)) e.setCancelled(true);
    }

    /** Q never drops anything; with the marker in hand it reloads. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Game g = games.of(e.getPlayer());
        if (g == null) return;
        e.setCancelled(true);
        if (Loadout.isMarker(content, e.getItemDrop().getItemStack())) g.reload(e.getPlayer());
    }

    /** F opens the scoreboard: the nearest thing to Tab a server can hear. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = games.of(e.getPlayer());
        if (g == null) return;
        e.setCancelled(true);
        plugin.scoreboard().open(e.getPlayer(), g);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (Loadout.isMarker(content, e.getItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = games.of(p);
        if (g == null) return;
        if (e.getAction() == Action.PHYSICAL) {
            e.setCancelled(true);
            return;
        }
        // Never open doors, chests or trapdoors in the arena; using what is in your hand is fine.
        e.setUseInteractedBlock(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) return;
        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        ItemStack item = e.getItem();
        String lobby = Loadout.lobbyAction(item);
        if (lobby != null) {
            e.setCancelled(true);
            if (!right) return;
            switch (lobby) {
                case Loadout.SCOREBOARD -> plugin.scoreboard().open(p, g);
                case Loadout.LEAVE -> games.leave(p);
                default -> {
                }
            }
            return;
        }
        if (right && Loadout.isGrenade(content, item)) {
            e.setCancelled(true);
            g.throwGrenade(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (games.playing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!games.playing(p) || p.hasPermission("paintball.admin")) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!ALLOWED.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(plugin.messages().component("not-during-match"));
        }
    }

    /** Taken out of the arena's world by something else (another plugin, an op): that is leaving. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Game g = games.of(p);
        if (g == null || e.getTo().getWorld() == null) return;
        if (!e.getTo().getWorld().getName().equals(g.arena().worldName())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (games.of(p) == g) games.leave(p);
            });
        }
    }
}
