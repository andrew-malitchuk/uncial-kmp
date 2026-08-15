plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "PDF page rasterization for Uncial on Android, iOS and the JVM."

// PDF page -> raster. Android uses the framework's PdfRenderer and iOS uses PDFKit, so
// only the JVM needs a third-party dependency. (§6.2)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        jvmMain.dependencies {
            implementation(libs.pdfbox)
        }
        jvmTest.dependencies {
            // PDFBox again, this time to GENERATE the PDFs the rasterizer is fed: a test
            // that shipped a binary fixture could not vary rotation or the crop box.
            implementation(libs.pdfbox)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
