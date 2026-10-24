package nl.ashlyn.core.wrapped;

/**
 * Created by Ashlyn on 24/10/2026
 *
 * What the season tracker remembers about one player, on top of Minecraft's own statistics.
 */
public class SeasonStats {

    public static final String[] DIMENSIONS = {"overworld", "nether", "end"};

    String name;
    final int[] dimensionMinutes = new int[DIMENSIONS.length];
    final int[] hourMinutes = new int[24];
    int daysPlayed;
    String lastDay;
    int longestSessionMinutes;
    int advancements;
    String firstAdvancement;
    long firstAdvancementTime;

    SeasonStats(String name) {
        this.name = name;
    }
}
