plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dokka)
}

// Aggregated API reference: `./gradlew dokkaGenerate` renders the public modules into
// build/dokka/html, which the docs site links to under /api.
dependencies {
    dokka(projects.verivent)
    dokka(projects.veriventUi)
    dokka(projects.veriventKtor)
}

dokka {
    moduleName.set("Snoop API")
}
