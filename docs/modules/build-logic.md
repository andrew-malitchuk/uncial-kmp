# build-logic

The included build holding Uncial's convention plugins. Not published.

## Features

- **One target set, defined once**: android + jvm + iosArm64 + iosSimulatorArm64 + iosX64, composed per module from three plugins.
- **Strict public API everywhere**: `explicitApi()` plus Kotlin's built-in ABI validation on every module.
- **Artifact naming**: `base.archivesName` is `uncial-<module>`, so Gradle paths stay readable as `:sdk:model` while the AAR/JAR and the klib unique name match the Maven coordinates.
- Versions (JDK 21 toolchain, JVM target 11, `compileSdk` 36, `minSdk` 24) come from the version catalog, never from a module.

## Layout

The plugins live in a `:convention` subproject as ordinary `Plugin<Project>` classes — the layout the sibling SDKs (`axiom-sdk`, `bitshift-kmp`) use — rather than as precompiled script plugins:

```
build-logic/
├── settings.gradle.kts
└── convention/
    ├── build.gradle.kts                   # the plugin classpath and the id → class registry
    ├── README.md
    └── src/main/kotlin/uncial/convention/
        ├── core/                          # internal: identity, catalog, naming, dsl, language, publish
        └── source/                        # the surface: base class, plugins, the languageData { } DSL
```

Same rule as every other module — `core/` is what is `internal` and is never applied, `source/` is what a module's build script reaches for — down to **one file per artifact**, named after it (`versionOf` → `core/catalog/VersionOf.kt`). `build-logic/convention/README.md` is the reference for the package layout and for adding a plugin.

Every plugin extends `BaseConventionPlugin`, whose `apply` is `final` and runs a fixed seven-step order — `configurePlugins` → `configureExtensions` → `configureKotlin` → `configureTargets` → `configureSourceSets` → `configureTasks` → `configureArtifacts`. A subclass overrides only the steps it has something to say about. The order is load-bearing: a plugin id has to be applied before the extension it registers can be configured, and a task has to be registered before it can be wired into an artifact.

Plugin ids did not change with the move, and they are API: every module's `plugins { }` block names them as strings, so renaming one is a repository-wide breaking change.

## Core Components

### `uncial.kmp.base`

Applied by every module. Sets the JVM toolchain, `explicitApi()`, `abiValidation()`, the `kotlin("test")` dependency for `commonTest`, and `-Xexpect-actual-classes` — `expect class Raster` is deliberate, so the Beta warning is noise.

Because `abiValidation()` is on, `checkKotlinAbi` runs as part of `build`: an API change fails the build until `./gradlew updateKotlinAbi` regenerates `api/*.api` and the diff is reviewed.

### `uncial.target.android`

Applies `com.android.kotlin.multiplatform.library` and configures `kotlin { android { } }`: a namespace derived from the module name, `compileSdk`/`minSdk` from the catalog, host tests, and the JVM target.

It also packages each module's `consumer-rules/*.pro` into its AAR automatically, so a consumer's release build keeps whatever JNI and `androidx.startup` reach reflectively. The file tree is resolved eagerly on purpose — AGP's `consumerKeepRules.file(Any)` unwraps a `Provider` on the spot, so laziness would buy nothing.

### `uncial.target.jvm` and `uncial.target.ios`

A `jvm()` target with the catalog's JVM target, and `iosArm64()` + `iosSimulatorArm64()` + `iosX64()` respectively.

### `uncial.language-data` and `DownloadLanguageDataTask`

Adds a `languageData { language = …; sha256 = … }` extension and a `downloadLanguageData` task that fetches `<lang>.traineddata` from `tessdata_fast` and verifies its SHA-256.

The task is `@CacheableTask` and a pure function of the language code, URL and digest — no file inputs — so a clean CI checkout restores models from the build cache instead of pulling several MB per language. It re-verifies rather than checking mere existence, because Gradle does not clear an `@OutputDirectory` before re-running: bumping the expected checksum would otherwise find the stale file and package it unverified.

Its output is wired into the Android variant as a **generated** asset directory and into `jvmMain`'s resources, which is what makes Gradle order the download before packaging.

### `uncial.publish` and `uncial.publish.aggregation`

`uncial.publish` makes one module publishable: coordinates (`uncial-<module>`), the shared POM with the module's own `description`, a real sources jar and an empty javadoc jar, GPG signing from `configure/signing/secrets.properties`, and the nmcp handshake that exposes its publications to the aggregation.

`uncial.publish.aggregation` is applied to the **root project only** and holds the list of published modules — collecting all twelve into one Central Portal deployment instead of twelve. A module is published by applying `uncial.publish`, giving it a `description`, and adding a line to that list; a module missing from the list builds signed artifacts locally and never reaches the Portal. See `PUBLISHING.md`.

## Usage

```kotlin
plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
}
```

Drop `uncial.target.ios` for an Android/JVM-only module such as `engine-tesseract`, and keep only `uncial.target.ios` for an iOS-only one such as `engine-vision`.

!!! warning "Every plugin must be declared in the root `build.gradle.kts` with `apply false`"
    Otherwise a module applying Kotlin directly and modules applying it through `build-logic`
    load two copies of the Kotlin Gradle plugin, and Gradle refuses to share its build
    services.

!!! warning "AGP 9 compiles Kotlin itself"
    Applying `org.jetbrains.kotlin.android` is rejected. KMP library modules use
    `com.android.kotlin.multiplatform.library` with their configuration inside
    `kotlin { android { } }` — no flavors, no build types, no `BuildConfig`.
