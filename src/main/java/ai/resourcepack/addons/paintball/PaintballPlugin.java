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
import ai.resourcepack.common.update.UpdateCheck;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
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
    /** Ticks since enable: Spigot has no current-tick counter of its own. */
    private long ticks;

    /** This plugin's SpigotMC resource id: the number in its page's URL. */
    private static final int SPIGOT_RESOURCE = 139542;

    private UpdateCheck updates;

    /** The oldest engine with everything this plugin calls (the HUD's json: overlays, Overlays.width and space). */
    static final String MIN_ENGINE = "0.2.3";

    /**
     * Whether version {@code have} is {@code need} or newer, compared number by
     * number ("0.10.0" is newer than "0.9.4"); anything after a dash, as in
     * "0.2.3-SNAPSHOT", is ignored, and a version that is not numbers passes.
     */
    static boolean atLeast(String have, String need) {
        String[] a = have.split("-", 2)[0].split("\\.");
        String[] b = need.split("\\.");
        try {
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int x = i < a.length ? Integer.parseInt(a[i].trim()) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].trim()) : 0;
                if (x != y) return x > y;
            }
            return true;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void onEnable() {
        Plugin found = getServer().getPluginManager().getPlugin("RPEngine");
        if (!(found instanceof RPEnginePlugin rp)) {
            getLogger().severe("RP Engine isn't here, and paintball is drawn by it. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        engine = rp;
        String have = rp.getDescription().getVersion();
        if (!atLeast(have, MIN_ENGINE)) {
            // Said plainly and stopped here: an older engine lacks the HUD calls
            // this plugin makes, and would otherwise fail mid-match instead.
            getLogger().severe("Paintball needs RP Engine " + MIN_ENGINE + " or newer, and this server has " + have
                    + ". Update it from https://resourcepack.ai/rp-engine and restart. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
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
        addNewOptions();
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
        getServer().getScheduler().runTaskTimer(this, () -> {
            ticks++;
            games.tick();
        }, 1L, 1L);
        getLogger().info("Paintball is ready: " + arenas.all().size() + " arena(s). /pb join, then an op runs /pb start.");

        updates = UpdateCheck.spigot(this, SPIGOT_RESOURCE)
                .name("Paintball")
                .permission("paintball.update")
                .enabledWhen(() -> getConfig().getBoolean("update-check", true))
                .player(this::tellUpdate)
                .start();
    }

    /** The nudge: the sentence, then the link as one clickable piece. */
    private void tellUpdate(Player player, UpdateCheck.Update update) {
        BaseComponent line = Text.bungee(messages.component("update",
                "latest", update.latest(), "running", update.running()));
        BaseComponent link = Text.bungee(messages.component("update-link"));
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, update.url()));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.hover.content.Text(update.url())));
        // A deserialised component's extras can't be added to, so both go in a fresh one.
        player.spigot().sendMessage(new net.md_5.bungee.api.chat.TextComponent(line, link));
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
        addNewOptions();
        settings = new Settings(getConfig());
        messages = new Messages(getConfig());
        if (!games.games().isEmpty()) return false;
        arenas.load();
        return true;
    }

    /**
     * Writes any option the server's config.yml does not have yet (one a newer
     * build added) into it, with its default, so every option there is can be
     * found in the file. Nothing already there is touched.
     */
    private void addNewOptions() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public long ticks() {
        return ticks;
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
