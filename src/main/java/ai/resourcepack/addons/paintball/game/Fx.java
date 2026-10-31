package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.content.Content;
import ai.resourcepack.addons.paintball.team.Paint;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;

/** Everything a match sounds and looks like that is not the HUD. */
final class Fx {
    private final Content content;

    Fx(Content content) {
        this.content = content;
    }

    private static float wobble(float pitch, float by) {
        return pitch + (ThreadLocalRandom.current().nextFloat() * 2 - 1) * by;
    }

    void shot(Location at) {
        content.sound(at, "shot", 0.9f, wobble(1f, 0.06f), Sound.ENTITY_CHICKEN_EGG);
    }

    void empty(Player p) {
        content.sound(p, "empty", 0.8f, 1f, Sound.BLOCK_DISPENSER_FAIL);
    }

    void reloadStart(Player p) {
        content.sound(p.getLocation(), "reload", 0.8f, 1f, Sound.ITEM_CROSSBOW_LOADING_MIDDLE);
    }

    /** The sights coming up, or going down. */
    void aim(Player p, boolean up) {
        p.playSound(p.getLocation(), up ? Sound.ITEM_SPYGLASS_USE : Sound.ITEM_SPYGLASS_STOP_USING, 0.6f, up ? 1.3f : 1.1f);
    }

    void reloadDone(Player p) {
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.7f, 1.4f);
    }

    /** A ball breaking on something: a puff of its colour and a wet slap. */
    void splat(Location at, Paint paint, boolean big) {
        Color c = paint.bukkitColor();
        at.getWorld().spawnParticle(Particle.DUST, at, big ? 40 : 12, big ? 0.6 : 0.12, big ? 0.6 : 0.12, big ? 0.6 : 0.12, 0,
                new Particle.DustOptions(c, big ? 1.6f : 1.1f));
        ItemStack ball = content.item("ball_" + paint.id());
        at.getWorld().spawnParticle(Particle.ITEM, at, big ? 30 : 6, 0.05, 0.05, 0.05, big ? 0.35 : 0.12, ball);
        content.sound(at, big ? "burst" : "splat", big ? 1.2f : 0.55f, wobble(1f, 0.12f), Sound.ENTITY_SLIME_SQUISH_SMALL);
    }

    void trail(Location at, Paint paint) {
        at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(paint.bukkitColor(), 0.6f));
    }

    void splash(Location at) {
        at.getWorld().spawnParticle(Particle.SPLASH, at, 8, 0.1, 0.05, 0.1, 0);
    }

    /** The splat in the victim's ears; the paint on their screen is the HUD's. */
    void splatted(Player victim) {
        if (victim == null) return;
        content.sound(victim, "splat", 1f, 0.8f, Sound.ENTITY_SLIME_SQUISH);
    }

    void hitConfirm(Player shooter) {
        if (shooter == null) return;
        content.sound(shooter, "hit", 0.9f, 1f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
    }

    void knockedOut(Player victim) {
        if (victim == null) return;
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
    }

    void respawned(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 14, 0.3, 0.6, 0.3, 0.02);
    }

    void whistle(Player p) {
        content.sound(p, "whistle", 1f, 1f, Sound.BLOCK_NOTE_BLOCK_FLUTE);
    }

    void tick(Player p, int secondsLeft) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, secondsLeft <= 3 ? 1.6f : 1.2f);
    }

    /** A pling as the clock passes a warning, higher as the last seconds run out. */
    void timeWarning(Player p, int secondsLeft) {
        float pitch = secondsLeft <= 5 ? 2.0f - secondsLeft * 0.15f : secondsLeft <= 10 ? 1.2f : 0.9f;
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, pitch);
    }

    /** The edge of a safe zone, in its owner's paint, drawn only for them. */
    void safeZone(Player p, Location centre, double r, boolean square, Paint paint) {
        Particle.DustOptions dust = new Particle.DustOptions(paint == null ? Color.WHITE : paint.bukkitColor(), 1.0f);
        double y = centre.getY() + 0.15;
        int points = Math.max(12, (int) Math.round(r * (square ? 8 : 2 * Math.PI)));
        for (int i = 0; i < points; i++) {
            double x;
            double z;
            if (square) {
                double t = i * 8.0 / points;
                int side = (int) (t / 2);
                double along = (t % 2) - 1;
                x = side == 0 ? along * r : side == 1 ? r : side == 2 ? -along * r : -r;
                z = side == 0 ? -r : side == 1 ? along * r : side == 2 ? r : -along * r;
            } else {
                double a = i * 2 * Math.PI / points;
                x = Math.cos(a) * r;
                z = Math.sin(a) * r;
            }
            p.spawnParticle(Particle.DUST, centre.getX() + x, y, centre.getZ() + z, 1, 0, 0, 0, 0, dust);
        }
    }

    void protectedHit(Location at) {
        at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 6, 0.1, 0.1, 0.1, 0.1);
    }

    void fanfare(Player p, boolean won) {
        if (won) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
        }
    }

    void firework(Location at, Paint paint) {
        at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(paint.bukkitColor(), Color.WHITE).withFade(paint.bukkitColor()).trail(true).build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
        });
    }
}
