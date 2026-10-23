package nl.ashlyn.core.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.api.CoreConfig;

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
 * Created by Ashlyn on 23/10/2026
 *
 * GET  /api/whitelist  tells the website whether requests are open
 * POST /api/whitelist  {"name": "...", "realName": "..."} files a whitelist request
 */
public class WhitelistHandler {

    private static final int MAX_BODY = 1024;
    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final Core plugin;
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public WhitelistHandler(Core plugin) {
        this.plugin = plugin;
    }

    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (method.equalsIgnoreCase("GET")) {
            boolean open = callSync(CoreConfig::isWhitelistRequestsOpen, false);
            JsonObject json = new JsonObject();
            json.addProperty("open", open);
            send(exchange, 200, json);
            return;
        }
        if (!method.equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        if (!allow(clientIp(exchange))) {
            send(exchange, 429, error("too-many"));
            return;
        }

        JsonObject body;
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) {
                send(exchange, 413, error("too-large"));
                return;
            }
            body = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            send(exchange, 400, error("bad-request"));
            return;
        }

        String name = string(body, "name");
        String realName = string(body, "realName");
        WhitelistRequests.Result result = callSync(() -> WhitelistRequests.submit(name, realName), null);

        if (result == null) {
            send(exchange, 503, error("unavailable"));
        } else if (result == WhitelistRequests.Result.OK) {
            JsonObject ok = new JsonObject();
            ok.addProperty("ok", true);
            send(exchange, 200, ok);
        } else {
            int status = switch (result) {
                case INVALID_NAME, MISSING_REAL_NAME -> 400;
                case CLOSED, FULL -> 403;
                default -> 409;
            };
            send(exchange, status, error(result.name().toLowerCase().replace('_', '-')));
        }
    }

    private <T> T callSync(Callable<T> task, T fallback) {
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
            if (times.size() >= MAX_PER_WINDOW) return false;
            times.addLast(now);
            return true;
        }
    }

    // Behind a Cloudflare Tunnel every request comes from the tunnel, the real visitor is in this header
    private static String clientIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("CF-Connecting-IP");
        return forwarded != null ? forwarded : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static JsonObject error(String code) {
        JsonObject json = new JsonObject();
        json.addProperty("error", code);
        return json;
    }

    private static void send(HttpExchange exchange, int status, JsonObject json) throws IOException {
        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
