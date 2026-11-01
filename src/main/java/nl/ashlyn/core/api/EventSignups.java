package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Who says they'll come to an event on the portal, and who was online while it went on.
 * Events are keyed by their start time, saved in signups.yml.
 * Only touch this from the main thread.
 */
public class EventSignups {

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "signups.yml");
    }

    /**
     * Get the players who said they'll come
     * @param time The event's start time
     * @return Their UUIDs
     */
    public static List<UUID> going(long time) {
        return uuids(YamlConfiguration.loadConfiguration(file()).getStringList(time + ".going"));
    }

    /**
     * Get what a player answered for an event
     * @param time The event's start time
     * @param uuid The player
     * @return true if they come, false if they can't, null if they didn't answer
     */
    public static Boolean answer(long time, UUID uuid) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        if (yaml.getStringList(time + ".going").contains(uuid.toString())) return true;
        if (yaml.getStringList(time + ".not").contains(uuid.toString())) return false;
        return null;
    }

    /**
     * Save what a player answered for an event
     * @param time  The event's start time
     * @param uuid  The player
     * @param going true if they come, false if they can't, null to take the answer back
     */
    public static void setAnswer(long time, UUID uuid, Boolean going) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        List<String> yes = new ArrayList<>(yaml.getStringList(time + ".going"));
        List<String> no = new ArrayList<>(yaml.getStringList(time + ".not"));
        yes.remove(uuid.toString());
        no.remove(uuid.toString());
        if (Boolean.TRUE.equals(going)) yes.add(uuid.toString());
        if (Boolean.FALSE.equals(going)) no.add(uuid.toString());
        yaml.set(time + ".going", yes);
        yaml.set(time + ".not", no);
        save(yaml);
    }

    /**
     * Check if a player was online while an event went on
     * @param time The event's start time
     * @param uuid The player
     * @return true if they were there
     */
    public static boolean attended(long time, UUID uuid) {
        return YamlConfiguration.loadConfiguration(file()).getStringList(time + ".attended").contains(uuid.toString());
    }

    /**
     * Remember that players were online while an event went on
     * @param time    The event's start time
     * @param players The players who are there
     */
    public static void markAttended(long time, Collection<UUID> players) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        List<String> attended = new ArrayList<>(yaml.getStringList(time + ".attended"));
        boolean changed = false;
        for (UUID uuid : players) {
            if (attended.contains(uuid.toString())) continue;
            attended.add(uuid.toString());
            changed = true;
        }
        if (!changed) return;
        yaml.set(time + ".attended", attended);
        save(yaml);
    }

    private static List<UUID> uuids(List<String> values) {
        List<UUID> uuids = new ArrayList<>();
        for (String value : values) {
            try {
                uuids.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    private static void save(YamlConfiguration yaml) {
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save signups.yml: " + e.getMessage());
        }
    }
}
