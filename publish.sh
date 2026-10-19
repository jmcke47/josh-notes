#!/usr/bin/env bash
# One-command publish to GitHub Releases (jmcke47/josh-notes).
#
#   ./publish.sh [patch|minor|major|X.Y.Z|current] ["release notes"]
#
# Requires the GitHub CLI to be authenticated: `gh auth login` (or GH_TOKEN env
# var with a token that has `repo` scope / Contents: read+write).
# Steps: build via release.sh -> commit version bump -> push source ->
#        create GitHub release v<ver> with notes-app.apk + update.json ->
#        verify the releases/latest/download URLs resolve.
set -euo pipefail
cd "$(dirname "$0")"
REPO="${REPO:-jmcke47/josh-notes}"
BUMP="${1:-patch}"; NOTES="${2:-Bug fixes and improvements.}"

gh auth status >/dev/null 2>&1 || { echo "ERROR: gh is not authenticated. Run 'gh auth login' or set GH_TOKEN." >&2; exit 1; }

# Safety: never publish signing secrets.
if git ls-files | grep -qE '(^|/)keystore/|\.jks$|keystore\.properties'; then
  echo "ERROR: keystore files are tracked by git — refusing to push." >&2; exit 1
fi

./release.sh "$BUMP" "$NOTES"
VER=$(grep '^VERSION_NAME=' version.properties | cut -d= -f2)
TAG="v$VER"

# Create the repo on first publish.
if ! gh repo view "$REPO" >/dev/null 2>&1; then
  gh repo create "$REPO" --public --description "Josh Notes Android app" >/dev/null
fi
git remote get-url origin >/dev/null 2>&1 || git remote add origin "https://github.com/$REPO.git"
gh auth setup-git >/dev/null 2>&1 || true

git config user.name >/dev/null 2>&1 || git config user.name "jmcke47"
git config user.email >/dev/null 2>&1 || git config user.email "44462275+jmcke47@users.noreply.github.com"
git add -A
git diff --cached --quiet || git commit -qm "Release $TAG"
git branch -M main
git push -u origin main

if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  gh release upload "$TAG" release/latest/notes-app.apk release/latest/update.json --repo "$REPO" --clobber
else
  gh release create "$TAG" release/latest/notes-app.apk release/latest/update.json \
    --repo "$REPO" --title "Josh Notes $TAG" --notes "$NOTES" --target main --latest
fi

echo "==> Verifying public download URLs"
BASE="https://github.com/$REPO/releases/latest/download"
for f in update.json notes-app.apk; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -L "$BASE/$f")
  echo "    $BASE/$f -> HTTP $code"
done
CODE=$(grep '^VERSION_CODE=' version.properties | cut -d= -f2)
for i in 1 2 3 4 5 6; do   # GitHub's CDN can serve the previous file for a few seconds
  J=$(curl -sL "$BASE/update.json?nocache=$(date +%s)")
  echo "$J" | grep -q "\"versionCode\": *$CODE\b" && break
  sleep 5
done
echo "$J"
echo "==> Published $TAG: https://github.com/$REPO/releases/tag/$TAG"
