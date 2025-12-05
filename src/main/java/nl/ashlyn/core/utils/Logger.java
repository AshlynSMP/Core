package nl.ashlyn.core.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Created by Ashlyn on 05/12/2025
 */
public class Logger {
    public static void logCore(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.LIGHT_PURPLE + "[Core] " + message);
    }

    public static void logRadio(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[Radio] " + message);
    }

    public static void logPropaganda(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Propaganda] " + message);
    }

    public static void logCredits(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.DARK_BLUE + "[Credits] " + message);
    }
}
