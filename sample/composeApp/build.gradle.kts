import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// The shop's plan and baselines live in sample/shared, where the SwiftUI sample reads them too.
val shared = rootProject.layout.projectDirectory.dir("sample/shared")

/** The shared tracking plan as `SAMPLE_PLAN`, for the Developer screen's "Load the shop's plan". */
val generateSamplePlan = tasks.register("generateSamplePlan") {
    val plan = shared.file("tracking-plan.json")
    val out = layout.buildDirectory.dir("generated/samplePlan")
    inputs.file(plan)
    outputs.dir(out)
    doLast {
        val json = plan.asFile.readText().replace("$", "\${'$'}")
        out.get().file("SamplePlan.kt").asFile.writeText(
            "package io.github.asanre.verivent.sample\n\nval SAMPLE_PLAN: String = \"\"\"$json\"\"\"\n",
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.get().kotlin.srcDir(generateSamplePlan)
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(projects.veriventUi)
            implementation(projects.veriventKtor)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.androidx.test.runner)
            // Compose's ui-test pulls Espresso 3.5, which can't inject input on recent Android releases.
            implementation(libs.androidx.test.espresso.core)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// `recordBaselines` runs the device tests recording, rather than checking, the analytics baselines.
val recordingBaselines = gradle.startParameter.taskNames.any { it.substringAfterLast(':') == "recordBaselines" }

android {
    namespace = "io.github.asanre.verivent.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "io.github.asanre.verivent.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["recordBaselines"] = recordingBaselines.toString()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets.getByName("androidTest").resources.srcDir(shared)
}

// The tests write each recording to their output directory, which Gradle copies off the device.
tasks.register<Copy>("recordBaselines") {
    dependsOn("connectedDebugAndroidTest")
    from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output/debugAndroidTest/connected")) {
        include("**/*.json")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(shared.dir("baselines"))
}
