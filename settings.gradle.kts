pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JoshNotes"
include(":app")
// Old School theme kit, vendored from /workspace/shared/oldschool-theme by tools/sync-into.sh (see oldschool-theme/README.md).
include(":oldschool-theme")
