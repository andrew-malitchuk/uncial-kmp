plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "The Uncial OCR client: the single entry point a consumer calls."

// What the consumer actually calls.
//
// The dependency list below is the module's most important property: there is NO
// dependency on any engine module. Engines are reached only through OcrEngineFactory, so
// runtime does not know their class names and cannot be made to depend on Tesseract or
// Vision by accident. (§6.3, §6.6)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.model)
            api(projects.sdk.core)
            implementation(projects.sdk.raster)
        }
        commonTest.dependencies {
            implementation(projects.sdk.engineFake)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
