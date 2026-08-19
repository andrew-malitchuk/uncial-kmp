plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "A deterministic fake OCR engine for testing code that calls Uncial."

// A deterministic engine for consumer unit tests. Published, because it is part of the
// SDK's DX: without it, testing code that calls Uncial means running Tesseract. (§6.2)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        commonTest.dependencies {
            // The engine is a suspend API, so its own tests need runTest.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
