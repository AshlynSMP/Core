package nl.ashlyn.core.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.utils.TextScrambler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Created by Ashlyn on 14/11/2026
 *
 * Keeps the latest chat messages for the portal and lets linked players chat from the website.
 * Chat is read at MONITOR, after Radio has scrambled or blocked it, so the website shows what players saw in-game.
 * Messages from the website follow the same Radio tower rules.
 * Chat arrives on async threads, so all access is synchronized.
 */
public class ChatFeed implements Listener {

    public static final int MAX_LENGTH = 200;

    private final int size;
    private final Deque<JsonObject> messages = new ArrayDeque<>();
    private long nextId;

    /**
     * Ids start at the current time so they keep going up after a reload and the website never misses a message.
     */
    public ChatFeed(int size) {
        this.size = Math.max(1, size);
        this.nextId = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        add(event.getPlayer().getName(), event.getPlayer().getUniqueId().toString(), ChatColor.stripColor(event.getMessage()), false);
    }

    /**
     * Sends a message from the website to everyone in-game. Must run on the main thread.
     * Returns false when Radio has chat switched off.
     */
    public boolean send(OfflinePlayer player, String text) {
        Plugin radio = Bukkit.getPluginManager().getPlugin("Radio");
        boolean north = radio == null || radio.getConfig().getBoolean("tower-noord", true);
        boolean south = radio == null || radio.getConfig().getBoolean("tower-zuid", true);
        boolean lore = radio != null && CoreConfig.isLoreEnabled();

        if (lore && !north && !south) return false;
        String message = lore && (!north || !south) ? TextScrambler.scrambleLetters(text) : text;
        String name = player.getName();

        if (lore) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "[Radio] " + name + ChatColor.GRAY + " (web)" + ChatColor.GOLD + " >>> " + ChatColor.RESET + message);
        } else {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(name);
            String prefix = team != null ? team.getPrefix() : "";
            Bukkit.broadcastMessage(prefix + name + ChatColor.GRAY + " (web)" + ChatColor.RESET + ": " + message);
        }
        add(name, player.getUniqueId().toString(), message, true);
        return true;
    }

    public synchronized JsonArray toJson(long after) {
        JsonArray array = new JsonArray();
        messages.descendingIterator().forEachRemaining(m -> {
            if (m.get("id").getAsLong() > after) array.add(m);
        });
        return array;
    }

    /**
     * Strips colour codes and control characters from text typed on the website.
     */
    public static String clean(String text) {
        String stripped = ChatColor.stripColor(text).replace("§", "").replaceAll("\\p{Cntrl}", " ").trim();
        return stripped.replaceAll("\\s+", " ");
    }

    private synchronized void add(String player, String uuid, String text, boolean web) {
        JsonObject json = new JsonObject();
        json.addProperty("id", nextId++);
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("message", text);
        json.addProperty("web", web);
        json.addProperty("time", System.currentTimeMillis());
        messages.addFirst(json);
        while (messages.size() > size) messages.removeLast();
    }
}
