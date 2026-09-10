# lang-eng

English `.traineddata` for the Tesseract engines, packaged as its own artifact.

## Features

- **No Kotlin API at all.** The module's entire payload is a data file; its committed ABI dump is empty and adding the dependency is the whole integration step.
- **Shipped separately** so that 4.1 MB of English never lands unconditionally inside somebody's AAR.
- Targets: android + jvm. **iOS needs none of this** — Vision ships its models with the OS, and this module has no iOS target.
- `tessdata_fast` — the same bytes `lang-download` fetches, so a bundled model and a downloaded one are identical.

## Core Components

### How the file gets there

The `.traineddata` is **not in git**. The `uncial.language-data` convention plugin registers a `downloadLanguageData` task that fetches `eng.traineddata` from Tesseract's `tessdata_fast` repository and verifies it against the SHA-256 pinned in the module's `build.gradle.kts`. Packaging depends on that task, so a corrupted or substituted model fails the build rather than degrading recognition quietly.

The task's output is wired in twice:

| Target | Wiring | Result |
|---|---|---|
| Android | a **generated** asset source directory | `assets/tessdata/eng.traineddata`, merged into the consumer's APK by AGP |
| JVM | a `jvmMain` resource directory | `tessdata/eng.traineddata` on the classpath, unpacked to a cache directory on first use |

`AndroidTessDataProvider` (the engine's Android default) copies the asset into `filesDir` once, because Tesseract cannot read an asset stream; `ClasspathTessDataProvider` does the equivalent on the JVM.

## Usage

```kotlin
implementation("io.github.andrew-malitchuk:uncial-lang-eng:<version>")
```

Nothing else. To refresh or verify the model locally:

```bash
./gradlew :sdk:lang-eng:downloadLanguageData
```

Bundling is one of four ways to supply language data — see [Language data](../language-data.md) for the system, downloaded and bring-your-own options.

!!! warning "`androidResources { enable = true }` is load-bearing"
    A KMP Android library packages no assets at all without it, so the AAR would ship
    empty — silently. For a module whose entire payload is an asset, that is the whole
    artifact. A static `src/androidMain/assets` directory is not picked up either; the data
    has to arrive as a generated source directory.
