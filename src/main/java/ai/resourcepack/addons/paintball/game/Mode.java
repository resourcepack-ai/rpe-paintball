package ai.resourcepack.addons.paintball.game;

import java.util.Locale;

/**
 * What a match is played for. An arena plays the {@code mode} in its settings
 * (config.yml's unless it sets its own), and {@code /pb start <arena> <mode>}
 * plays another one for a match.
 */
public enum Mode {
    /** Red against blue, most splats wins. The game as it always was. */
    TEAMS(true),
    /** Everybody against everybody, each in a paint of their own. */
    FFA(false),
    /** Red against blue, for each other's flag. */
    CTF(true);

    private final boolean teams;

    Mode(boolean teams) {
        this.teams = teams;
    }

    /** Whether players are dealt onto red and blue. */
    public boolean teams() {
        return teams;
    }

    /** Its name in config and commands: "teams", "ffa", "ctf". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Mode parse(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "teams", "team", "tdm", "deathmatch" -> TEAMS;
            case "ffa", "free", "freeforall", "free-for-all" -> FFA;
            case "ctf", "flag", "flags", "capture", "capturetheflag" -> CTF;
            default -> null;
        };
    }
}
