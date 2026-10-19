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

rootProject.name = "verivent-root"

include(":verivent")
include(":verivent-ui")
include(":verivent-ktor")
include(":verivent-native")
include(":verivent-no-op")
include(":verivent-ui-no-op")
include(":verivent-ktor-no-op")
include(":verivent-parity-check:real")
include(":verivent-parity-check:noop")
include(":sample:composeApp")
