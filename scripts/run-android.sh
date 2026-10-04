#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties")}"
cd "$ROOT"
./gradlew :androidApp:installDebug
"$SDK/platform-tools/adb" shell am start -n dev.alphavideo.demo/.MainActivity
