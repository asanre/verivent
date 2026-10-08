import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// iOS-only bridge: assembles the SnoopKit.xcframework consumed by the Swift package. Not published
// to Maven Central and has no no-op mirror — it is only an input to the XCFramework.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    val xcf = XCFramework("SnoopKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SnoopKit"
            xcf.add(this)
        }
    }

    sourceSets {
        iosMain.dependencies {
            // Deliberately `implementation`, never `api`/`export`: keeping snoop out of the
            // exported ObjC surface is what leaves the Compose UI unreachable, so Kotlin/Native's
            // dead-code elimination can strip it from the release binary.
            implementation(projects.snoop)
        }
    }
}
