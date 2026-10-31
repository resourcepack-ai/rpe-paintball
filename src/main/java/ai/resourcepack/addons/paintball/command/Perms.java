package ai.resourcepack.addons.paintball.command;

import java.util.List;

/**
 * Every permission node the plugin checks, in one place.
 *
 * <p>One node per thing a server might want to hand out on its own. The two
 * old nodes, {@code paintball.play} and {@code paintball.admin}, are still in
 * {@code plugin.yml}, but only as convenience parents: granting one grants
 * every node below it, and nothing in the code checks either of them.
 * {@code PermsTest} holds this class and {@code plugin.yml} to each other.
 */
public final class Perms {
    private Perms() {
    }

    // ---- players (children of paintball.play, everyone by default) ---------------

    /** {@code /pb join}: into an arena's lobby. */
    public static final String JOIN = "paintball.join";
    /** {@code /pb team} and the lobby's red and blue items. */
    public static final String TEAM = "paintball.team";
    /** {@code /pb colour} and the lobby's marker, which picks a colour. */
    public static final String COLOUR = "paintball.colour";
    /** {@code /pb scoreboard}, F in a match and the lobby's scoreboard item. */
    public static final String SCOREBOARD = "paintball.scoreboard";

    // ---- staff (children of paintball.admin, ops by default) ---------------------

    /** {@code /pb start}, and being told in the lobby that you can. */
    public static final String START = "paintball.start";
    /** {@code /pb stop}. */
    public static final String STOP = "paintball.stop";
    /** {@code /pb reload}. */
    public static final String RELOAD = "paintball.reload";
    /** {@code /pb as <player> ...}: run a subcommand as somebody else. */
    public static final String AS = "paintball.as";
    /** {@code /pb arena} setup (list, create, spawns, bounds, the wand, open/close, tp, rename, its own settings). */
    public static final String ARENA = "paintball.arena";
    /** {@code /pb arena generate}, which replaces every block in a large box. */
    public static final String ARENA_GENERATE = "paintball.arena.generate";
    /** {@code /pb arena delete}, which also ends that arena's match. */
    public static final String ARENA_DELETE = "paintball.arena.delete";
    /** Running any command during a match, not only the short list a player may. */
    public static final String BYPASS_COMMANDS = "paintball.bypass.commands";

    /** Who is told when a newer version is out. Not a child of either parent. */
    public static final String UPDATE = "paintball.update";

    /** What {@code paintball.play} grants. */
    public static final List<String> PLAY = List.of(JOIN, TEAM, COLOUR, SCOREBOARD);
    /** What {@code paintball.admin} grants. */
    public static final List<String> ADMIN = List.of(START, STOP, RELOAD, AS, ARENA, ARENA_GENERATE, ARENA_DELETE, BYPASS_COMMANDS);
}
