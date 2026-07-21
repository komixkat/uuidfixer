package dev.komixkat.uuidfixer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Caches username to UUID lookups on disk so repeat joins skip the API call. */
final class UuidCache {

	private static final long POSITIVE_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);
	private static final long NEGATIVE_TTL_MILLIS = TimeUnit.HOURS.toMillis(1);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
	private static final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

	private UuidCache() {
	}

	static UUID get(String username) {
		Entry entry = cache.get(key(username));
		if (entry == null) {
			return null;
		}
		long ttl = entry.uuid == null ? NEGATIVE_TTL_MILLIS : POSITIVE_TTL_MILLIS;
		if (System.currentTimeMillis() - entry.timestamp > ttl) {
			return null;
		}
		return entry.uuid;
	}

	static boolean has(String username) {
		return get(username) != null || isCachedMiss(username);
	}

	private static boolean isCachedMiss(String username) {
		Entry entry = cache.get(key(username));
		return entry != null && entry.uuid == null
				&& System.currentTimeMillis() - entry.timestamp <= NEGATIVE_TTL_MILLIS;
	}

	static void put(String username, UUID uuid) {
		cache.put(key(username), new Entry(uuid, System.currentTimeMillis()));
	}

	private static String key(String username) {
		return username.toLowerCase(Locale.ROOT);
	}

	private static Path cacheFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(UuidFixerMod.MOD_ID).resolve("cache.json");
	}

	static void load() {
		Path file = cacheFile();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, Entry> loaded = GSON.fromJson(reader, MAP_TYPE);
			if (loaded != null) {
				cache.putAll(loaded);
			}
		} catch (IOException | RuntimeException e) {
			UuidFixerMod.LOGGER.warn("[{}] failed to read cache.json: {}", UuidFixerMod.MOD_ID, e.toString());
		}
	}

	static void save() {
		Path file = cacheFile();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(cache, MAP_TYPE, writer);
			}
		} catch (IOException e) {
			UuidFixerMod.LOGGER.warn("[{}] failed to save cache.json: {}", UuidFixerMod.MOD_ID, e.toString());
		}
	}

	private static final class Entry {
		final UUID uuid;
		final long timestamp;

		Entry(UUID uuid, long timestamp) {
			this.uuid = uuid;
			this.timestamp = timestamp;
		}
	}
}
