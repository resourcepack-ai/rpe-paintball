package ai.resourcepack.addons.paintball.arena;

import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import ai.resourcepack.addons.paintball.game.GameManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The op side of an arena: every change the setup commands and the setup
 * dialog make goes through here, so both say the same things and save the
 * same way. Also the bounds wand.
 */
public final class ArenaSetup implements Listener {
    private final ArenaStore store;
    private final GameManager games;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> picks = new HashMap<>();

    public ArenaSetup(Plugin plugin, ArenaStore store, GameManager games) {
        this.store = store;
        this.games = games;
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public Arena create(Player p, String id, String name) {
        id = id.toLowerCase();
        if (!ArenaStore.ID.matcher(id).matches()) {
            p.sendMessage(Text.error("An arena id is lower-case letters, digits, - and _ (up to 32)."));
            return null;
        }
        if (store.get(id) != null) {
            p.sendMessage(Text.error("There's already an arena called " + id + "."));
            return null;
        }
        Arena a = new Arena(id, name == null || name.isBlank() ? pretty(id) : name, p.getWorld().getName());
        store.put(a);
        p.sendMessage(Text.msg("Created <#FFD23F>" + a.name() + "</#FFD23F> in <white>" + a.worldName()
                + "</white>. Now: setwaiting, addspawn red/blue, wand + setbounds, enable."));
        return a;
    }

    private static String pretty(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("[-_]")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private boolean sameWorld(Player p, Arena a) {
        if (a.waiting() == null && a.spawnCount(Team.RED) == 0 && a.spawnCount(Team.BLUE) == 0 && a.bounds() == null) return true;
        if (p.getWorld().getName().equals(a.worldName())) return true;
        p.sendMessage(Text.error(a.name() + " is in " + a.worldName() + "; go there first."));
        return false;
    }

    private boolean idle(Player p, Arena a) {
        if (games.existing(a.id()) != null && games.existing(a.id()).live()) {
            p.sendMessage(Text.error("A match is being played on " + a.name() + ". <white>/pb stop " + a.id() + "</white> first."));
            return false;
        }
        return true;
    }

    public void setWaiting(Player p, Arena a) {
        if (!sameWorld(p, a)) return;
        a.waiting(p.getLocation());
        store.save();
        p.sendMessage(Text.msg("Waiting spot set."));
    }

    public void addSpawn(Player p, Arena a, Team t) {
        if (!sameWorld(p, a) || !idle(p, a)) return;
        Location at = p.getLocation();
        at.setX(Math.floor(at.getX()) + 0.5);
        at.setZ(Math.floor(at.getZ()) + 0.5);
        at.setPitch(0);
        if (a.spawnCount(Team.RED) + a.spawnCount(Team.BLUE) == 0 && a.waiting() == null) {
            a.waiting(at);
        }
        a.addSpawn(t, at);
        store.save();
        p.sendMessage(Text.msg("Added a <" + t.color().asHexString() + ">" + t.display().toLowerCase() + "</"
                + t.color().asHexString() + "> spawn <#9AA0A6>(" + a.spawnCount(t) + "/" + Arena.MAX_SPAWNS + ")"));
    }

    public void clearSpawns(Player p, Arena a, Team t) {
        if (!idle(p, a)) return;
        a.clearSpawns(t);
        store.save();
        p.sendMessage(Text.msg("Cleared the " + t.display().toLowerCase() + " spawns."));
    }

    public void setBounds(Player p, Arena a) {
        Location[] pick = picks.get(p.getUniqueId());
        if (pick == null || pick[0] == null || pick[1] == null) {
            p.sendMessage(Text.error("Mark two corners with the wand first: <white>/pb arena wand"));
            return;
        }
        if (!pick[0].getWorld().equals(pick[1].getWorld()) || !sameWorld(p, a) || !idle(p, a)) return;
        BoundingBox box = BoundingBox.of(pick[0].getBlock(), pick[1].getBlock());
        a.bounds(box);
        store.save();
        p.sendMessage(Text.msg("Bounds set: <white>" + (int) box.getWidthX() + "×" + (int) box.getHeight() + "×" + (int) box.getWidthZ() + "</white>."));
    }

    public void bounds(Arena a, BoundingBox box) {
        a.bounds(box);
        store.save();
    }

    public void enable(Player p, Arena a, boolean on) {
        if (on) {
            List<String> missing = a.missing();
            if (!missing.isEmpty()) {
                p.sendMessage(Text.error(a.name() + " still needs " + String.join(", ", missing) + "."));
                return;
            }
        } else if (!idle(p, a)) {
            return;
        }
        a.enabled(on);
        store.save();
        p.sendMessage(Text.msg(a.name() + " is " + (on ? "<#5BE37D>open</#5BE37D>. Players can <white>/pb join</white>." : "<#FF5C5C>closed</#FF5C5C>.")));
    }

    // ---- the bounds wand -------------------------------------------------------------

    public ItemStack wand() {
        ItemStack w = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = w.getItemMeta();
        m.itemName(Text.mm("<bold><#FFD23F>Arena bounds wand"));
        m.lore(List.of(Text.mm("<#9AA0A6>Left-click a block: first corner"), Text.mm("<#9AA0A6>Right-click a block: second corner"),
                Component.empty(), Text.mm("<#9AA0A6>Then <white>/pb arena setbounds <id>")));
        m.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        m.setEnchantmentGlintOverride(true);
        w.setItemMeta(m);
        return w;
    }

    @EventHandler
    public void onWand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || !e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().getPersistentDataContainer().has(wandKey)) return;
        if (!e.getPlayer().hasPermission("paintball.admin")) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        int i;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) i = 0;
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) i = 1;
        else return;
        e.setCancelled(true);
        Location[] pick = picks.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new Location[2]);
        pick[i] = b.getLocation();
        String size = "";
        if (pick[0] != null && pick[1] != null && pick[0].getWorld().equals(pick[1].getWorld())) {
            BoundingBox box = BoundingBox.of(pick[0].getBlock(), pick[1].getBlock());
            size = "  <#9AA0A6>(" + (int) box.getWidthX() + "×" + (int) box.getHeight() + "×" + (int) box.getWidthZ() + ")";
        }
        e.getPlayer().sendMessage(Text.msg((i == 0 ? "First" : "Second") + " corner: <white>" + b.getX() + ", " + b.getY() + ", " + b.getZ() + "</white>" + size));
    }

    public boolean hasPicks(Player p) {
        Location[] pick = picks.get(p.getUniqueId());
        return pick != null && pick[0] != null && pick[1] != null;
    }
}
