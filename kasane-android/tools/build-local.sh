#!/usr/bin/env bash
# Builds Kasane on your own computer: free, no GitHub minutes, no Claude.
#   tools/build-local.sh            fast build (no R8), a minute or two after the first run
#   tools/build-local.sh release    the full minified release build
# Needs JDK 17+, the Android SDK (ANDROID_HOME) and Gradle (or ./gradlew), and the shared key:
# download release.jks and password.txt from your password manager and point at them:
#   KASANE_KEYSTORE=/path/release.jks KASANE_KEYSTORE_PASSWORD="$(cat /path/password.txt)" tools/build-local.sh
set -euo pipefail
cd "$(dirname "$0")/.."
: "${KASANE_KEYSTORE:?set KASANE_KEYSTORE to the shared release.jks}"
: "${KASANE_KEYSTORE_PASSWORD:?set KASANE_KEYSTORE_PASSWORD}"
GRADLE=gradle; [ -x ./gradlew ] && GRADLE=./gradlew
if [ "${1:-}" = release ]; then TASK=:app:assembleRelease; OUT=release; else TASK=:app:assemblePersonal; OUT=personal; fi
$GRADLE "$TASK"
APK=$(ls app/build/outputs/apk/$OUT/*.apk | head -1)
echo "Built $APK"
"${ANDROID_HOME:?}"/build-tools/*/apksigner verify --print-certs "$APK" | grep SHA-256
echo "Expected a906ada6970a00355b816a72057c3bc7563b041ab9d5c2f93c1ca5fbc744ed66"
