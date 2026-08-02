pluginManagement {
    // `build-logic` is a composite build: its convention plugins are compiled
    // first, then applied by the modules below as if they were published.
    includeBuild("build-logic")
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

// Lets modules depend on each other as `projects.core.data` instead of
// `project(":core:data")` — typo-proof and navigable from the IDE.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "mindlog-todo-native"

include(":app")
include(":core:designsystem")
include(":core:datastore")
include(":core:network")
include(":core:data")
include(":feature:auth")
include(":feature:tasks")

// Not split out yet, on purpose. A module earns its existence when two modules
// consume it or its build configuration genuinely differs:
//   :core:model    — would only be used by :core:data today.
//   :core:database — no Room until offline writes are on the table; the SSE
//                    stream carries invalidation signals, not deltas, so a
//                    local store would not save a single request.
//   :core:ui       — shared composables live in :core:designsystem until they
//                    start depending on domain models.
