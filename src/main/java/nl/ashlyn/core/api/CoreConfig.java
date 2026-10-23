package nl.ashlyn.core.api;

import nl.ashlyn.core.Core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Ashlyn on 05/12/2025
 */
public class CoreConfig {
    /**
     * Get the list of leaders from the config
     * @return List of leader usernames
     */
    public static List<String> getLeaders() {
        return Core.getCoreConfig().getStringList("leaders");
    }

    /**
     * Check if a player is a leader
     * @param username The username to check
     * @return true if the player is a leader, false otherwise
     */
    public static boolean isLeader(String username) {
        return getLeaders().contains(username);
    }

    /**
     * Check if lore feature is enabled
     * @return true if lore is enabled, false otherwise
     */
    public static boolean isLoreEnabled() {
        return Core.getCoreConfig().getBoolean("lore-enabled", false);
    }

    /**
     * Set the list of leaders in the config
     * @param leaders List of leader usernames
     */
    public static void setLeaders(List<String> leaders) {
        Core.getCoreConfig().set("leaders", leaders);
    }

    /**
     * Add a leader to the config
     * @param username The username to add
     */
    public static void addLeader(String username) {
        List<String> leaders = getLeaders();
        if (!leaders.contains(username)) {
            leaders.add(username);
            setLeaders(leaders);
        }
    }

    /**
     * Remove a leader from the config
     * @param username The username to remove
     */
    public static void removeLeader(String username) {
        List<String> leaders = getLeaders();
        leaders.remove(username);
        setLeaders(leaders);
    }

    /**
     * Set whether lore feature is enabled
     * @param enabled true to enable lore, false to disable
     */
    public static void setLoreEnabled(boolean enabled) {
        Core.getCoreConfig().set("lore-enabled", enabled);
    }

    /**
     * Get the act the storyline is currently in
     * @return Act number, starting at 1
     */
    public static int getAct() {
        return Math.max(1, Core.getCoreConfig().getInt("story.act", 1));
    }

    /**
     * Get how many acts the season has
     * @return Number of acts
     */
    public static int getTotalActs() {
        return Math.max(1, Core.getCoreConfig().getInt("story.total-acts", 3));
    }

    /**
     * Move the storyline to an act
     * @param act Act number, starting at 1
     */
    public static void setAct(int act) {
        Core.getCoreConfig().set("story.act", act);
    }

    /**
     * Get the titles of the acts, index 0 is act 1
     * @return List of titles
     */
    public static List<String> getActTitles() {
        return new ArrayList<>(Core.getCoreConfig().getStringList("story.acts"));
    }

    /**
     * Get the title of an act
     * @param act Act number, starting at 1
     * @return The title, or ??? if it has none yet
     */
    public static String getActTitle(int act) {
        List<String> titles = getActTitles();
        return act <= titles.size() && !titles.get(act - 1).isBlank() ? titles.get(act - 1) : "???";
    }

    /**
     * Give an act a title
     * @param act   Act number, starting at 1
     * @param title The title
     */
    public static void setActTitle(int act, String title) {
        List<String> titles = getActTitles();
        while (titles.size() < act) titles.add("");
        titles.set(act - 1, title);
        Core.getCoreConfig().set("story.acts", titles);
    }

    /**
     * Get the short description of an act
     * @param act Act number, starting at 1
     * @return The description, or null if it has none
     */
    public static String getActInfo(int act) {
        List<String> info = Core.getCoreConfig().getStringList("story.info");
        return act <= info.size() && !info.get(act - 1).isBlank() ? info.get(act - 1) : null;
    }

    /**
     * Give an act a short description
     * @param act  Act number, starting at 1
     * @param text The description
     */
    public static void setActInfo(int act, String text) {
        List<String> info = new ArrayList<>(Core.getCoreConfig().getStringList("story.info"));
        while (info.size() < act) info.add("");
        info.set(act - 1, text);
        Core.getCoreConfig().set("story.info", info);
    }

    /**
     * Get every event, past and planned, sorted from oldest to newest
     * @return List of events
     */
    public static List<ServerEvent> getEvents() {
        List<ServerEvent> events = new ArrayList<>();
        for (Map<?, ?> map : Core.getCoreConfig().getMapList("events")) {
            Object name = map.get("name");
            Object time = map.get("time");
            if (name == null || !(time instanceof Number number)) continue;
            Object recap = map.get("recap");
            events.add(new ServerEvent(name.toString(), number.longValue(), recap != null ? recap.toString() : null));
        }
        events.sort(Comparator.comparingLong(ServerEvent::time));
        return events;
    }

    /**
     * Replace all events in the config
     * @param events List of events
     */
    public static void setEvents(List<ServerEvent> events) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServerEvent event : events) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", event.name());
            map.put("time", event.time());
            if (event.recap() != null) map.put("recap", event.recap());
            list.add(map);
        }
        Core.getCoreConfig().set("events", list);
    }

    /**
     * Plan a new event
     * @param name The event name
     * @param time Epoch milliseconds the event starts
     */
    public static void addEvent(String name, long time) {
        List<ServerEvent> events = getEvents();
        events.add(new ServerEvent(name, time, null));
        setEvents(events);
    }

    /**
     * Get the event that is going on now or comes next
     * @return The event, or null if nothing is going on or planned
     */
    public static ServerEvent getNextEvent() {
        long liveUntil = System.currentTimeMillis() - getEventLiveMillis();
        return getEvents().stream().filter(e -> e.time() > liveUntil).findFirst().orElse(null);
    }

    /**
     * Get the most recent event that has already started
     * @return The event, or null if no event has started yet
     */
    public static ServerEvent getLastStartedEvent() {
        ServerEvent last = null;
        for (ServerEvent event : getEvents()) {
            if (event.hasStarted()) last = event;
        }
        return last;
    }

    /**
     * How long an event counts as going on after it started
     * @return Milliseconds
     */
    public static long getEventLiveMillis() {
        return (long) (Core.getCoreConfig().getDouble("event.live-hours", 3) * 3_600_000L);
    }

    /**
     * Get the timezone event times are entered in
     * @return Timezone id, e.g. Europe/Amsterdam
     */
    public static String getEventTimezone() {
        return Core.getCoreConfig().getString("event.timezone", "Europe/Amsterdam");
    }

    /**
     * Check if the website takes whitelist requests
     * @return true if requests are open, false otherwise
     */
    public static boolean isWhitelistRequestsOpen() {
        return Core.getCoreConfig().getBoolean("web.whitelist-requests.open", true);
    }

    /**
     * Get how many whitelist requests can be open at once
     * @return Maximum number of open requests
     */
    public static int getMaxWhitelistRequests() {
        return Core.getCoreConfig().getInt("web.whitelist-requests.max-pending", 100);
    }

    /**
     * Save the configuration to disk
     */
    public static void saveConfig() {
        Core.getInstance().saveConfig();
    }

    /**
     * Reload the Core configuration
     */
    public static void reloadConfig() {
        Core.getInstance().reloadConfig();
    }
}
