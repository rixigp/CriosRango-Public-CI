pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
        id("org.jetbrains.compose") version "1.9.2"
    }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "CriosRango"
include(":app")
include(":shared")
