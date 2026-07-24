import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compiles ../src against the REAL modules; :snoop-parity-check:noop compiles the same source
// against the no-op mirrors. Either compilation failing in `check` means the APIs drifted.
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

    sourceSets {
        androidMain {
            kotlin.srcDir("../src")
            dependencies {
                implementation(projects.snoopCore)
                implementation(projects.snoopKtor)
                implementation(projects.snoopAnalytics)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.runtime)
                implementation(compose.ui)
            }
        }
    }
}

android {
    namespace = "com.snoop.paritycheck.real"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("check") { dependsOn("compileDebugKotlinAndroid") }
