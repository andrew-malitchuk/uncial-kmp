plugins {
    id("uncial.kmp.base")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "Apple Vision recognition backend for Uncial on iOS."

// Vision and PDFKit are system frameworks, so this module has zero third-party
// dependencies and iOS needs no language data at all. (§6.2, §6.4)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        commonTest.dependencies {
            // The iOS integration test drives the real recognizer, which is a suspend API.
            implementation(libs.kotlinx.coroutines.test)
            // Test-only: the integration test rasterizes a real PDF with PDFKit before
            // handing it to Vision. Production code here still depends on :sdk:core alone.
            implementation(projects.sdk.raster)
        }
    }
}
