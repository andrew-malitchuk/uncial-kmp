# lang-ukr — Claude Instructions

## Module Purpose

Ships Ukrainian `ukr.traineddata` as its own artifact, so the model never lands
unconditionally inside somebody's AAR. **There is no Kotlin code here** — the committed ABI
dump is empty, and it should stay empty.

## What This Module Is

`build.gradle.kts` and nothing else:

```kotlin
languageData {
    language = "ukr"
    sha256 = "…"
}
```

The `uncial.language-data` convention plugin turns that into a `downloadLanguageData` task and
wires its output into the AAR's assets and the JVM jar's resources. If you need to change how
that works, the code is in `build-logic/convention` — not here.

## Rules

- **Do not add source files.** A helper that reads this data belongs in `engine-tesseract`
  (`AndroidTessDataProvider`, `ClasspathTessDataProvider`) or in `core` as a contract.
- **The checksum and the URL change together.** Bumping `sha256` without a matching upstream
  change means the build fails on the *stale* file — the task re-verifies rather than
  checking mere existence, precisely so a changed expectation cannot pass.
- **`androidResources { enable = true }` stays.** Without it a KMP Android library packages no
  assets at all and this AAR ships empty, silently. That is the entire artifact.
- **The data is generated, never committed.** A static `src/androidMain/assets` directory is
  not picked up either; it has to arrive as a generated source directory, which is also what
  orders the download before packaging.
- **`tessdata_fast`, not `tessdata`** — the same bytes `lang-download` fetches, so a bundled
  model and a downloaded one are byte-identical. The full models are 12 MB (ukr) and 23 MB
  (eng); there is nothing smaller than fast to fall back to.
- **No iOS target, ever.** Vision ships its models with the OS.

## Verify

```bash
./gradlew :sdk:lang-ukr:downloadLanguageData
./gradlew :sdk:lang-ukr:build
unzip -l sdk/lang-ukr/build/outputs/aar/uncial-lang-ukr.aar | grep tessdata
```
