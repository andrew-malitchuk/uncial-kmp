plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.publish")
}

description = "Downloads Tesseract language data at runtime instead of bundling it."

// Fetches .traineddata at runtime instead of shipping it in the artifact, for consumers who
// cannot afford 3.8-4.1 MB per language in their download size. (PLAN.md §6.4)
//
// Android + JVM only, like the lang-* artifacts: iOS needs no language data at all because
// Vision ships its models with the OS.
//
// No HTTP library: this uses the platform's own client (HttpURLConnection on both targets),
// because a networking dependency inside an SDK is a version conflict waiting to happen and
// this module makes exactly one kind of request.
// Android and the JVM share the whole download implementation -- HttpURLConnection,
// java.io.File and MessageDigest are all present on both, and only the default cache
// directory differs. That sharing is worth an intermediate source set: the shared code is
// the code that verifies checksums, and two copies of it would eventually disagree.
//
// Declared through the hierarchy template rather than with manual dependsOn() calls, which
// conflict with the default template and produce a warning on every build.
@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                // Matched by target name so this does not depend on which Android plugin
                // variant is in use.
                withCompilations { compilation ->
                    compilation.target.name == "jvm" || compilation.target.name == "android"
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.sdk.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
