package ai.resourcepack.addons.paintball.game;

import ai.resourcepack.addons.paintball.Content;
import ai.resourcepack.addons.paintball.Team;
import ai.resourcepack.addons.paintball.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
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

    void reloadDone(Player p) {
        p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.7f, 1.4f);
    }

    /** A ball breaking on something: a puff of its colour and a wet slap. */
    void splat(Location at, Team team, boolean big) {
        Color c = team.bukkitColor();
        at.getWorld().spawnParticle(Particle.DUST, at, big ? 40 : 12, big ? 0.6 : 0.12, big ? 0.6 : 0.12, big ? 0.6 : 0.12, 0,
                new Particle.DustOptions(c, big ? 1.6f : 1.1f));
        ItemStack ball = content.item("ball_" + team.id());
        at.getWorld().spawnParticle(Particle.ITEM, at, big ? 30 : 6, 0.05, 0.05, 0.05, big ? 0.35 : 0.12, ball);
        content.sound(at, big ? "burst" : "splat", big ? 1.2f : 0.55f, wobble(1f, 0.12f), Sound.ENTITY_SLIME_SQUISH_SMALL);
    }

    void trail(Location at, Team team) {
        at.getWorld().spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(team.bukkitColor(), 0.6f));
    }

    void splash(Location at) {
        at.getWorld().spawnParticle(Particle.SPLASH, at, 8, 0.1, 0.05, 0.1, 0);
    }

    /** The victim's screen, painted in the colour of whoever hit them. */
    void screenSplat(Player victim, Team shooter) {
        if (victim == null) return;
        int n = 1 + ThreadLocalRandom.current().nextInt(3);
        String glyph = content.icon("screen_splat_" + n, "");
        if (glyph.isEmpty()) return;
        victim.showTitle(Title.title(Component.text(glyph, shooter.color()), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(450), Duration.ofMillis(1100))));
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

    void firework(Location at, Team team) {
        at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(team.bukkitColor(), Color.WHITE).withFade(team.bukkitColor()).trail(true).build());
            meta.setPower(1);
            f.setFireworkMeta(meta);
        });
    }

    void title(Player p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        p.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    static Component big(String s, net.kyori.adventure.text.format.TextColor c) {
        return Text.plain(s, c).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD);
    }
}
