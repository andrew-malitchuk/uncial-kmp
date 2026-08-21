plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.publish")
}

description = "Tesseract recognition backend for Uncial on Android and the JVM."

// Tesseract on both Android and the JVM, which is what buys behavioural parity between
// them: same engine, same .traineddata. (§4)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        commonTest.dependencies {
            // The language-data providers are suspending, and the cancellation rules these
            // tests exist to pin are only observable under a real coroutine.
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            // JitPack-only; see settings.gradle.kts and PLAN.md §8.1.
            implementation(libs.tesseract4android)
            // androidx.startup comes transitively from :sdk:core, which owns the
            // Context plumbing for every Android-side module.
        }
        jvmMain.dependencies {
            // Binds to the SYSTEM libtesseract: the JVM artifact is deliberately not
            // self-contained, and isAvailable() has to say so honestly. (§5.9)
            implementation(libs.tess4j)
            implementation(libs.jna)
        }
    }
}
