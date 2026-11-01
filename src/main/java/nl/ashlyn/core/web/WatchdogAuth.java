package nl.ashlyn.core.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ashlyn on 01/11/2026
 *
 * Talks to Watchdog for the player portal. Swaps the login code for an access token,
 * because Watchdog doesn't answer the browser directly, and asks /connect/userinfo
 * who a token belongs to. Answers are remembered for a few minutes.
 */
public class WatchdogAuth {

    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_CACHE = 500;

    private final String url;
    private final String clientId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * Who a Watchdog token belongs to
     * @param subject The Watchdog account id
     * @param name    The Watchdog username
     */
    public record Identity(String subject, String name) {}

    private record Cached(Identity identity, long expires) {}

    public WatchdogAuth(String url, String clientId) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.clientId = clientId;
    }

    /**
     * Swap the code Watchdog sent back after login for an access token
     * @param code        The authorization code
     * @param verifier    The PKCE code verifier
     * @param redirectUri The redirect URI used for the login
     * @return Watchdog's token response, or null if Watchdog refused
     */
    public JsonObject exchange(String code, String verifier, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("code", code);
        form.put("code_verifier", verifier);
        form.put("redirect_uri", redirectUri);
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (!body.isEmpty()) body.append('&');
            body.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });

        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        JsonObject json = send(request);
        return json != null && json.has("access_token") ? json : null;
    }

    /**
     * Find out who a token belongs to
     * @param token The access token
     * @return The identity, or null if the token isn't valid
     */
    public Identity verify(String token) {
        long now = System.currentTimeMillis();
        String key = hash(token);
        Cached cached = cache.get(key);
        if (cached != null && cached.expires() > now) return cached.identity();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/connect/userinfo"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        JsonObject json = send(request);
        if (json == null || !json.has("sub")) return null;

        String name = string(json, "preferred_username");
        if (name == null) name = string(json, "name");
        Identity identity = new Identity(json.get("sub").getAsString(), name != null ? name : "?");
        if (cache.size() >= MAX_CACHE) cache.values().removeIf(c -> c.expires() <= now);
        if (cache.size() < MAX_CACHE) cache.put(key, new Cached(identity, now + CACHE_MILLIS));
        return identity;
    }

    private JsonObject send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
