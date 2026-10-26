#!/usr/bin/env bash
# Vendors this kit into an app project as a Gradle module, so the app builds on its own (CI, fresh clones).
#
#   /workspace/shared/oldschool-theme/tools/sync-into.sh <project-dir> [module-dir-name]
#
# Copies the kit to <project-dir>/<module-dir-name> (default: oldschool-theme), replacing the previous copy, and
# writes a .kit-version stamp. It never touches the app module. One-time wiring (see README):
#   settings.gradle.kts:  include(":oldschool-theme")
#   build.gradle.kts:     id("com.android.library") version "<same AGP as the app>" apply false
#   app/build.gradle.kts: implementation(project(":oldschool-theme"))
set -euo pipefail
KIT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_PROJECT="${1:?usage: sync-into.sh <project-dir> [module-dir-name]}"
NAME="${2:-oldschool-theme}"
[ -d "$DEST_PROJECT" ] || { echo "No such project: $DEST_PROJECT" >&2; exit 1; }
DEST="$(cd "$DEST_PROJECT" && pwd)/$NAME"
case "$DEST" in "$KIT"|"$KIT"/*) echo "Refusing to sync the kit into itself" >&2; exit 1;; esac
VER="$(cat "$KIT/VERSION")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Everything needed to build (and regenerate art); no build outputs.
tar -C "$KIT" --exclude=build --exclude=.gradle --exclude='*.iml' --exclude=.kit-version -cf - . | tar -C "$TMP" -xf -
echo "$VER" > "$TMP/.kit-version"
rm -rf "$DEST"; mkdir -p "$DEST"; cp -a "$TMP/." "$DEST/"
echo "Old School kit v$VER -> $DEST"
