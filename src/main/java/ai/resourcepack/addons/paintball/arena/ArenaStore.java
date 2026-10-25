package ai.resourcepack.addons.paintball.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** arenas.yml: every arena, by id. Saved whole after every change. */
public final class ArenaStore {
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final File file;
    private final Logger log;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaStore(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        arenas.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("arenas");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) arenas.put(id, Arena.load(id, s));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of("Paintball arenas. Set them up in game with /pb arena; editing by hand works too (then /pb reload)."));
        for (Arena a : arenas.values()) a.save(yml.createSection("arenas." + a.id()));
        try {
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            yml.save(tmp);
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.severe("Could not save arenas.yml: " + e.getMessage());
        }
    }

    public Arena get(String id) {
        return id == null ? null : arenas.get(id.toLowerCase());
    }

    public Collection<Arena> all() {
        return arenas.values();
    }

    public List<Arena> enabled() {
        List<Arena> out = new ArrayList<>();
        for (Arena a : arenas.values()) if (a.enabled()) out.add(a);
        return out;
    }

    public void put(Arena a) {
        arenas.put(a.id(), a);
        save();
    }

    public void remove(String id) {
        arenas.remove(id);
        save();
    }

    /** The display name for an arena id, even one deleted since its stats were kept. */
    public String nameOf(String id) {
        Arena a = arenas.get(id);
        return a == null ? id : a.name();
    }
}
