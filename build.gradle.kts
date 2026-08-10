// Uncial is a library-only build: the root project carries no code, and the one plugin it
// does apply is `uncial.publish.aggregation` -- publishing is the single concern that is
// genuinely root-scoped, because the twelve published modules have to arrive at the Central
// Portal as ONE deployment rather than twelve. (PUBLISHING.md §A1)
//
// They are DECLARED here with `apply false` so that every plugin resolves in one
// classloader scope. Without this, a module that applies the Kotlin plugin directly (the
// samples) and modules that apply it through build-logic convention plugins (the SDK) load
// two copies of the Kotlin Gradle Plugin, and Gradle refuses to share KGP's build services
// between them.
plugins {
    id("uncial.publish.aggregation")

    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
}

