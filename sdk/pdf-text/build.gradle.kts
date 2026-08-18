plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "Reads a PDF's own text layer instead of running OCR over it."

// The digital path: a PDF's own text layer, ~1000x faster than OCR and exact.
// Optional and deliberately NOT part of the Fat AAR -- whoever OCRs photographs should
// not pay several MB of PDFBox-Android for a path they never take. (§6.2, §8.2)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        androidMain.dependencies {
            implementation(libs.pdfbox.android)
        }
        jvmMain.dependencies {
            implementation(libs.pdfbox)
        }
    }
}
