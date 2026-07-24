import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compiles ../src against the NO-OP mirrors; :snoop-parity-check:real compiles the same source
// against the real modules. Either compilation failing in `check` means the APIs drifted.
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
                implementation(projects.snoopCoreNoOp)
                implementation(projects.snoopKtorNoOp)
                implementation(projects.snoopAnalyticsNoOp)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(compose.runtime)
                implementation(compose.ui)
            }
        }
    }
}

android {
    namespace = "com.snoop.paritycheck.noop"
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
