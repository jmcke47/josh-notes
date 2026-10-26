# Josh Notes (Android)

Kotlin / Views + Material 3, minSdk 26, target/compile SDK 37. Package `com.josh.notes`.

* Notes stored as JSON in the app's private storage (`files/notes.json`); auto-save on leaving the editor.
* Home-screen widget "Josh Notes" (resizable, scrollable list via RemoteViewsService). Tap a note to open it, "+" for a new note. Refreshes on every save/delete.
* v1.1: search, pin, colour labels, checklists, swipe-to-delete + Undo, share/receive text, JSON backup export/import (SAF), sort, archive, offline dictation (Android on-device recognizer or Vosk; ~40 MB model downloaded on first use), volume-key dictation trigger, widget mic button, theme (Light/Dark/System + Material You), Settings screen.
* Notes file format v2 (`{"format":2,"notes":[...]}`); v1 arrays are read transparently and backed up to `files/notes.v1-backup.json` on first launch.
* Self-updater: toolbar ⟳ "Check for updates" → fetches `update.json`, compares versionCode, downloads APK, opens the system installer.

## Build / release
    ./release.sh                 # patch bump (1.0.0 -> 1.0.1), versionCode +1
    ./release.sh minor "Notes"   # or major | X.Y.Z | current
Output: `release/v<ver>/` and `release/latest/` (`notes-app.apk` + `update.json`). Upload both files from `release/latest/` to the host.

## Update hosting
Hosted on GitHub Releases: https://github.com/jmcke47/josh-notes/releases (manifest at releases/latest/download/update.json).
Publish a new version in one command (needs `gh auth login` or GH_TOKEN): `./publish.sh patch "What changed"`.
Installed apps can also be pointed at a new URL in-app: menu → "Update source URL".
`apkUrl` in update.json may be absolute or relative to the manifest URL. Must be HTTPS (release builds block cleartext).

## Signing — IMPORTANT
`keystore/release.jks` + `keystore/keystore.properties` sign every build (debug too). Never regenerate it.
Backup: `/home/box/backups/notes-app-keystore/`. Cert SHA-256: E5:08:1D:F9:...:9E:A5

## Old School theme kit
The Old School look (tokens, font, drawables, dialogs/context menu, XP drop, banners, widget pieces…) lives in the
shared kit `/workspace/shared/oldschool-theme/` (v1.0.0, see its README). It is vendored here as the Gradle module
`oldschool-theme/` (committed, so clones/CI build without the shared folder) and refreshed by `release.sh` via
`tools/sync-into.sh`. Don't edit `oldschool-theme/` directly: change the shared kit, bump its VERSION, re-sync.
Josh Notes adds only `Theme.JoshNotes.OldSchool` (parent `Theme.OldSchool`) in `res/values/themes_os.xml`.
