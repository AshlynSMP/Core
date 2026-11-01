package nl.ashlyn.core.web;

import com.sun.net.httpserver.HttpExchange;
import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Player heads and bodies for the website, drawn from the skins the server knows.
 *   GET /api/head/<uuid>.png  the face with the hat layer
 *   GET /api/body/<uuid>.png  the whole player from the front
 * Only for players who have been on the server. Images are kept in plugins/Core/skins
 * and drawn again from the latest skin once they're an hour old.
 */
public class HeadHandler {

    private static final Pattern PATH = Pattern.compile("^/api/(head|body)/([0-9a-fA-F-]{36})\\.png$");
    private static final long FRESH_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final int MAX_SKIN_BYTES = 64 * 1024;

    private final Core plugin;
    private final File folder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<UUID, CompletableFuture<Void>> refreshing = new ConcurrentHashMap<>();
    private final Map<UUID, String> skins = new ConcurrentHashMap<>();

    private record Known(boolean known, PlayerProfile profile) {}

    public HeadHandler(Core plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "skins");
    }

    public void handle(HttpExchange exchange) throws IOException {
        Matcher matcher = PATH.matcher(exchange.getRequestURI().getPath());
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET") || !matcher.matches()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        File file = new File(folder, uuid + "-" + matcher.group(1) + ".png");
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > FRESH_MILLIS) {
            CompletableFuture<Void> job = refresh(uuid);
            if (job != null && !file.isFile()) {
                try {
                    job.get(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
        }

        if (!file.isFile()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] body = Files.readAllBytes(file.toPath());
        exchange.getResponseHeaders().add("Content-Type", "image/png");
        exchange.getResponseHeaders().add("Cache-Control", "public, max-age=600");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private CompletableFuture<Void> refresh(UUID uuid) {
        CompletableFuture<Void> running = refreshing.get(uuid);
        if (running != null) return running;

        Known known = known(uuid);
        if (known == null || !known.known()) return null;

        CompletableFuture<PlayerProfile> profile = known.profile() != null
                ? CompletableFuture.completedFuture(known.profile())
                : Bukkit.createPlayerProfile(uuid).update();
        CompletableFuture<Void> job = profile.thenAcceptAsync(p -> draw(uuid, p.getTextures()));
        CompletableFuture<Void> existing = refreshing.putIfAbsent(uuid, job);
        if (existing != null) return existing;
        job.whenComplete((ignored, error) -> refreshing.remove(uuid));
        return job;
    }

    private Known known(UUID uuid) {
        try {
            return plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                Player online = player.getPlayer();
                return new Known(online != null || player.hasPlayedBefore(), online != null ? online.getPlayerProfile() : null);
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private void draw(UUID uuid, PlayerTextures textures) {
        URL skin = textures.getSkin();
        if (skin == null || !"textures.minecraft.net".equals(skin.getHost())) return;

        File head = new File(folder, uuid + "-head.png");
        File body = new File(folder, uuid + "-body.png");
        long now = System.currentTimeMillis();
        if (skin.toString().equals(skins.get(uuid)) && head.isFile() && body.isFile()) {
            head.setLastModified(now);
            body.setLastModified(now);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(skin.toString().replace("http://", "https://")))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > MAX_SKIN_BYTES) return;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64 && image.getHeight() != 32) return;

            boolean slim = textures.getSkinModel() == PlayerTextures.SkinModel.SLIM;
            folder.mkdirs();
            Files.write(head.toPath(), SkinRenderer.head(image));
            Files.write(body.toPath(), SkinRenderer.body(image, slim));
            skins.put(uuid, skin.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Logger.logCore(ChatColor.RED + "Could not draw the head of " + uuid + ": " + e.getMessage());
        }
    }
}
