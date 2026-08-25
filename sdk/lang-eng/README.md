# lang-eng

> English `.traineddata` for the Tesseract engines, packaged as its own artifact.

Gradle `:sdk:lang-eng` · Maven `io.github.andrew-malitchuk:uncial-lang-eng` · [full docs](../../docs/modules/lang-eng.md)

## Responsibility

Ships one language model, so that 4.1 MB of English never lands unconditionally inside
somebody's AAR. The module has **no Kotlin API at all** — its committed ABI dump is empty,
and adding the dependency is the whole integration step.

Targets are android + jvm. **iOS needs none of this**: Vision ships its models with the OS,
so this module has no iOS target.

## Layout

No sources. The payload is a data file, produced at build time into
`build/generated/languageData/`.

## Dependencies

`api(:sdk:core)`, for the `LanguageDataProvider` contract the engines read it through.

## How the file gets there

The `.traineddata` is **not in git**. The `uncial.language-data` convention plugin registers a
`downloadLanguageData` task that fetches `eng.traineddata` from Tesseract's `tessdata_fast`
repository and verifies it against the SHA-256 pinned in this module's `build.gradle.kts`.
Packaging depends on that task, so a corrupted or substituted model fails the build rather
than degrading recognition quietly.

The output is wired in twice:

| Target | Wiring | Result |
|---|---|---|
| Android | a **generated** asset source directory | `assets/tessdata/eng.traineddata`, merged into the consumer's APK by AGP |
| JVM | a `jvmMain` resource directory | `tessdata/eng.traineddata` on the classpath, unpacked to a cache directory on first use |

`AndroidTessDataProvider` copies the asset into `filesDir` once, because Tesseract cannot read
an asset stream; `ClasspathTessDataProvider` does the equivalent on the JVM.

## Usage

```kotlin
implementation("io.github.andrew-malitchuk:uncial-lang-eng:<version>")
```

Nothing else. Bundling is one of four ways to supply language data — see
[Language data](../../docs/language-data.md) for the system, downloaded and bring-your-own
options.

## Known behaviours and pitfalls

- **`androidResources { enable = true }` is load-bearing.** A KMP Android library packages no
  assets at all without it, so the AAR would ship empty — silently. For a module whose entire
  payload is an asset, that is the whole artifact.
- **A static `src/androidMain/assets` directory is not picked up.** The data has to arrive as
  a generated source directory, which is also what makes Gradle order the download before
  packaging.
- **`tessdata_fast`, not `tessdata`** — the same bytes `lang-download` fetches, so a bundled
  model and a downloaded one are identical.

## Build

```bash
./gradlew :sdk:lang-eng:downloadLanguageData    # fetch + checksum
./gradlew :sdk:lang-eng:build
```
