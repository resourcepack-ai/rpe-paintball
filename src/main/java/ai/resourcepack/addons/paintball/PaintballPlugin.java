package ai.resourcepack.addons.paintball;

import ai.resourcepack.engine.RPEnginePlugin;
import ai.resourcepack.engine.api.AddonContent;
import ai.resourcepack.addons.paintball.arena.ArenaSetup;
import ai.resourcepack.addons.paintball.arena.ArenaStore;
import ai.resourcepack.addons.paintball.game.GameListener;
import ai.resourcepack.addons.paintball.game.GameManager;
import ai.resourcepack.addons.paintball.game.Loadout;
import ai.resourcepack.addons.paintball.game.PlayerSnapshot;
import ai.resourcepack.addons.paintball.ui.Scoreboard;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;

import java.io.File;

/**
 * Paintball: 5v5 on RP Engine. A hit splats you, you are back in three seconds,
 * and the team with more kills when the clock runs out wins.
 *
 * <p>Everything a player sees is an ordinary RP Engine content folder inside
 * this jar, installed into {@code plugins/RPEngine/content/paintball/} on
 * enable: the marker, the mask, the balls, the splats and the sounds, the HUD
 * ({@code huds/hud.json}) and the scoreboard ({@code dialogs/scoreboard.json}),
 * both drawn in ResourcePack AI Studio. This plugin plays the game and feeds
 * them values.
 */
public final class PaintballPlugin extends JavaPlugin {
    private RPEnginePlugin engine;
    private Content content;
    private Settings settings;
    private Messages messages;
    private ArenaStore arenas;
    private PlayerSnapshot snapshots;
    private GameManager games;
    private ArenaSetup setup;
    private Scoreboard scoreboard;

    @Override
    public void onEnable() {
        Plugin found = getServer().getPluginManager().getPlugin("RPEngine");
        if (!(found instanceof RPEnginePlugin rp)) {
            getLogger().severe("RP Engine isn't here, and paintball is drawn by it. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        engine = rp;
        try {
            if (AddonContent.install(this, engine, Content.NS)) {
                getLogger().info("Installed the paintball content into RP Engine; reloading it once.");
                engine.reload();
            }
        } catch (Exception e) {
            getLogger().severe("Could not install the paintball content: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        content = new Content(engine);
        Loadout.init(this);

        saveDefaultConfig();
        settings = new Settings(getConfig());
        messages = new Messages(getConfig());
        getDataFolder().mkdirs();
        arenas = new ArenaStore(new File(getDataFolder(), "arenas.yml"), getLogger());
        arenas.load();
        snapshots = new PlayerSnapshot(new File(getDataFolder(), "snapshots"), getLogger());
        games = new GameManager(this);
        setup = new ArenaSetup(this, arenas, games);
        scoreboard = new Scoreboard(this);

        sweepStrays();
        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(setup, this);
        PluginCommand cmd = getCommand("paintball");
        if (cmd != null) {
            PaintballCommand handler = new PaintballCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getServer().getScheduler().runTaskTimer(this, games::tick, 1L, 1L);
        getLogger().info("Paintball is ready: " + arenas.all().size() + " arena(s). /pb join, then an op runs /pb start.");
    }

    @Override
    public void onDisable() {
        if (games != null) games.stopAll();
    }

    /** Takes down any paintball displays a crash left in loaded worlds (they are never saved, but be sure). */
    private void sweepStrays() {
        int n = 0;
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class)) {
                if (e.getScoreboardTags().contains("paintball")) {
                    e.remove();
                    n++;
                }
            }
        }
        if (n > 0) getLogger().info("Removed " + n + " stray paint display(s).");
    }

    /** Re-reads config.yml, and arenas.yml too unless a match is using them. Answers whether arenas were read. */
    public boolean reloadSettings() {
        reloadConfig();
        settings = new Settings(getConfig());
        messages = new Messages(getConfig());
        if (!games.games().isEmpty()) return false;
        arenas.load();
        return true;
    }

    public Content content() {
        return content;
    }

    public Settings settings() {
        return settings;
    }

    public ArenaStore arenas() {
        return arenas;
    }

    public PlayerSnapshot snapshots() {
        return snapshots;
    }

    public GameManager games() {
        return games;
    }

    public ArenaSetup setup() {
        return setup;
    }

    public Messages messages() {
        return messages;
    }

    public Scoreboard scoreboard() {
        return scoreboard;
    }
}
