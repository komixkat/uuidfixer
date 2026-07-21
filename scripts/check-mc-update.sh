#!/usr/bin/env bash
# Checks for newer stable Minecraft, Fabric Loader, and Fabric API versions
# and updates gradle.properties if any changed. Never touches loom_version
# or the Gradle wrapper - those require a human to verify a build actually
# works, since Loom's compatibility with a given Gradle version isn't
# something this script can safely detect.

set -uo pipefail

PROPS_FILE="gradle.properties"
USER_AGENT="komixkat/uuidfixer-updater (https://github.com/komixkat/uuidfixer)"
CURL=(curl -sf -H "User-Agent: ${USER_AGENT}")

finish_no_update() {
	echo "$1"
	echo "updated=false" >> "$GITHUB_OUTPUT"
	exit 0
}

fetch_json() {
	local url="$1" filter="$2" body
	if ! body=$("${CURL[@]}" "$url"); then
		echo "  request failed: $url" >&2
		return 0
	fi
	jq -r "$filter" <<< "$body" 2>/dev/null
}

current_mc=$(grep '^minecraft_version=' "$PROPS_FILE" | cut -d= -f2)
current_loader=$(grep '^loader_version=' "$PROPS_FILE" | cut -d= -f2)
current_fabric_api=$(grep '^fabric_api_version=' "$PROPS_FILE" | cut -d= -f2)
echo "Currently pinned: mc=$current_mc loader=$current_loader fabric_api=$current_fabric_api"

latest_mc=$(fetch_json "https://meta.fabricmc.net/v2/versions/game" \
	'[.[] | select(.stable == true)][0].version')
[ -z "$latest_mc" ] || [ "$latest_mc" == "null" ] && finish_no_update "Could not reach Minecraft version metadata, skipping this run."

latest_loader=$(fetch_json "https://meta.fabricmc.net/v2/versions/loader" \
	'[.[] | select(.stable == true)][0].version')

target_mc="$latest_mc"
latest_fabric_api=$(fetch_json \
	"https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${target_mc}%22%5D&loaders=%5B%22fabric%22%5D" \
	'.[0].version_number')

if [ "$latest_mc" != "$current_mc" ] && { [ -z "$latest_fabric_api" ] || [ "$latest_fabric_api" == "null" ]; }; then
	echo "Minecraft $latest_mc exists but Fabric API isn't published for it yet, staying on $current_mc."
	target_mc="$current_mc"
	latest_fabric_api=$(fetch_json \
		"https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${current_mc}%22%5D&loaders=%5B%22fabric%22%5D" \
		'.[0].version_number')
fi

if [ -z "$latest_loader" ] || [ -z "$latest_fabric_api" ] || [ "$latest_fabric_api" == "null" ]; then
	finish_no_update "Could not resolve one or more versions cleanly, skipping this run."
fi

if [ "$target_mc" == "$current_mc" ] \
	&& [ "$latest_loader" == "$current_loader" ] \
	&& [ "$latest_fabric_api" == "$current_fabric_api" ]; then
	finish_no_update "Already up to date."
fi

echo "Bumping to: mc=$target_mc loader=$latest_loader fabric_api=$latest_fabric_api"

sed -i \
	-e "s/^minecraft_version=.*/minecraft_version=${target_mc}/" \
	-e "s/^loader_version=.*/loader_version=${latest_loader}/" \
	-e "s/^fabric_api_version=.*/fabric_api_version=${latest_fabric_api}/" \
	"$PROPS_FILE"

{
	echo "updated=true"
	echo "new_mc_version=$target_mc"
	echo "new_loader_version=$latest_loader"
	echo "new_fabric_api_version=$latest_fabric_api"
} >> "$GITHUB_OUTPUT"
