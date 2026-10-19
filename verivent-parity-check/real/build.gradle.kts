import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compiles ../src (Android) and ../src-ios against the REAL modules; :verivent-parity-check:noop
// compiles the same sources against the no-op mirrors. Either compilation failing in `check` means
// the APIs drifted. One simulator target is enough for the iOS half — the source is arch-agnostic.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosSimulatorArm64()

    sourceSets {
        iosMain {
            kotlin.srcDir("../src-ios")
            dependencies {
                implementation(projects.veriventUi)
            }
        }
        androidMain {
            kotlin.srcDir("../src")
            dependencies {
                implementation(projects.verivent)
                implementation(projects.veriventUi)
                implementation(projects.veriventKtor)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.runtime)
                implementation(compose.ui)
            }
        }
    }
}

android {
    namespace = "io.github.asanre.verivent.paritycheck.real"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("check") { dependsOn("compileDebugKotlinAndroid", "compileKotlinIosSimulatorArm64") }
