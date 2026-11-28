# UUID Fixer

Assigns Mojang UUIDs to players joining an offline-mode server, allowing
in-game entities and other services (mods like Essential) to use the actual
UUID instead of a hash of the player's username.

Server-side only.

## Download

Latest build: https://github.com/komixkat/uuidfixer/releases/latest

## Supported Versions

This mod supports the following Minecraft versions:

| Minecraft Version | Release |
|-------------------|---------|
| 26.2 | [26.2](https://github.com/komixkat/uuidfixer/releases/tag/v26.2) |
| 26.3 | [26.3](https://github.com/komixkat/uuidfixer/releases/tag/v26.3) |

## How it works

- `UUIDUtilMixin` intercepts `UUIDUtil.createOfflinePlayerUUID` and
  substitutes the player's real UUID when one exists.
- `UuidLookupService` queries `api.mojang.com` with a 2 second timeout.
  Verified results are cached for 24 hours, misses for 1 hour.
- A temporary API failure (timeout, network error, 5xx, unusable response)
  reuses the last verified UUID instead of assigning an offline UUID. That
  also applies after a normal cache expiry, so a short outage cannot create
  a duplicate player data file or an empty inventory.
- A `404`/`204` preserves a still-valid verified UUID, since Mojang can
  return those while rate limiting. Once a verified entry has expired, a
  definite `404` clears it and the offline UUID is used again, so a name
  that changes hands is not pinned to an old UUID forever.
- `UuidCache` stores results in `config/uuidfixer/cache.json` and writes the
  file on every cache change, so verified UUIDs survive crashes and forced
  stops.
- Simultaneous logins for the same name are serialized on a fixed set of
  locks, and usernames are URL encoded before the API request is built.
- A dedicated server running with `online-mode=true` logs a warning at
  startup: the vanilla login flow never calls offline UUID resolution, so
  the mod has no effect in that mode.

## Admin commands

All commands are server-side and require permission level 3.

| Command | Effect |
|---------|--------|
| `/uuidfixer status` | Cache summary (total, manual, negative entries) |
| `/uuidfixer status <name>` | Current binding and cache state for a player |
| `/uuidfixer check <name>` | Fresh API query; leaves the cache alone on error or `404`, never overwrites a manual override |
| `/uuidfixer set <name> <uuid>` | Force a manual UUID override, saved to disk and never expired |
| `/uuidfixer clear <name>` | Remove a cache entry, including from disk |
| `/uuidfixer reload` | Reload `config/uuidfixer/cache.json` from disk |
| `/uuidfixer playerdata list [name-or-uuid]` | List player data files, optionally filtered |
| `/uuidfixer playerdata delete <uuid> confirm` | Delete stale `.dat`/`.json` files after a confirmation prompt |

To repair a premium player who got an offline UUID, use `/uuidfixer check <name>`
to confirm the Mojang UUID, `/uuidfixer set <name> <uuid>` to pin it,
then remove the stale offline player data file if necessary. Back up the
world before deleting player data.

## Building

Requires JDK 25 (or whatever `java_version` is set to in `gradle.properties`).

```
./gradlew build
```

Output: `build/libs/uuidfixer-<version>.jar`

## Releasing

```
git tag v1.0.0
git push origin v1.0.0
```

`release.yml` builds the jar and attaches it to a GitHub Release
automatically. Modrinth can link to `.../releases/latest`, which always
points at the newest tag.

## Updating for a new Minecraft version

`gradle.properties` holds every version pin. `check-mc-update.yml` runs
daily and automatically updates to new stable releases of Minecraft, Fabric
Loader, and Fabric API. When the build succeeds, it:
1. Updates `gradle.properties` with new versions
2. Updates `README.md` with the new version in the Supported Versions table
3. Commits changes, creates a `v<version>` tag, and pushes both to main
4. The tag triggers `release.yml` to build and publish a GitHub Release

`loom_version` and the Gradle wrapper version (in
`gradle/wrapper/gradle-wrapper.properties`) are not auto-updated. Loom's
compatible Gradle version changes between releases and can't be verified
without an actual build, so bump these by hand:

1. Check the current stable Loom release:
   `https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml`
2. Update `loom_version` in `gradle.properties`.
3. Update the Gradle version in `gradle/wrapper/gradle-wrapper.properties`
   to whatever that Loom release requires (check its release notes).
4. Run `./gradlew build` locally and fix anything that breaks before pushing.

If the automated build fails, the workflow opens a pull request for manual review.

## License

MIT
