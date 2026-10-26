#!/usr/bin/env bash
# Josh Notes release script
#
#   ./release.sh [patch|minor|major|X.Y.Z|current] ["release notes"]
#   ("current" rebuilds the current version without bumping)
#
# Bumps versionCode (+1) and versionName, runs assembleRelease (signed with the
# persistent keystore in ./keystore), and writes:
#   release/v<version>/notes-app.apk   + update.json
#   release/latest/notes-app.apk       + update.json   (upload these two files)
#
# Upload BOTH files from release/latest/ to your host so that
#   UPDATE_MANIFEST_URL  -> update.json
#   UPDATE_BASE_URL/notes-app.apk -> the APK
# (see update.properties). Set APK_URL=... env var to override apkUrl entirely.
set -euo pipefail
cd "$(dirname "$0")"

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"

# Old School theme kit: refresh the vendored copy from the shared kit when it's on this machine
# (the vendored ./oldschool-theme is committed, so fresh clones / CI build without the shared folder).
KIT_SRC="${OLDSCHOOL_KIT:-/workspace/shared/oldschool-theme}"
if [[ -x "$KIT_SRC/tools/sync-into.sh" ]]; then
  "$KIT_SRC/tools/sync-into.sh" "$PWD" oldschool-theme
elif [[ ! -f oldschool-theme/VERSION ]]; then
  echo "ERROR: oldschool-theme/ (Old School kit) is missing and $KIT_SRC isn't available." >&2; exit 1
fi

BUMP="${1:-patch}"
NOTES="${2:-Bug fixes and improvements.}"

if [[ ! -f keystore/release.jks || ! -f keystore/keystore.properties ]]; then
  echo "ERROR: keystore/release.jks missing. Restore it from /home/box/backups/notes-app-keystore/." >&2
  echo "       NEVER generate a new one: updates would fail to install over the old app." >&2
  exit 1
fi

prop() { grep -E "^$1=" "$2" | head -1 | cut -d= -f2-; }
OLD_CODE=$(prop VERSION_CODE version.properties)
OLD_NAME=$(prop VERSION_NAME version.properties)
IFS=. read -r MA MI PA <<<"$OLD_NAME"
case "$BUMP" in
  patch) NEW_NAME="$MA.$MI.$((PA+1))" ;;
  minor) NEW_NAME="$MA.$((MI+1)).0" ;;
  major) NEW_NAME="$((MA+1)).0.0" ;;
  [0-9]*.[0-9]*.[0-9]*) NEW_NAME="$BUMP" ;;
  current) NEW_NAME="$OLD_NAME" ;;
  *) echo "Unknown bump '$BUMP' (use patch|minor|major|X.Y.Z)" >&2; exit 1 ;;
esac
NEW_CODE=$((OLD_CODE+1))
if [[ "$BUMP" == current ]]; then NEW_NAME="$OLD_NAME"; NEW_CODE="$OLD_CODE"; fi

cp version.properties version.properties.bak
printf "VERSION_CODE=%s\nVERSION_NAME=%s\n" "$NEW_CODE" "$NEW_NAME" > version.properties
trap 'if [[ -f version.properties.bak ]]; then mv version.properties.bak version.properties; echo "Build failed; version reverted to $OLD_NAME ($OLD_CODE)" >&2; fi' ERR

echo "==> Building v$NEW_NAME (versionCode $NEW_CODE)"
./gradlew --no-daemon -q assembleRelease

APK=app/build/outputs/apk/release/app-release.apk
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
"$BUILD_TOOLS/apksigner" verify "$APK"

BASE_URL=$(prop UPDATE_BASE_URL update.properties)
APK_URL="${APK_URL:-${BASE_URL%/}/notes-app.apk}"

OUT="release/v$NEW_NAME"
mkdir -p "$OUT" release/latest
cp "$APK" "$OUT/notes-app.apk"
python3 - "$NEW_CODE" "$NEW_NAME" "$APK_URL" "$NOTES" > "$OUT/update.json" <<'PY'
import json, sys
code, name, url, notes = sys.argv[1:5]
print(json.dumps({"versionCode": int(code), "versionName": name, "apkUrl": url, "notes": notes}, indent=2))
PY
cp "$OUT/notes-app.apk" "$OUT/update.json" release/latest/
sha256sum "$OUT/notes-app.apk" | cut -d' ' -f1 > "$OUT/notes-app.apk.sha256"
rm -f version.properties.bak
trap - ERR

echo "==> Done: v$NEW_NAME (code $NEW_CODE)"
echo "    $OUT/notes-app.apk"
echo "    $OUT/update.json"
echo "    Upload release/latest/{notes-app.apk,update.json} to your update host."
