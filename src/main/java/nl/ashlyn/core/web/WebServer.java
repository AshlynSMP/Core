package nl.ashlyn.core.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import com.google.gson.JsonArray;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * Small HTTP API for the AshlynSMP website.
 * GET /api/status returns the live server state as JSON.
 * /api/whitelist takes whitelist requests, see WhitelistHandler.
 */
public class WebServer {

    private final Core plugin;
    private HttpServer server;
    private BukkitTask snapshotTask;
    private BukkitTask rosterTask;
    private JsonArray roster = new JsonArray();
    private ActivityFeed activity;
    private ExecutorService executor;
    private volatile String snapshot = "{\"online\":true}";

    public WebServer(Core plugin) {
        this.plugin = plugin;
    }

    public void start() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("web.enabled", false)) return;

        String bind = config.getString("web.bind", "0.0.0.0");
        int port = config.getInt("web.port", 8150);
        String allowedOrigin = config.getString("web.allowed-origin", "*");
        boolean showPlayers = config.getBoolean("web.show-players", true);
        int refreshSeconds = Math.max(1, config.getInt("web.refresh-seconds", 5));
        int rosterRefreshSeconds = Math.max(10, config.getInt("web.roster-refresh-seconds", 60));

        if (showPlayers) {
            activity = new ActivityFeed(config.getInt("web.activity-size", 30));
            plugin.getServer().getPluginManager().registerEvents(activity, plugin);
        }

        if (showPlayers) {
            rosterTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> roster = StatusSnapshot.buildRoster(), 0L, rosterRefreshSeconds * 20L);
        }
        snapshotTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> snapshot = StatusSnapshot.build(showPlayers, roster, activity).toString(), 1L, refreshSeconds * 20L);

        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not start web API on " + bind + ":" + port + ": " + e.getMessage());
            return;
        }

        WhitelistHandler whitelist = new WhitelistHandler(plugin);
        server.createContext("/api/status", exchange -> {
            if (preflight(exchange, allowedOrigin)) return;
            handle(exchange);
        });
        server.createContext("/api/whitelist", exchange -> {
            if (preflight(exchange, allowedOrigin)) return;
            whitelist.handle(exchange);
        });
        executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.start();

        Logger.logCore("Web API running on " + ChatColor.GREEN + bind + ":" + port);
    }

    public void stop() {
        if (snapshotTask != null) snapshotTask.cancel();
        if (rosterTask != null) rosterTask.cancel();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    // Adds the CORS headers and answers the browser's preflight request; returns true when it was one
    private static boolean preflight(HttpExchange exchange, String allowedOrigin) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if (!exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) return false;
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] body = snapshot.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
