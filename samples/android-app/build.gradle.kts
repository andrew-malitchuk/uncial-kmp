plugins {
    // AGP 9 compiles Kotlin itself: applying org.jetbrains.kotlin.android is not just
    // unnecessary, it is rejected outright. The Compose compiler plugin is a separate
    // Kotlin compiler plugin and still has to be applied by hand.
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Proves the Android side of the SDK on a real device: PdfRenderer rasterizing,
// Tesseract4Android recognizing, language data arriving from the uncial-lang-* assets, and
// the engine registering itself through androidx.startup with no init code here.
//
// Note what this module does NOT contain: no Uncial initialization, no engine selection,
// no tessdata copying. That is the DX the SDK is supposed to deliver. (PLAN.md §8.4)
android {
    namespace = "io.github.andrewmalitchuk.uncial.samples.android"
    // Newer than the SDK modules: the current Compose BOM demands 37, and a sample
    // can afford that because nothing depends on it.
    compileSdk = libs.versions.android.sampleCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.andrewmalitchuk.uncial.samples.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.sampleCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // R8 on, because §8.8's acceptance criterion is a RELEASE build that works --
            // JNI entry points are invisible to R8 and get stripped without consumer rules.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.sdk.runtime)
    implementation(projects.sdk.structure)
    implementation(projects.sdk.engineTesseract)
    implementation(projects.sdk.pdfText)
    // Language data. Their assets merge into this APK, which is how the engine finds them.
    implementation(projects.sdk.langUkr)
    implementation(projects.sdk.langEng)

    // Declared directly, not left to arrive through activity-compose: `by viewModels()`
    // lives in activity-ktx and AndroidViewModel in lifecycle-viewmodel, and a transitive
    // dependency is not a contract -- activity-compose is free to stop bringing them.
    implementation(libs.androidx.activity.ktx)
    // FileProvider, for handing the camera app somewhere to write. Same reasoning as
    // above: it arrives transitively through activity, and a transitive dependency is not
    // a contract.
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose lives in the SAMPLE, never in the SDK.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.toolingPreview)
    debugImplementation(libs.compose.ui.tooling)

    // Host tests only -- `summarize` is pure, which is why it can be checked without a device.
    // Explicit coordinates: AGP 9 compiles Kotlin itself, so the `kotlin("test")` helper
    // the KMP modules use is not available here.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${libs.versions.kotlin.get()}")
}
