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
        // Tesseract4Android (OCR на устройстве) публикуется только на JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("cz\\.adaptech.*") }
        }
    }
}

rootProject.name = "expenses-android"
include(":app")
include(":core:domain")
