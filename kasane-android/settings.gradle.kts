pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here so a core-only build never resolves the Android plugin.
    plugins {
        id("com.android.application") version "8.10.1"
        id("org.jetbrains.kotlin.android") version "1.9.22"
        id("org.jetbrains.kotlin.jvm") version "1.9.22"
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Kasane"
include(":core")
// The Android app needs the Android SDK. Leave it out with -PcoreOnly to
// build and test the pure-Kotlin core on a machine without one.
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
