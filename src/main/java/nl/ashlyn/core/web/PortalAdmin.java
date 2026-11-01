package nl.ashlyn.core.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.PlayerLinks;
import nl.ashlyn.core.commands.MaintenanceCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * The leaders' part of the portal. Only for players linked to a leader or op account.
 *   GET  /api/portal/admin           open requests, server settings and who is linked
 *   POST /api/portal/admin/requests  {"name", "action": "accept" | "deny"}
 *   POST /api/portal/admin/events    {"name", "time"} plan an event
 *   POST /api/portal/admin/settings  {"maintenance", "reason", "whitelistOpen"} each optional
 */
public class PortalAdmin {

    private static final int MAX_EVENT_NAME = 60;
    private static final int MAX_REASON = 120;

    private final PortalHandler portal;

    public PortalAdmin(PortalHandler portal) {
        this.portal = portal;
    }

    void handle(HttpExchange exchange, WatchdogAuth.Identity identity, String path, String method) throws IOException {
        Boolean leader = portal.callSync(() -> {
            PlayerLinks.Link link = PlayerLinks.get(identity.subject());
            return link != null && PortalViews.isLeader(Bukkit.getOfflinePlayer(link.uuid()));
        }, null);
        if (leader == null) {
            PortalHandler.reply(exchange, null);
            return;
        }
        if (!leader) {
            PortalHandler.reply(exchange, PortalHandler.error("forbidden"));
            return;
        }

        if (path.equals("/api/portal/admin")) {
            if (!method.equals("GET")) PortalHandler.notAllowed(exchange);
            else PortalHandler.reply(exchange, portal.callSync(this::overview, null));
            return;
        }
        if (!method.equals("POST")) {
            PortalHandler.notAllowed(exchange);
            return;
        }
        JsonObject body = PortalHandler.body(exchange);
        if (body == null) return;

        switch (path) {
            case "/api/portal/admin/requests" -> PortalHandler.reply(exchange, request(body));
            case "/api/portal/admin/events" -> PortalHandler.reply(exchange, portal.callSync(() -> event(body), null));
            case "/api/portal/admin/settings" -> PortalHandler.reply(exchange, portal.callSync(() -> settings(body), null));
            default -> PortalHandler.send(exchange, 404, PortalHandler.error("not-found"));
        }
    }

    private JsonObject overview() {
        JsonObject json = new JsonObject();
        JsonArray requests = new JsonArray();
        for (WhitelistRequests.Request request : WhitelistRequests.getRequests()) {
            JsonObject r = new JsonObject();
            r.addProperty("name", request.name());
            r.addProperty("realName", request.realName());
            r.addProperty("time", request.time());
            requests.add(r);
        }
        json.add("requests", requests);
        json.addProperty("whitelistOpen", CoreConfig.isWhitelistRequestsOpen());

        JsonObject maintenance = new JsonObject();
        maintenance.addProperty("enabled", CoreConfig.isMaintenance());
        maintenance.addProperty("reason", CoreConfig.getMaintenanceReason());
        json.add("maintenance", maintenance);

        JsonArray linked = new JsonArray();
        Set<UUID> linkedIds = new HashSet<>();
        for (PlayerLinks.Link link : PlayerLinks.all()) {
            linkedIds.add(link.uuid());
            JsonObject l = new JsonObject();
            String name = Bukkit.getOfflinePlayer(link.uuid()).getName();
            l.addProperty("name", name != null ? name : "?");
            l.addProperty("watchdog", link.watchdog());
            linked.add(l);
        }
        json.add("linked", linked);

        JsonArray unlinked = new JsonArray();
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null || !player.hasPlayedBefore() && !player.isOnline()) continue;
            if (!linkedIds.contains(player.getUniqueId())) unlinked.add(player.getName());
        }
        json.add("unlinked", unlinked);
        return json;
    }

    private JsonObject request(JsonObject body) {
        String name = PortalHandler.string(body, "name");
        String action = PortalHandler.string(body, "action");
        if (name == null || !"accept".equals(action) && !"deny".equals(action)) return PortalHandler.error("bad-request");

        CompletableFuture<String> accepted = portal.callSync(() -> {
            WhitelistRequests.Request request = WhitelistRequests.find(name);
            if (request == null) return null;
            if (action.equals("deny")) {
                WhitelistRequests.remove(request.name());
                return CompletableFuture.completedFuture(request.name());
            }
            return WhitelistRequests.accept(request);
        }, null);
        if (accepted == null) return PortalHandler.error("not-found");

        try {
            String result = accepted.get(15, TimeUnit.SECONDS);
            return result != null ? PortalHandler.ok(true) : PortalHandler.error("unknown-account");
        } catch (Exception e) {
            return null;
        }
    }

    private JsonObject event(JsonObject body) {
        String name = PortalHandler.string(body, "name");
        JsonElement time = body.get("time");
        if (name == null || name.isBlank() || name.strip().length() > MAX_EVENT_NAME || time == null || !time.isJsonPrimitive()) {
            return PortalHandler.error("bad-request");
        }
        long start = time.getAsLong();
        if (start <= System.currentTimeMillis()) return PortalHandler.error("in-the-past");
        CoreConfig.addEvent(name.strip(), start);
        CoreConfig.saveConfig();
        return PortalHandler.ok(true);
    }

    private JsonObject settings(JsonObject body) {
        JsonElement whitelist = body.get("whitelistOpen");
        if (whitelist != null && whitelist.isJsonPrimitive()) {
            CoreConfig.setWhitelistRequestsOpen(whitelist.getAsBoolean());
            CoreConfig.saveConfig();
        }
        JsonElement maintenance = body.get("maintenance");
        if (maintenance != null && maintenance.isJsonPrimitive()) {
            String reason = PortalHandler.string(body, "reason");
            if (reason != null) reason = reason.isBlank() ? null : reason.strip();
            if (reason != null && reason.length() > MAX_REASON) reason = reason.substring(0, MAX_REASON);
            MaintenanceCommand.set(maintenance.getAsBoolean(), reason);
        }
        return overview();
    }
}
