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
    }
}

rootProject.name = "BrahmClock"

include(":core")
// `-PcoreOnly` builds just the pure-Kotlin engine (no Android SDK needed).
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
