package nl.ashlyn.core.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.GameRule;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Remembers the latest joins, leaves, deaths and advancements for the website.
 * Events and snapshots both run on the main thread, so no locking is needed.
 */
public class ActivityFeed implements Listener {

    private final int size;
    private final Deque<JsonObject> events = new ArrayDeque<>();
    private long nextId = 1;

    public ActivityFeed(int size) {
        this.size = Math.max(1, size);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        JsonObject json = create(event.getPlayer().hasPlayedBefore() ? "join" : "first-join", event.getPlayer());
        add(json);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        add(create("quit", event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        add(create("death", event.getEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || !display.shouldAnnounceChat()) return;

        Player player = event.getPlayer();
        if (Boolean.FALSE.equals(player.getWorld().getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS))) return;

        JsonObject json = create("advancement", player);
        json.addProperty("title", display.getTitle());
        json.addProperty("description", display.getDescription());
        json.addProperty("frame", display.getType().name().toLowerCase());
        add(json);
    }

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        events.forEach(array::add);
        return array;
    }

    private JsonObject create(String type, Player player) {
        JsonObject json = new JsonObject();
        json.addProperty("id", nextId++);
        json.addProperty("type", type);
        json.addProperty("player", player.getName());
        json.addProperty("time", System.currentTimeMillis());
        return json;
    }

    private void add(JsonObject json) {
        events.addFirst(json);
        while (events.size() > size) events.removeLast();
    }
}
