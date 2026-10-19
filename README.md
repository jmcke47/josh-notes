# Josh Notes (Android)

Kotlin / Views + Material 3, minSdk 26, target/compile SDK 37. Package `com.josh.notes`.

* Notes stored as JSON in the app's private storage (`files/notes.json`); auto-save on leaving the editor.
* Home-screen widget "Josh Notes" (resizable, scrollable list via RemoteViewsService). Tap a note to open it, "+" for a new note. Refreshes on every save/delete.
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
