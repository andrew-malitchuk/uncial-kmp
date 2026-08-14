plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "Engine contracts for Uncial: rasterizers, text recognizers and the engine registry."

// Engine contracts. A consumer only looks here when writing their own engine. (§6.1)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.model)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // The Android Context every platform module needs arrives here, once, so that
            // engine-tesseract and pdf-text do not each ship their own initializer.
            // `api` because it contributes a ContentProvider to the merged manifest and so
            // needs the consumer's build system to participate. (PLAN.md §5.5, §8.2)
            api(libs.androidx.startup)
            implementation(libs.androidx.annotation)
        }
    }
}
