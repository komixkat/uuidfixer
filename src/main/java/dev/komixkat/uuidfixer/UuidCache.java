package dev.komixkat.uuidfixer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Caches username -> UUID lookups on disk so repeat joins skip the API call. */
final class UuidCache {
    private static final long POSITIVE_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final long NEGATIVE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    private UuidCache() {}

    static final class Entry {
        final UUID uuid;
        final long timestamp;
        /** null for entries written before this field existed. */
        final Boolean manual;

        Entry(UUID uuid, long timestamp) {
            this(uuid, timestamp, Boolean.FALSE);
        }

        Entry(UUID uuid, long timestamp, Boolean manual) {
            this.uuid = uuid;
            this.timestamp = timestamp;
            this.manual = manual == null ? Boolean.FALSE : manual;
        }
    }

    static Entry peek(String username) {
        return cache.get(key(username));
    }

    /** A verified UUID that is still within its normal TTL. */
    static UUID get(String username) {
        Entry entry = peek(username);
        if (entry == null || entry.uuid == null) return null;
        if (Boolean.TRUE.equals(entry.manual) || fresh(entry)) return entry.uuid;
        return null;
    }

    /**
     * The last verified UUID, even if its TTL has expired. Used only when the
     * Mojang API is temporarily unavailable, so a name that changes hands can
     * still be re-resolved once the API recovers.
     */
    static UUID getStale(String username) {
        Entry entry = peek(username);
        return entry == null ? null : entry.uuid;
    }

    static boolean isFresh(String username) {
        Entry entry = peek(username);
        return entry != null && entry.uuid != null && (Boolean.TRUE.equals(entry.manual) || fresh(entry));
    }

    /** True only for a still-valid cached "name not found" result. */
    static boolean hasNegative(String username) {
        Entry entry = peek(username);
        return entry != null && entry.uuid == null
                && System.currentTimeMillis() - entry.timestamp <= NEGATIVE_TTL_MILLIS;
    }

    static void put(String username, UUID uuid) {
        cache.put(key(username), new Entry(uuid, System.currentTimeMillis()));
        save();
    }

    static void putManual(String username, UUID uuid) {
        cache.put(key(username), new Entry(uuid, System.currentTimeMillis(), Boolean.TRUE));
        save();
    }

    static boolean remove(String username) {
        boolean removed = cache.remove(key(username)) != null;
        if (removed) save();
        return removed;
    }

    static void reload() {
        cache.clear();
        load();
    }

    static int size() {
        return cache.size();
    }

    static int manualCount() {
        int count = 0;
        for (Entry entry : cache.values()) {
            if (entry.uuid != null && Boolean.TRUE.equals(entry.manual)) count++;
        }
        return count;
    }

    static int negativeCount() {
        int count = 0;
        for (Entry entry : cache.values()) {
            if (entry.uuid == null) count++;
        }
        return count;
    }

    private static boolean fresh(Entry entry) {
        return System.currentTimeMillis() - entry.timestamp <= POSITIVE_TTL_MILLIS;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static Path cacheFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(UuidFixerMod.MOD_ID).resolve("cache.json");
    }

    static void load() {
        Path file = cacheFile();
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Entry> loaded = GSON.fromJson(reader, MAP_TYPE);
            if (loaded != null) cache.putAll(loaded);
        } catch (Exception e) {
            UuidFixerMod.LOGGER.warn("[{}] failed to read cache.json: {}", UuidFixerMod.MOD_ID, e.toString());
        }
    }

    static void save() {
        Path file = cacheFile();
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(cache, MAP_TYPE, writer);
            }
        } catch (Exception e) {
            UuidFixerMod.LOGGER.warn("[{}] failed to save cache.json: {}", UuidFixerMod.MOD_ID, e.toString());
        }
    }
}
