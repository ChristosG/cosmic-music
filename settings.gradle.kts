pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Cosmic"

// Modules implemented in slice 1 (foundation: scan + list + play).
// Additional modules (downloads, extractor, lyrics, shuffle, more features)
// will be added in later iterations as their implementations land.
include(":app")
include(":core:common")
include(":core:db")
include(":core:metadata")
include(":core:player")
include(":core:download")
include(":core:extractor")
include(":core:lyrics")
include(":core:prefs")
include(":feature:library")
include(":feature:nowplaying")
include(":feature:download")
include(":feature:settings")
include(":feature:search")
include(":feature:common")
include(":feature:playlists")
include(":core:shuffle")
