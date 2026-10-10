package ai.resourcepack.addons.paintball;

import ai.resourcepack.addons.paintball.arena.Arena;
import ai.resourcepack.addons.paintball.arena.ArenaSetup;
import ai.resourcepack.addons.paintball.arena.ArenaStore;
import ai.resourcepack.addons.paintball.arena.FieldBuilder;
import ai.resourcepack.addons.paintball.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /paintball}, alias {@code /pb}.
 *
 * <pre>
 * /pb join | team | leave | scoreboard    everybody
 * /pb start | stop | reload | as | arena  ops
 * </pre>
 */
public final class PaintballCommand implements TabExecutor {
    private final PaintballPlugin plugin;

    public PaintballCommand(PaintballPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "join" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("paintball.admin");
        switch (sub) {
            case "join", "play" -> {
                Player p = player(sender);
                if (p == null) return true;
                Arena a = args.length > 1 ? plugin.arenas().get(args[1]) : plugin.games().defaultArena();
                plugin.games().join(p, a);
            }
            case "leave", "quit" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (!plugin.games().playing(p)) Text.send(p, Text.error("You're not in paintball."));
                else plugin.games().leave(p);
            }
            case "team", "pick" -> {
                Player p = player(sender);
                if (p == null) return true;
                Game g = plugin.games().of(p);
                String which = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                Team t = Team.parse(which);
                if (g == null) {
                    Text.send(p, Text.error("Join first: <white>/pb join"));
                } else if (t == null && !List.of("auto", "random", "any").contains(which)) {
                    Text.send(p, Text.error("/pb team <red|blue|auto>"));
                } else {
                    g.pick(p, t, false);
                }
            }
            case "scoreboard", "sb", "score", "top" -> {
                Player p = player(sender);
                if (p == null) return true;
                Game g = plugin.games().of(p);
                if (g == null) Text.send(p, Text.error("Join first: <white>/pb join"));
                else plugin.scoreboard().open(p, g);
            }
            case "start" -> {
                if (!admin) return deny(sender);
                Game g = gameFor(sender, args.length > 1 ? args[1] : null, true);
                if (g == null) return true;
                if (g.live()) {
                    Text.send(sender, Text.error("A match is already on."));
                } else if (g.start()) {
                    Text.send(sender, Text.msg("Match on " + g.arena().name() + " starting."));
                }
            }
            case "stop", "end" -> {
                if (!admin) return deny(sender);
                Game g = gameFor(sender, args.length > 1 ? args[1] : null, false);
                if (g == null) Text.send(sender, Text.error("Nobody is playing."));
                else g.stop();
            }
            case "as" -> {
                // For NPCs and anything else that runs console commands: /pb as <player> join
                if (!admin) return deny(sender);
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    Text.send(sender, Text.error("/pb as <online player> <what they run>"));
                    return true;
                }
                return onCommand(target, command, label, Arrays.copyOfRange(args, 2, args.length));
            }
            case "reload" -> {
                if (!admin) return deny(sender);
                Text.send(sender, plugin.reloadSettings() ? Text.msg("Reloaded config.yml and arenas.yml.")
                        : Text.msg("Reloaded config.yml. arenas.yml is left alone while somebody is playing."));
            }
            case "arena" -> {
                if (!admin) return deny(sender);
                arena(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            default -> help(sender, admin);
        }
        return true;
    }

    /**
     * The game a command means: the named arena's, the sender's own, or the
     * default arena's. With {@code joinSelf}, a player who is not in yet is put in
     * first, which is what lets an op type /pb start on their own.
     */
    private Game gameFor(CommandSender sender, String arenaId, boolean joinSelf) {
        Player self = as(sender);
        Arena arena = arenaId != null ? plugin.arenas().get(arenaId) : null;
        if (arenaId != null && arena == null) {
            Text.send(sender, Text.error("No arena called " + arenaId + "."));
            return null;
        }
        if (arena == null && self != null && plugin.games().of(self) != null) return plugin.games().of(self);
        if (arena == null) arena = plugin.games().defaultArena();
        if (arena == null) {
            Text.send(sender, Text.error("There's no arena yet: <white>/pb arena generate <id> confirm</white> builds one where you stand."));
            return null;
        }
        if (joinSelf && self != null && !plugin.games().playing(self)) {
            if (!plugin.games().join(self, arena)) return null;
        }
        Game g = plugin.games().existing(arena.id());
        if (g == null && joinSelf) Text.send(sender, Text.error("Nobody is in " + arena.name() + " yet."));
        return g;
    }

