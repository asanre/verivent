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

rootProject.name = "snoop"

include(":snoop-core")
include(":snoop-ui")
include(":snoop-ktor")
include(":snoop-analytics")
include(":snoop-native")
include(":snoop-core-no-op")
include(":snoop-ui-no-op")
include(":snoop-ktor-no-op")
include(":snoop-analytics-no-op")
include(":snoop-parity-check:real")
include(":snoop-parity-check:noop")
include(":sample:composeApp")
