pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Aster-Android"

include(":app")
include(":core-crypto")
include(":core-api")
include(":core-design")
include(":core-storage")

val is_fdroid_build = startParameter.projectProperties.containsKey("fdroid") ||
    startParameter.taskNames.any { it.contains("fdroid", ignoreCase = true) }

if (!is_fdroid_build) {
    include(":baselineprofile")
}
