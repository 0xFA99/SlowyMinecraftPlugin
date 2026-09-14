package dev.slowy.core.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

@NullMarked
public final class SkinFetcher {

    private static final String PLAYERDB_API = "https://playerdb.co/api/player/minecraft/%s";
    private static final String MOJANG_UUID_API = "https://api.mojang.com/users/profiles/minecraft/%s";
    private static final String MOJANG_SESSION_API = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";
    private static final String MINESKIN_V2_API = "https://api.mineskin.org/v2/skins/%s";
    private static final String MINESKIN_GENERATE_API = "https://api.mineskin.org/generate/url";

    private static final Pattern MINESKIN_URL_PATTERN =
            Pattern.compile("(?:mineskin\\.org/skins/|minesk\\.in/)([a-zA-Z0-9_-]+)");

    // Matches: 36-char UUID, 32-char hex, 24-char hex, 8-char shortId, or numeric ID
    private static final Pattern MINESKIN_ID_PATTERN =
            Pattern.compile("^(?:[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}|[a-fA-F0-9]{32}|[a-fA-F0-9]{24}|[a-fA-F0-9]{8}|[0-9]{1,12})$");

    private static final Duration TIMEOUT_FAST = Duration.ofSeconds(6);
    private static final Duration TIMEOUT_GENERATE = Duration.ofSeconds(12);

    private final HttpClient httpClient;
    private final Logger logger;

    public SkinFetcher(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_FAST)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static boolean isMineSkinInput(String input) {
        if (input == null || input.isBlank()) return false;
        String clean = input.trim();
        return extractMineSkinId(clean) != null
                || (clean.startsWith("http://") || clean.startsWith("https://"));
    }

    public @Nullable SkinData fetchSkinSync(String target) {
        if (target.isBlank()) return null;
        String clean = target.trim();

        String mineSkinId = extractMineSkinId(clean);
        if (mineSkinId != null) {
            SkinData data = fetchFromMineSkin(mineSkinId);
            if (data != null && data.isValid()) return data;
        }

        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            SkinData generated = fetchFromMineSkinGenerator(clean);
            if (generated != null && generated.isValid()) return generated;
        }

        SkinData mojangData = fetchFromMojang(clean);
        if (mojangData != null && mojangData.isValid()) {
            return mojangData;
        }

