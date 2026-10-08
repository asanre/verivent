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
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "snoop-root"

include(":snoop")
include(":snoop-ui")
include(":snoop-ktor")
include(":snoop-native")
include(":snoop-no-op")
include(":snoop-ui-no-op")
include(":snoop-ktor-no-op")
include(":snoop-parity-check:real")
include(":snoop-parity-check:noop")
include(":sample:composeApp")
