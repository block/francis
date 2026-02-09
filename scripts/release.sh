#!/bin/bash
set -euo pipefail

# Start at repo root - one level above scripts dir
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

JAR_PATH="tool/release/build/libs/release.jar"

# Build the release tool if needed
if [[ ! -f "$JAR_PATH" ]] || [[ -n "$(find tool/release/src -newer "$JAR_PATH" 2>/dev/null)" ]]; then
    echo "Building release tool..."
    ./gradlew :tool:release:jar -q
fi

# Pass through all arguments
exec java -jar "$JAR_PATH" "$@"
