plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.language-data")
    id("uncial.publish")
}

description = "Ukrainian Tesseract language data, packaged as an Uncial artifact."

// Ukrainian language data for the Tesseract engines, shipped as its own artifact so the
// 7.9 MB of ukr+eng never lands unconditionally inside an AAR. iOS needs none of this:
// Vision ships its languages with the OS. (PLAN.md §6.4)
//
// The .traineddata is not in git — it is downloaded and checksum-verified by
// ./gradlew :sdk:lang-ukr:downloadLanguageData, which packaging depends on.
languageData {
    language = "ukr"
    sha256 = "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b"
}

kotlin {
    android {
        // The KMP Android library plugin ships an AAR with no assets at all unless this is
        // switched on -- which for a module whose entire payload IS an asset means a
        // silently empty artifact.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
    }
}
