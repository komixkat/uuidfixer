#!/usr/bin/env bash
# Updates README.md with the new Minecraft version in the "Supported Versions" section

set -euo pipefail

NEW_VERSION="$1"
README="README.md"

# Create Supported Versions section if it doesn't exist
if ! grep -q "## Supported Versions" "$README"; then
    # Find a good place to insert it (after "## Download" section)
    # We'll insert after the "## Download" section
    sed -i '/^## Download$/,/^##/ { /^## [^D]/i ## Supported Versions\n\nThis mod supports the following Minecraft versions:\n\n| Minecraft Version | Release Date |\n|-------------------|--------------|\n}' "$README"
fi

# Check if version already exists in table
if grep -q "| $NEW_VERSION |" "$README"; then
    echo "Version $NEW_VERSION already in README"
    exit 0
fi

# Get current date for release date
RELEASE_DATE=$(date '+%Y-%m-%d')

# Add new version to the table (insert after header)
sed -i "/^| Minecraft Version | Release Date |$/a | $NEW_VERSION | $RELEASE_DATE |" "$README"

echo "Added $NEW_VERSION to README"
