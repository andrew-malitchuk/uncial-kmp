plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "Turns Uncial's line geometry into headings and paragraphs."

// Geometry -> semantics. 100% commonMain, no platform code, no coroutines: a pure
// function that is fully testable and that a caller may never invoke. (§6.1)
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.model)
        }
    }
}
