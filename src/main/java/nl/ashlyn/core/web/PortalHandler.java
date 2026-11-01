package nl.ashlyn.core.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;
import nl.ashlyn.core.api.EventSignups;
import nl.ashlyn.core.api.PlayerLinks;
import nl.ashlyn.core.api.ServerEvent;
import nl.ashlyn.core.wrapped.StatsTable;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * The player portal on the website. Players sign in with Watchdog and send its token along.
 *   POST   /api/portal/login   {"code", "verifier", "redirectUri"} swaps the login code for a token
 *   GET    /api/portal/me      the signed-in player, their link, stats, ranks and the next event
 *   POST   /api/portal/link    get a code to type in-game with /koppel
 *   DELETE /api/portal/link    remove the link
 *   GET    /api/portal/events  planned and past events, with who comes and who was there
 *   POST   /api/portal/rsvp    {"time", "going": true | false | null} answer for an event
 *   /api/portal/admin/...      leaders only, see PortalAdmin
 * During maintenance only leaders get in. Accounts that aren't linked yet can still link,
 * since only leaders can join the server to type the code.
 */
public class PortalHandler {

    private static final int MAX_BODY = 4096;
    private static final int MAX_LOGINS = 10;
    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final Core plugin;
    private final WatchdogAuth watchdog;
    private final PortalViews views;
    private final PortalAdmin admin;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public PortalHandler(Core plugin, WatchdogAuth watchdog, ActivityFeed activity, StatsTable stats) {
        this.plugin = plugin;
        this.watchdog = watchdog;
        this.views = new PortalViews(plugin, activity, stats);
        this.admin = new PortalAdmin(this);
    }

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        if (path.equals("/api/portal/login")) {
            if (!method.equals("POST")) notAllowed(exchange);
            else login(exchange);
            return;
        }

        String token = bearer(exchange);
        WatchdogAuth.Identity identity = token != null ? watchdog.verify(token) : null;
        if (identity == null) {
            send(exchange, 401, error("unauthorized"));
            return;
        }

        JsonObject blocked = callSync(() -> maintenance(identity, path), null);
        if (blocked == null || blocked.has("error")) {
            reply(exchange, blocked);
            return;
        }

        if (path.startsWith("/api/portal/admin")) {
            admin.handle(exchange, identity, path, method);
            return;
        }

        switch (path) {
            case "/api/portal/me" -> {
                if (!method.equals("GET")) notAllowed(exchange);
                else reply(exchange, callSync(() -> views.me(identity), null));
            }
            case "/api/portal/events" -> {
                if (!method.equals("GET")) notAllowed(exchange);
                else reply(exchange, callSync(() -> views.events(identity), null));
            }
            case "/api/portal/link" -> {
                if (method.equals("POST")) reply(exchange, callSync(() -> code(identity), null));
                else if (method.equals("DELETE")) reply(exchange, callSync(() -> ok(PlayerLinks.unlink(identity.subject())), null));
                else notAllowed(exchange);
            }
            case "/api/portal/rsvp" -> {
                if (!method.equals("POST")) {
                    notAllowed(exchange);
                    return;
                }
                JsonObject body = body(exchange);
                if (body != null) reply(exchange, callSync(() -> rsvp(identity, body), null));
            }
            default -> send(exchange, 404, error("not-found"));
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!allow(clientIp(exchange))) {
            send(exchange, 429, error("too-many"));
            return;
        }
        JsonObject body = body(exchange);
        if (body == null) return;

        String code = string(body, "code");
        String verifier = string(body, "verifier");
        String redirectUri = string(body, "redirectUri");
        if (code == null || verifier == null || redirectUri == null) {
            send(exchange, 400, error("bad-request"));
            return;
        }

        JsonObject tokens = watchdog.exchange(code, verifier, redirectUri);
        if (tokens == null) {
            send(exchange, 401, error("login-failed"));
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("token", tokens.get("access_token").getAsString());
        json.addProperty("expiresIn", tokens.has("expires_in") ? tokens.get("expires_in").getAsLong() : 3600);
        send(exchange, 200, json);
    }

    private JsonObject maintenance(WatchdogAuth.Identity identity, String path) {
        if (!CoreConfig.isMaintenance()) return new JsonObject();
        PlayerLinks.Link link = PlayerLinks.get(identity.subject());
        if (link == null && (path.equals("/api/portal/me") || path.equals("/api/portal/link"))) return new JsonObject();
        if (link != null && PortalViews.isLeader(Bukkit.getOfflinePlayer(link.uuid()))) return new JsonObject();

        JsonObject json = error("maintenance");
        json.addProperty("reason", CoreConfig.getMaintenanceReason());
        return json;
    }

    private JsonObject code(WatchdogAuth.Identity identity) {
        if (PlayerLinks.get(identity.subject()) != null) return error("already-linked");
        return PortalViews.codeJson(PlayerLinks.code(identity.subject(), identity.name()));
    }

    private JsonObject rsvp(WatchdogAuth.Identity identity, JsonObject body) {
        PlayerLinks.Link link = PlayerLinks.get(identity.subject());
        if (link == null) return error("not-linked");
        JsonElement time = body.get("time");
        JsonElement going = body.get("going");
        if (time == null || !time.isJsonPrimitive() || going == null) return error("bad-request");

        long start = time.getAsLong();
        long liveUntil = System.currentTimeMillis() - CoreConfig.getEventLiveMillis();
        boolean planned = CoreConfig.getEvents().stream().map(ServerEvent::time).anyMatch(t -> t == start && t > liveUntil);
        if (!planned) return error("no-event");

        EventSignups.setAnswer(start, link.uuid(), going.isJsonNull() ? null : going.getAsBoolean());
        return ok(true);
    }

    <T> T callSync(Callable<T> task, T fallback) {
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, task).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean allow(String ip) {
        long now = System.currentTimeMillis();
        if (attempts.size() > 500) attempts.values().removeIf(t -> t.isEmpty() || now - t.peekLast() > WINDOW_MILLIS);
        Deque<Long> times = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MILLIS) times.pollFirst();
            if (times.size() >= MAX_LOGINS) return false;
            times.addLast(now);
            return true;
        }
    }

    static void reply(HttpExchange exchange, JsonObject json) throws IOException {
        if (json == null) send(exchange, 503, error("unavailable"));
        else if (!json.has("error")) send(exchange, 200, json);
        else send(exchange, switch (json.get("error").getAsString()) {
            case "forbidden" -> 403;
            case "bad-request" -> 400;
            case "no-event", "not-found" -> 404;
            case "maintenance" -> 503;
            default -> 409;
        }, json);
    }

    static JsonObject body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                send(exchange, 413, error("too-large"));
                return null;
            }
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            send(exchange, 400, error("bad-request"));
            return null;
        }
    }

    private static String bearer(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7).trim();
        return token.isEmpty() || token.length() > 4096 ? null : token;
    }

    private static String clientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("CF-Connecting-IP");
        return forwarded != null ? forwarded : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    static JsonObject ok(boolean value) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", value);
        return json;
    }

    static JsonObject error(String code) {
        JsonObject json = new JsonObject();
        json.addProperty("error", code);
        return json;
    }

    static void notAllowed(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    static void send(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
