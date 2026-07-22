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
include(":snoop-ktor")
include(":snoop-analytics")
include(":snoop-ui")
include(":snoop-web")
include(":snoop-ktor-no-op")
include(":snoop-analytics-no-op")
include(":snoop-ui-no-op")
include(":snoop-web-no-op")
include(":sample:composeApp")
