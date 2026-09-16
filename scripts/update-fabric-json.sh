#!/usr/bin/env bash
# Updates minecraft version in fabric.mod.json to match gradle.properties

set -uo pipefail

MC_VERSION=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
FABRIC_JSON="src/main/resources/fabric.mod.json"

python3 -c '
import re, sys
path = "src/main/resources/fabric.mod.json"
with open(path, "r") as f:
    content = f.read()

new_mc = "~" + sys.argv[1]
content = re.sub(r"\"minecraft\":\s*\"[^\"]+\"", f"\"minecraft\": \"{new_mc}\"", content)

with open(path, "w") as f:
    f.write(content)
' "$MC_VERSION"

echo "Updated fabric.mod.json minecraft dependency to ~$MC_VERSION"
