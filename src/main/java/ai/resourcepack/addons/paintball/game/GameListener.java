package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.command.Perms;
import ai.resourcepack.addons.paintball.text.Text;
import ai.resourcepack.addons.paintball.content.Content;
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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
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
    /** The tick each player last dropped (pressed Q), to tell the game's drop swing from a click. */
    private final java.util.Map<java.util.UUID, Long> dropped = new java.util.HashMap<>();

    public GameListener(PaintballPlugin plugin) {
        this.plugin = plugin;
        this.games = plugin.games();
        this.content = plugin.content();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dropped.remove(e.getPlayer().getUniqueId());
        games.leave(e.getPlayer());
    }

    /** Somebody who was in a match when the server went down gets their things back. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Game.clearModifiers(p, plugin);
        if (!plugin.snapshots().has(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !games.playing(p) && plugin.snapshots().restore(p)) {
                Text.send(p, plugin.messages().component("restored"));
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
        dropped.put(e.getPlayer().getUniqueId(), plugin.ticks());
        e.setCancelled(true);
        if (Loadout.isMarker(content, e.getItemDrop().getItemStack())) g.reload(e.getPlayer());
    }

    /** F opens the scoreboard: the nearest thing to Tab a server can hear. */
    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = games.of(e.getPlayer());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getPlayer().hasPermission(Perms.SCOREBOARD)) plugin.scoreboard().open(e.getPlayer(), g);
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
                case Loadout.SCOREBOARD -> {
                    if (p.hasPermission(Perms.SCOREBOARD)) plugin.scoreboard().open(p, g);
                }
                case Loadout.LEAVE -> games.leave(p);
                case Loadout.PICK_RED -> {
                    if (p.hasPermission(Perms.TEAM)) g.pick(p, ai.resourcepack.addons.paintball.team.Team.RED, true);
                }
                case Loadout.PICK_BLUE -> {
                    if (p.hasPermission(Perms.TEAM)) g.pick(p, ai.resourcepack.addons.paintball.team.Team.BLUE, true);
                }
                default -> {
                }
            }
            return;
        }
        if (right && Loadout.isGrenade(content, item)) {
            e.setCancelled(true);
            g.throwGrenade(p);
            return;
        }
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
        if (left && Loadout.isMarker(content, item)) e.setCancelled(true);
    }

    /**
     * A left click with the marker aims, or stops aiming. The swing is what the
     * server hears for every left click (the marker has no reach, so there is
     * never a hit instead, and no swing animation, so nothing is seen); the one
     * swing that is not a click is the one the game adds to dropping an item,
     * which here is Q to reload, so a swing straight after a drop is let go.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        Game g = games.of(p);
        if (g == null || !Loadout.isMarker(content, p.getInventory().getItemInMainHand())) return;
        Long drop = dropped.get(p.getUniqueId());
        if (drop != null && plugin.ticks() - drop <= 2) return;
        g.toggleAim(p);
    }

    /** Another slot in hand: the sights come down (the marker in the hotbar goes back to hip). */
    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Game g = games.of(e.getPlayer());
        if (g != null) g.lowerAim(e.getPlayer());
    }

    /** Nobody sprints down the sights. */
    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (!e.isSprinting()) return;
        Game g = games.of(e.getPlayer());
        if (g != null) g.lowerAim(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (games.playing(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!games.playing(p) || p.hasPermission(Perms.BYPASS_COMMANDS)) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!ALLOWED.contains(label)) {
            e.setCancelled(true);
            Text.send(p, plugin.messages().component("not-during-match"));
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
