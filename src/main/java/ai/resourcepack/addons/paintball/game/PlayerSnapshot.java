package ai.resourcepack.addons.paintball.game;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Everything a match changes about a player, as it was before they joined:
 * inventory, where they stood, game mode, health, hunger, levels and effects.
 *
 * <p>Written to disk the moment it is taken, and deleted when it is given back,
 * so a server that goes down mid-match gives everybody their things back the
 * next time they join.
 */
public final class PlayerSnapshot {
    private final File dir;
    private final Logger log;

    public PlayerSnapshot(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
        dir.mkdirs();
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    public void take(Player p) {
        YamlConfiguration y = new YamlConfiguration();
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (contents[i] == null) contents[i] = ItemStack.empty();
        y.set("inventory", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        Location l = p.getLocation();
        y.set("world", l.getWorld().getName());
        y.set("pos", List.of(l.getX(), l.getY(), l.getZ(), (double) l.getYaw(), (double) l.getPitch()));
        y.set("gamemode", p.getGameMode().name());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", (double) p.getSaturation());
        y.set("level", p.getLevel());
        y.set("exp", (double) p.getExp());
        y.set("allow-flight", p.getAllowFlight());
        y.set("fire", p.getFireTicks());
        List<PotionEffect> effects = new ArrayList<>(p.getActivePotionEffects());
        y.set("effects", effects);
        try {
            y.save(file(p.getUniqueId()));
        } catch (IOException e) {
            log.severe("Could not save " + p.getName() + "'s inventory snapshot: " + e.getMessage());
        }
    }

    /** Puts a player back as they were and forgets the snapshot. Answers false when there was none. */
    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        reset(p);
        try {
            String inv = y.getString("inventory");
            if (inv != null) p.getInventory().setContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(inv)));
        } catch (RuntimeException e) {
            log.severe("Could not read " + p.getName() + "'s saved inventory (kept at " + f.getName() + ".bad): " + e.getMessage());
            f.renameTo(new File(dir, f.getName() + ".bad"));
            return false;
        }
        try {
            p.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ignored) {
            p.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.max(1, Math.min(cap, y.getDouble("health", 20))));
        p.setFoodLevel(y.getInt("food", 20));
        p.setSaturation((float) y.getDouble("saturation", 5));
        p.setLevel(y.getInt("level", 0));
        p.setExp((float) Math.max(0, Math.min(0.9999, y.getDouble("exp", 0))));
        p.setAllowFlight(y.getBoolean("allow-flight", false) || p.getGameMode() == GameMode.CREATIVE);
        p.setFireTicks(y.getInt("fire", 0));
        List<?> effects = y.getList("effects");
        if (effects != null) {
            List<PotionEffect> out = new ArrayList<>();
            for (Object o : effects) if (o instanceof PotionEffect e) out.add(e);
            p.addPotionEffects(out);
        }
        World w = Bukkit.getWorld(y.getString("world", ""));
        List<Double> pos = y.getDoubleList("pos");
        if (w != null && pos.size() >= 5) {
            p.teleport(new Location(w, pos.get(0), pos.get(1), pos.get(2), pos.get(3).floatValue(), pos.get(4).floatValue()));
        } else {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        f.delete();
        return true;
    }

    /** A clean slate: empty inventory, full health and food, no effects, not on fire. */
    public static void reset(Player p) {
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.closeInventory();
        Collection<PotionEffect> active = p.getActivePotionEffects();
        for (PotionEffect e : active) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setLevel(0);
        p.setExp(0);
    }
}
