plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.language-data")
    id("uncial.publish")
}

description = "English Tesseract language data, packaged as an Uncial artifact."

// English language data for the Tesseract engines, shipped as its own artifact so the
// 7.9 MB of ukr+eng never lands unconditionally inside an AAR. iOS needs none of this:
// Vision ships its languages with the OS. (PLAN.md §6.4)
//
// The .traineddata is not in git — it is downloaded and checksum-verified by
// ./gradlew :sdk:lang-eng:downloadLanguageData, which packaging depends on.
languageData {
    language = "eng"
    sha256 = "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"
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
