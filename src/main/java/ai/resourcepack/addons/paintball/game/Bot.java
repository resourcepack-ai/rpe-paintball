package ai.resourcepack.addons.paintball.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.UUID;

/** One computer player's figure and what it has in mind. See {@link Bots}. */
final class Bot {
    /** Its mannequin, kept for the whole match and hidden while it is out. */
    UUID figure;

    /** Who it is shooting at, and since when it has had them in sight. */
    GamePlayer target;
    long seenSince;
    /** When it next looks round for somebody to shoot. */
    long nextLook;
    /** It fires in bursts: shots left in this one, and when the next may start. */
    int burst;
    long burstAt;
    /** Which way it is sidestepping (1 or -1), and until when. */
    double strafe = 1;
    long strafeUntil;
    /** A way round something in its path, and until when it takes it. */
    Vector detour;
    long detourUntil;
    /** Where it was a second ago, to notice it has stopped getting anywhere. */
    Location lastPos;
    long lastPosAt;
    /** In capture the flag: whether it goes for their flag, or stays to guard its own. */
    boolean attacker;

    LivingEntity figure() {
        Entity e = figure == null ? null : Bukkit.getEntity(figure);
        return e instanceof LivingEntity living && living.isValid() ? living : null;
    }
}
