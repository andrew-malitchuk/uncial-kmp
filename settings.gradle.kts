rootProject.name = "uncial-kmp"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Tesseract4Android is published on JitPack only (§8.1 — the whole reason the
        // Fat AAR exists). Scoped to one group so JitPack never resolves anything else.
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
            content { includeGroupAndSubgroups("com.github.adaptech-cz") }
        }
    }
}

// ── Contract core (all targets) ──
include(":sdk:model")
include(":sdk:core")
include(":sdk:structure")

// ── Platform implementations ──
include(":sdk:raster")
include(":sdk:engine-tesseract")
include(":sdk:engine-vision")
include(":sdk:engine-fake")
include(":sdk:pdf-text")

// ── Runtime: the single consumer entry point ──
include(":sdk:runtime")

// ── Language data ──
include(":sdk:lang-ukr")
include(":sdk:lang-eng")
include(":sdk:lang-download")

// ── Distribution ──
// The iOS umbrella: one framework containing everything an iOS consumer needs, because
// Obj-C/Swift has no equivalent of a multi-module Gradle graph. Phase 5 turns this into an
// XCFramework plus an SPM package; for now the iOS sample embeds it directly.
include(":dist:ios-framework")

// ── Samples: consume project(...) during phases 0-2, published artifacts from phase 6 ──
include(":samples:cli")
include(":samples:android-app")
