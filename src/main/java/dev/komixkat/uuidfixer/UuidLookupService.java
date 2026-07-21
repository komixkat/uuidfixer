package dev.komixkat.uuidfixer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Looks up a player's real Mojang UUID by username. Runs on the login
 * thread, so timeouts are short: a slow or unreachable API should never
 * hang a player's connection.
 */
public final class UuidLookupService {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.build();

	private UuidLookupService() {
	}

	public static UUID lookup(String username) {
		UUID cached = UuidCache.get(username);
		if (cached != null) {
			return cached;
		}
		if (UuidCache.has(username)) {
			return null;
		}

		UUID result = fetchFromMojang(username);
		UuidCache.put(username, result);
		return result;
	}

	private static UUID fetchFromMojang(String username) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
					.timeout(TIMEOUT)
					.GET()
					.build();

			HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				return parseUuid(response.body());
			}
			if (response.statusCode() != 404 && response.statusCode() != 204) {
				UuidFixerMod.LOGGER.warn("[{}] Mojang API returned {} for '{}'",
						UuidFixerMod.MOD_ID, response.statusCode(), username);
			}
			return null;
		} catch (Exception e) {
			UuidFixerMod.LOGGER.warn("[{}] lookup failed for '{}', using offline UUID: {}",
					UuidFixerMod.MOD_ID, username, e.toString());
			return null;
		}
	}

	private static UUID parseUuid(String responseBody) {
		try {
			JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
			String raw = json.get("id").getAsString();
			return UUID.fromString(raw.replaceFirst(
					"(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
		} catch (RuntimeException e) {
			UuidFixerMod.LOGGER.warn("[{}] unexpected Mojang API response: {}", UuidFixerMod.MOD_ID, responseBody);
			return null;
		}
	}
}
