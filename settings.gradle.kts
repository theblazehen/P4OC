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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PocketCode"
include(":app")

// FluidMarkdown and dependencies (local modules in libs/)
include(":libs:fluid-markdown")
include(":libs:markwon-core")
include(":libs:markwon-ext-tables")
include(":libs:markwon-ext-strikethrough")
include(":libs:markwon-inline-parser")
include(":libs:markwon-html")
