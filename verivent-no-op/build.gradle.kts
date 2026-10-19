import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Binary API dump of each published module, checked against the committed one in `check`, so
    // a release that only adds can't silently remove or change a public signature.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // StateFlow in the mirrored TimelineStore surface; accepted cost of full API parity.
            api(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "io.github.asanre.verivent.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// KGP 2.2 doesn't hook the ABI check into `check` yet (2.3.20 does).
tasks.named("check") { dependsOn("checkLegacyAbi") }
