package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Links between Watchdog accounts and Minecraft accounts, saved in links.yml.
 * The portal hands out a short code, the player types it in-game with /koppel to link.
 * Only touch this from the main thread.
 */
public class PlayerLinks {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long CODE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Code> codes = new HashMap<>();

    /**
     * A code handed out on the portal, waiting to be typed in-game
     * @param code     The code, without the dash
     * @param subject  The Watchdog account id
     * @param watchdog The Watchdog username
     * @param expires  When the code stops working
     */
    public record Code(String code, String subject, String watchdog, long expires) {

        /**
         * Get the code the way players see it, e.g. K7F-2QX
         * @return The formatted code
         */
        public String display() {
            return code.substring(0, 3) + "-" + code.substring(3);
        }
    }

    /**
     * A saved link
     * @param subject  The Watchdog account id
     * @param watchdog The Watchdog username
     * @param uuid     The Minecraft account
     */
    public record Link(String subject, String watchdog, UUID uuid) {}

    private static File file() {
        return new File(Core.getInstance().getDataFolder(), "links.yml");
    }

    /**
     * Get the Minecraft account linked to a Watchdog account
     * @param subject The Watchdog account id
     * @return The link, or null if the account isn't linked
     */
    public static Link get(String subject) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        String uuid = yaml.getString(subject + ".uuid");
        if (uuid == null) return null;
        try {
            return new Link(subject, yaml.getString(subject + ".watchdog", "?"), UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get every saved link
     * @return The links
     */
    public static List<Link> all() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        List<Link> links = new ArrayList<>();
        for (String subject : yaml.getKeys(false)) {
            try {
                links.add(new Link(subject, yaml.getString(subject + ".watchdog", "?"), UUID.fromString(yaml.getString(subject + ".uuid", ""))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return links;
    }

    /**
     * Get the Watchdog account linked to a Minecraft account
     * @param uuid The Minecraft account
     * @return The link, or null if the player isn't linked
     */
    public static Link find(UUID uuid) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        for (String subject : yaml.getKeys(false)) {
            if (uuid.toString().equals(yaml.getString(subject + ".uuid"))) {
                return new Link(subject, yaml.getString(subject + ".watchdog", "?"), uuid);
            }
        }
        return null;
    }

    /**
     * Get the open code of a Watchdog account, or make a new one
     * @param subject  The Watchdog account id
     * @param watchdog The Watchdog username
     * @return The code
     */
    public static Code code(String subject, String watchdog) {
        long now = System.currentTimeMillis();
        codes.values().removeIf(c -> c.expires() < now);
        for (Code c : codes.values()) {
            if (c.subject().equals(subject)) return c;
        }
        String value;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 6; i++) builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            value = builder.toString();
        } while (codes.containsKey(value));
        Code code = new Code(value, subject, watchdog, now + CODE_MILLIS);
        codes.put(value, code);
        return code;
    }

    /**
     * Get the open code of a Watchdog account without making a new one
     * @param subject The Watchdog account id
     * @return The code, or null if there is none
     */
    public static Code openCode(String subject) {
        long now = System.currentTimeMillis();
        for (Code c : codes.values()) {
            if (c.subject().equals(subject) && c.expires() >= now) return c;
        }
        return null;
    }

    /**
     * Link a Minecraft account with the code typed in-game
     * Any older link of either account is replaced
     * @param input The code, with or without the dash
     * @param uuid  The Minecraft account
     * @return The new link, or null if the code is wrong or expired
     */
    public static Link redeem(String input, UUID uuid) {
        String value = input.replace("-", "").toUpperCase();
        Code code = codes.get(value);
        if (code == null || code.expires() < System.currentTimeMillis()) return null;
        codes.remove(value);

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        for (String subject : yaml.getKeys(false)) {
            if (uuid.toString().equals(yaml.getString(subject + ".uuid"))) yaml.set(subject, null);
        }
        yaml.set(code.subject() + ".uuid", uuid.toString());
        yaml.set(code.subject() + ".watchdog", code.watchdog());
        yaml.set(code.subject() + ".linked", System.currentTimeMillis());
        save(yaml);
        return new Link(code.subject(), code.watchdog(), uuid);
    }

    /**
     * Remove the link of a Watchdog account
     * @param subject The Watchdog account id
     * @return true if there was a link
     */
    public static boolean unlink(String subject) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file());
        if (!yaml.contains(subject)) return false;
        yaml.set(subject, null);
        save(yaml);
        return true;
    }

    /**
     * Remove the link of a Minecraft account
     * @param uuid The Minecraft account
     * @return true if there was a link
     */
    public static boolean unlink(UUID uuid) {
        Link link = find(uuid);
        return link != null && unlink(link.subject());
    }

    private static void save(YamlConfiguration yaml) {
        try {
            yaml.save(file());
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not save links.yml: " + e.getMessage());
        }
    }
}
