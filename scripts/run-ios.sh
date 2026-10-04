#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
[[ "${1:---simulator}" == --simulator ]] || { echo "Use --simulator" >&2; exit 1; }
mkdir -p "$ROOT/iosApp/Generated"
FINGERPRINT="$ROOT/iosApp/Generated/KotlinFrameworkFingerprint.swift"
if [[ ! -f "$FINGERPRINT" ]]; then
  echo '// Populated by the Kotlin framework build phase.' > "$FINGERPRINT"
fi
xcodegen generate --spec "$ROOT/iosApp/project.yml"
DESTINATION=(--simulator-name "${ALPHAVIDEO_SIMULATOR_NAME:-iPhone 17}")
if [[ -n "${ALPHAVIDEO_SIMULATOR_ID:-}" ]]; then
  DESTINATION=(--simulator-id "$ALPHAVIDEO_SIMULATOR_ID")
fi
exec xcodebuildmcp simulator build-and-run \
  --project-path "$ROOT/iosApp/AlphaVideoDemo.xcodeproj" \
  --scheme AlphaVideoDemo "${DESTINATION[@]}" \
  --derived-data-path "$ROOT/iosApp/build/DerivedData" --prefer-xcodebuild
