# UUID Fixer

Assigns Mojang UUIDs to players joining an offline-mode server, allowing
in-game entities and other services (mods like Essential) to use the actual
UUID instead of a hash of the player's username.

Server-side only.

## Download

Latest build: https://github.com/komixkat/uuidfixer/releases/latest

## How it works

- `UUIDUtilMixin` intercepts `UUIDUtil.createOfflinePlayerUUID` and
  substitutes the player's real UUID when one exists.
- `UuidLookupService` queries `api.mojang.com` with a 2 second timeout. A
  failed or slow lookup falls back to the vanilla offline UUID.
- `UuidCache` stores results in `config/uuidfixer/cache.json` so repeat
  joins skip the API call.

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
daily and opens a pull request when Minecraft, Fabric Loader, or Fabric API
have a new stable release.

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

## License

MIT