        // Fallback: PlayerDB (aktif dipelihara)
        return fetchFromPlayerDb(clean);
    }

    private @Nullable SkinData fetchFromPlayerDb(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(String.format(PLAYERDB_API, encoded)))
                .timeout(TIMEOUT_FAST)
                .header("User-Agent", "SlowyCore2/2.0.0")
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null) return null;
    
            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!root.has("data")) return null;
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("player")) return null;
            JsonObject player = data.getAsJsonObject("player");
            if (!player.has("skin_texture") || !player.has("raw_id")) return null;

            // PlayerDB mengekspos texture URL langsung, bukan value+signature signed.
            // Untuk skin ber-signature valid, tetap andalkan Mojang; ini hanya fallback
            // saat Mojang API gagal (mis. rate limit), jadi kita generate ulang lewat MineSkin.
            String skinTextureUrl = player.get("skin_texture").getAsString();
            return fetchFromMineSkinGenerator(skinTextureUrl);
        } catch (Exception e) {
            logger.debug("Failed to fetch skin from PlayerDB for {}: {}", username, e.getMessage());
            return null;
        }
    }

    public @Nullable SkinData fetchFromMineSkin(String skinId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(MINESKIN_V2_API, skinId.trim())))
                    .timeout(TIMEOUT_FAST)
                    .header("User-Agent", "SlowyCore2/2.0.0 (MineSkin Engine)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                return parseMineSkinResponse(root, skinId);
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch skin from MineSkin for {}: {}", skinId, e.getMessage());
        }
        return null;
    }

    public @Nullable SkinData fetchFromMineSkinGenerator(String imageUrl) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("url", imageUrl);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MINESKIN_GENERATE_API))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SlowyCore2/2.0.0 (MineSkin Engine)")
                    .header("Accept", "application/json")
                    .timeout(TIMEOUT_GENERATE)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                return parseMineSkinResponse(root, imageUrl);
            }
        } catch (Exception e) {
            logger.debug("Failed to generate skin from MineSkin for {}: {}", imageUrl, e.getMessage());
        }
        return null;
    }

    private @Nullable SkinData parseMineSkinResponse(JsonObject root, String fallbackName) {
        try {
            // Schema 1: MineSkin v2 API standard {"skin": {"name": ..., "texture": {"data": {"value": ..., "signature": ...}, "url": {"skin": ...}}}}
            if (root.has("skin") && root.get("skin").isJsonObject()) {
                JsonObject skinObj = root.getAsJsonObject("skin");
                String skinName = skinObj.has("name") && !skinObj.get("name").isJsonNull()
                        ? skinObj.get("name").getAsString()
                        : fallbackName;

                if (skinObj.has("texture") && skinObj.get("texture").isJsonObject()) {
                    JsonObject texObj = skinObj.getAsJsonObject("texture");
                    if (texObj.has("data") && texObj.get("data").isJsonObject()) {
                        JsonObject dataObj = texObj.getAsJsonObject("data");
                        String val = dataObj.has("value") ? dataObj.get("value").getAsString() : null;
                        String sig = dataObj.has("signature") ? dataObj.get("signature").getAsString() : null;

                        String url = null;
                        if (texObj.has("url") && texObj.get("url").isJsonObject()) {
                            JsonObject urlObj = texObj.getAsJsonObject("url");
                            if (urlObj.has("skin")) url = urlObj.get("skin").getAsString();
                        }

                        if (val != null && !val.isBlank() && sig != null && !sig.isBlank()) {
                            return new SkinData(skinName, val, sig, url, System.currentTimeMillis());
                        }
                    }
                }
            }

            // Schema 2: MineSkin generate API {"data": {"texture": {"value": ..., "signature": ..., "url": ...}}}
            if (root.has("data") && root.get("data").isJsonObject()) {
                JsonObject dataObj = root.getAsJsonObject("data");
                if (dataObj.has("texture") && dataObj.get("texture").isJsonObject()) {
                    JsonObject texObj = dataObj.getAsJsonObject("texture");
                    String val = texObj.has("value") ? texObj.get("value").getAsString() : null;
                    String sig = texObj.has("signature") ? texObj.get("signature").getAsString() : null;
                    String url = texObj.has("url") ? texObj.get("url").getAsString() : null;

                    if (val != null && !val.isBlank() && sig != null && !sig.isBlank()) {
                        return new SkinData(fallbackName, val, sig, url, System.currentTimeMillis());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error parsing MineSkin response for {}: {}", fallbackName, e.getMessage());
        }
        return null;
    }

    private @Nullable SkinData fetchFromMojang(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest uuidReq = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(MOJANG_UUID_API, encoded)))
                    .timeout(TIMEOUT_FAST)
                    .header("User-Agent", "SlowyCore2/2.0.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> uuidRes = httpClient.send(uuidReq, HttpResponse.BodyHandlers.ofString());
            if (uuidRes.statusCode() != 200 || uuidRes.body() == null || uuidRes.body().isBlank()) {
                return null;
            }

            JsonObject uuidObj = JsonParser.parseString(uuidRes.body()).getAsJsonObject();
            if (!uuidObj.has("id")) return null;
            String mojangId = uuidObj.get("id").getAsString();
            String officialName = uuidObj.has("name") ? uuidObj.get("name").getAsString() : username;

            HttpRequest sessionReq = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(MOJANG_SESSION_API, mojangId)))
                    .timeout(TIMEOUT_FAST)
                    .header("User-Agent", "SlowyCore2/2.0.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> sessionRes = httpClient.send(sessionReq, HttpResponse.BodyHandlers.ofString());
            if (sessionRes.statusCode() != 200 || sessionRes.body() == null || sessionRes.body().isBlank()) {
                return null;
            }

            JsonObject sessionObj = JsonParser.parseString(sessionRes.body()).getAsJsonObject();
            if (!sessionObj.has("properties")) return null;

            JsonArray properties = sessionObj.getAsJsonArray("properties");
            for (JsonElement elem : properties) {
                if (!elem.isJsonObject()) continue;
                JsonObject prop = elem.getAsJsonObject();
                if (prop.has("name") && "textures".equalsIgnoreCase(prop.get("name").getAsString())) {
                    String value = prop.has("value") ? prop.get("value").getAsString() : null;
                    String signature = prop.has("signature") ? prop.get("signature").getAsString() : null;
                    if (value != null && !value.isBlank() && signature != null && !signature.isBlank()) {
                        String textureUrl = extractTextureUrl(value);
                        return new SkinData(officialName, value, signature, textureUrl, System.currentTimeMillis());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch skin from Mojang API for {}: {}", username, e.getMessage());
        }
        return null;
    }

    public static @Nullable String extractMineSkinId(String input) {
        var matcher = MINESKIN_URL_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (MINESKIN_ID_PATTERN.matcher(input).matches()) {
            return input;
        }
        return null;
    }

    private @Nullable String extractTextureUrl(String base64Value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Value);
            JsonObject json = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures != null && textures.has("SKIN")) {
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (skin.has("url")) {
                    return skin.get("url").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