    // ---- arenas --------------------------------------------------------------------

    private void arena(CommandSender sender, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            if (plugin.arenas().all().isEmpty()) {
                Text.send(sender, Text.msg("No arenas yet. <white>/pb arena generate <id> confirm</white> builds one where you stand."));
                return;
            }
            for (Arena x : plugin.arenas().all()) {
                String state = x.enabled() ? "<#5BE37D>open" : x.missing().isEmpty() ? "<#FFD23F>ready" : "<#FF5C5C>needs " + String.join(", ", x.missing());
                Text.send(sender, Text.msg("<white><bold>" + x.name() + "</bold></white> <#5F6368>(" + x.id() + ", " + x.worldName() + ")</#5F6368>  " + state));
            }
            return;
        }
        Player p = as(sender);
        if (p == null) {
            // From the console a field can still be built, at a place named outright:
            // /pb arena generate <id> at <world> <x> <y> <z> [name]
            if (a.length >= 7 && a[0].equalsIgnoreCase("generate") && a[2].equalsIgnoreCase("at")) {
                consoleGenerate(sender, a);
                return;
            }
            Text.send(sender, Text.error("Arena setup is done in game (or: /pb arena generate <id> at <world> <x> <y> <z> [name])."));
            return;
        }
        ArenaSetup setup = plugin.setup();
        String op = a[0].toLowerCase(Locale.ROOT);
        if (op.equals("wand")) {
            p.getInventory().addItem(setup.wand());
            Text.send(p, Text.msg("Left-click one corner, right-click the other, then <white>/pb arena setbounds <id></white>."));
            return;
        }
        if (a.length < 2) {
            Text.send(p, Text.error("/pb arena " + op + " <id>"));
            return;
        }
        String id = a[1].toLowerCase(Locale.ROOT);
        String rest = a.length > 2 ? String.join(" ", Arrays.copyOfRange(a, 2, a.length)) : null;
        switch (op) {
            case "create" -> setup.create(p, id, rest);
            case "generate" -> {
                // It replaces every block in a big box, so it asks once.
                if (rest == null || !rest.toLowerCase(Locale.ROOT).startsWith("confirm")) {
                    Text.send(p, Text.msg("This builds a speedball field centred on you and replaces every block in a 31 × 13 × 57 box."));
                    Text.send(p, Text.msg("Best in an empty world (<white>/mv create paintball2 normal --world-type flat</white>). "
                            + "Run <white>/pb arena generate " + id + " confirm [name]</white> to build it."));
                    return;
                }
                String name = rest.length() > 7 ? rest.substring(7).trim() : null;
                Arena target = plugin.arenas().get(id);
                if (target != null && !target.worldName().equals(p.getWorld().getName())) {
                    plugin.arenas().remove(id);
                    target = null;
                }
                if (target == null) target = setup.create(p, id, name == null || name.isEmpty() ? null : name);
                if (target == null) return;
                new FieldBuilder(p.getLocation()).build(target, setup);
                p.teleport(target.waiting());
                Text.send(p, Text.msg("Built <#FFD23F>" + target.name() + "</#FFD23F>. <white>/pb start</white> when you're ready."));
            }
            default -> {
                Arena arena = plugin.arenas().get(id);
                if (arena == null) {
                    Text.send(p, Text.error("No arena called " + id + ". <white>/pb arena list"));
                    return;
                }
                switch (op) {
                    case "delete", "remove" -> {
                        Game g = plugin.games().existing(arena.id());
                        if (g != null) g.stop();
                        plugin.arenas().remove(arena.id());
                        Text.send(p, Text.msg("Deleted " + arena.name() + "."));
                    }
                    case "setwaiting", "waiting", "setlobby" -> setup.setWaiting(p, arena);
                    case "addspawn", "spawn" -> {
                        Team t = Team.parse(a.length > 2 ? a[2] : null);
                        if (t == null) Text.send(p, Text.error("/pb arena addspawn " + id + " <red|blue>"));
                        else setup.addSpawn(p, arena, t);
                    }
                    case "clearspawns" -> {
                        Team t = Team.parse(a.length > 2 ? a[2] : null);
                        if (t == null) Text.send(p, Text.error("/pb arena clearspawns " + id + " <red|blue>"));
                        else setup.clearSpawns(p, arena, t);
                    }
                    case "setbounds", "bounds" -> setup.setBounds(p, arena);
                    case "enable", "open" -> setup.enable(p, arena, true);
                    case "disable", "close" -> setup.enable(p, arena, false);
                    case "tp", "goto" -> {
                        if (arena.waiting() != null) p.teleport(arena.waiting());
                        else Text.send(p, Text.error(arena.name() + " has no waiting spot yet."));
                    }
                    case "name", "rename" -> {
                        if (rest == null) {
                            Text.send(p, Text.error("/pb arena name " + id + " <display name>"));
                            return;
                        }
                        arena.name(rest);
                        plugin.arenas().save();
                        Text.send(p, Text.msg("Renamed to <#FFD23F>" + rest + "</#FFD23F>."));
                    }
                    default -> Text.send(p, Text.error("Unknown: /pb arena " + op));
                }
            }
        }
    }

    private void consoleGenerate(CommandSender sender, String[] a) {
        String id = a[1].toLowerCase(Locale.ROOT);
        World world = Bukkit.getWorld(a[3]);
        if (world == null || !ArenaStore.ID.matcher(id).matches()) {
            Text.send(sender, Text.error("No world " + a[3] + ", or a bad arena id."));
            return;
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(a[4]);
            y = Double.parseDouble(a[5]);
            z = Double.parseDouble(a[6]);
        } catch (NumberFormatException e) {
            Text.send(sender, Text.error("x y z have to be numbers."));
            return;
        }
        String name = a.length > 7 ? String.join(" ", Arrays.copyOfRange(a, 7, a.length)) : id;
        Arena old = plugin.arenas().get(id);
        if (old != null && plugin.games().existing(id) != null) plugin.games().existing(id).stop();
        Arena target = new Arena(id, name, world.getName());
        plugin.arenas().put(target);
        new FieldBuilder(new Location(world, x, y, z)).build(target, plugin.setup());
        Text.send(sender, Text.msg("Built and opened " + name + " in " + world.getName() + "."));
    }

    // ---- helpers -----------------------------------------------------------------------

    /** The player behind a command: typed, or run as them by /execute, a command block or an NPC plugin. */
    private static Player as(CommandSender sender) {
        if (sender instanceof Player p) return p;
        if (sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof Player p) return p;
        return null;
    }

    private static Player player(CommandSender sender) {
        Player p = as(sender);
        if (p == null) Text.send(sender, Text.error("Players only (or /pb as <player> ...)."));
        return p;
    }

    private static boolean deny(CommandSender sender) {
        Text.send(sender, Text.error("You can't do that."));
        return true;
    }

    private static void help(CommandSender s, boolean admin) {
        Text.send(s, Text.msg("<white>/pb join</white> <#9AA0A6>·</#9AA0A6> <white>/pb team <red|blue|auto></white> <#9AA0A6>·</#9AA0A6> <white>/pb leave</white> <#9AA0A6>·</#9AA0A6> <white>/pb scoreboard</white> <#9AA0A6>(or F in a match)"));
        if (admin) {
            Text.send(s, Text.msg("<white>/pb start</white> <#9AA0A6>(works alone) ·</#9AA0A6> <white>/pb stop</white> <#9AA0A6>·</#9AA0A6> <white>/pb reload</white> <#9AA0A6>·</#9AA0A6> <white>/pb as <player> ...</white>"));
            Text.send(s, Text.msg("<white>/pb arena</white> <#9AA0A6>list · generate <id> confirm · create · setwaiting · addspawn · clearspawns · wand · setbounds · enable · disable · tp · name · delete"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("paintball.admin");
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("join", "team", "leave", "scoreboard"));
            if (admin) out.addAll(List.of("start", "stop", "arena", "reload", "as"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join", "start", "stop" -> plugin.arenas().all().forEach(a -> out.add(a.id()));
                case "team", "pick" -> out.addAll(List.of("red", "blue", "auto"));
                case "arena" -> {
                    if (admin) out.addAll(List.of("list", "generate", "create", "setwaiting", "addspawn", "clearspawns",
                            "wand", "setbounds", "enable", "disable", "tp", "name", "delete"));
                }
                case "as" -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("arena") && admin
                && !List.of("list", "wand", "create", "generate").contains(args[1].toLowerCase(Locale.ROOT))) {
            plugin.arenas().all().forEach(a -> out.add(a.id()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("arena")) {
            String op = args[1].toLowerCase(Locale.ROOT);
            if (op.equals("addspawn") || op.equals("clearspawns")) out.addAll(List.of("red", "blue"));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
