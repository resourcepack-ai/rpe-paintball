package ai.resourcepack.addons.paintball.stats;

/** One player's counters on one arena (or summed over all of them). Gson reads and writes these fields. */
public final class StatLine {
    public int kills;
    public int deaths;
    public int shots;
    public int hits;
    public int wins;
    public int losses;
    public int matches;
    public int mvps;
    public long paint;

    public void add(StatLine o) {
        kills += o.kills;
        deaths += o.deaths;
        shots += o.shots;
        hits += o.hits;
        wins += o.wins;
        losses += o.losses;
        matches += o.matches;
        mvps += o.mvps;
        paint += o.paint;
    }

    public StatLine copy() {
        StatLine c = new StatLine();
        c.add(this);
        return c;
    }

    public int accuracy() {
        return shots == 0 ? 0 : (int) Math.round(hits * 100.0 / shots);
    }

    public String kd() {
        // A point whatever the server's language: "1.50", never "1,50".
        return String.format(java.util.Locale.ROOT, "%.2f", kills / (double) Math.max(1, deaths));
    }
}
