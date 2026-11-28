package dev.komixkat.uuidfixer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public final class UuidLookupService {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    /* A fixed set of locks keeps simultaneous joins for one name serialized
       without growing a lock entry for every username ever seen. */
    private static final Object[] LOCKS = new Object[64];

    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new Object();
    }

    private UuidLookupService() {}

    public static UUID lookup(String username) {
        synchronized (lockFor(username)) {
            UUID cached = UuidCache.get(username);
            if (cached != null) {
                UuidFixerMod.LOGGER.info("[{}] cache hit for '{}': {}", UuidFixerMod.MOD_ID, username, cached);
                return cached;
            }
            if (UuidCache.hasNegative(username)) {
                UuidFixerMod.LOGGER.info("[{}] cached miss for '{}', using offline UUID", UuidFixerMod.MOD_ID, username);
                return null;
            }

            UuidFixerMod.LOGGER.info("[{}] resolving '{}' via Mojang API", UuidFixerMod.MOD_ID, username);
            FetchResult result;
            try {
                result = fetchFromMojang(username);
            } catch (Exception e) {
                return reuseVerifiedOrOffline(username,
                        "the Mojang API lookup failed: " + e, true);
            }

            if (result.notFound) {
                /* A fresh verified entry wins over a 404, which Mojang can
                   also return while rate limiting. An expired entry is not
                   pinned forever: once the API says the name is unknown, we
                   fall back to the offline UUID and cache the miss briefly. */
                UUID existing = UuidCache.get(username);
                if (existing != null) {
                    UuidFixerMod.LOGGER.warn("[{}] Mojang returned 404/204 for '{}', keeping verified UUID {}",
                            UuidFixerMod.MOD_ID, username, existing);
                    return existing;
                }
                UuidFixerMod.LOGGER.info("[{}] Mojang returned 404/204 for '{}', using offline UUID",
                        UuidFixerMod.MOD_ID, username);
                PlayerData.warnIfDuplicateRisk(username, "Mojang reported the name as unknown");
                UuidCache.put(username, null);
                return null;
            }

            if (result.uuid == null) {
                return reuseVerifiedOrOffline(username, "Mojang returned an unusable response", true);
            }

            UuidFixerMod.LOGGER.info("[{}] resolved '{}' to {}", UuidFixerMod.MOD_ID, username, result.uuid);
            UuidCache.put(username, result.uuid);
            return result.uuid;
        }
    }

    /** Returns the last verified UUID on a temporary failure, otherwise offline. */
    private static UUID reuseVerifiedOrOffline(String username, String reason, boolean warnAboutDuplicate) {
        UUID verified = UuidCache.getStale(username);
        if (verified != null) {
            UuidFixerMod.LOGGER.warn("[{}] {} for '{}', reusing last verified UUID {}",
                    UuidFixerMod.MOD_ID, reason, username, verified);
            return verified;
        }
        UuidFixerMod.LOGGER.warn("[{}] {} for '{}', using offline UUID", UuidFixerMod.MOD_ID, reason, username);
        if (warnAboutDuplicate) PlayerData.warnIfDuplicateRisk(username, reason);
        return null;
    }

    /**
     * Queries the API ignoring the cache, without overwriting a manual
     * override. Used by /uuidfixer check.
     */
    public static CheckResult check(String username) {
        FetchResult result;
        try {
            result = fetchFromMojang(username);
        } catch (Exception e) {
            return new CheckResult(null, false, false, e.toString());
        }

        UuidCache.Entry entry = UuidCache.peek(username);
        boolean manualKept = entry != null && entry.uuid != null && Boolean.TRUE.equals(entry.manual);
        if (result.notFound) return new CheckResult(null, true, manualKept, null);
        if (result.uuid == null) {
            return new CheckResult(null, false, manualKept, "Mojang returned an unusable response");
        }
        if (!manualKept) UuidCache.put(username, result.uuid);
        return new CheckResult(result.uuid, false, manualKept, null);
    }

    public record CheckResult(UUID uuid, boolean notFound, boolean manualPreserved, String error) {}

    private static Object lockFor(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        int index = Math.floorMod(key.hashCode(), LOCKS.length);
        return LOCKS[index];
    }

    private static FetchResult fetchFromMojang(String username) throws Exception {
        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 200) return new FetchResult(parseUuid(response.body()), false);
        if (status == 404 || status == 204) return new FetchResult(null, true);
        UuidFixerMod.LOGGER.warn("[{}] Mojang API returned {} for '{}'", UuidFixerMod.MOD_ID, status, username);
        return new FetchResult(null, false);
    }

    private static UUID parseUuid(String body) {
        try {
            com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            String id = object.get("id").getAsString();
            String dashed = id.replaceFirst(
                    "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                    "$1-$2-$3-$4-$5");
            return UUID.fromString(dashed);
        } catch (RuntimeException e) {
            UuidFixerMod.LOGGER.warn("[{}] unexpected Mojang API response: {}", UuidFixerMod.MOD_ID, body);
            return null;
        }
    }

    private static final class FetchResult {
        final UUID uuid;
        final boolean notFound;

        FetchResult(UUID uuid, boolean notFound) {
            this.uuid = uuid;
            this.notFound = notFound;
        }
    }
}
