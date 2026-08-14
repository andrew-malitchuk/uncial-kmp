# core — Claude Instructions

## Module Purpose

The engine contracts: the roles an OCR pipeline is assembled from, and nothing that
implements them. Also the home of the Android `Context` plumbing, because more than one
platform module needs it.

## Package Layout

```
source/{engine,raster,recognizer,language,log}/    the contracts
source/android/                                    UncialContext, UncialContextInitializer
```

New contract → `source/<role>/`. Internal helper → `core/<topic>/` (the module has none
today; adding the first one is fine, adding it at the package root is not).

## What Belongs Here vs Elsewhere

| Belongs in `core` | Does not |
|---|---|
| An interface an engine implements | The engine itself (`engine-*`) |
| `Raster` (the `expect class`) and `placeholderRaster` | A rasterizer implementation (`raster`) |
| `DigitalTextExtractor` (the contract) | The PDF reader behind it (`pdf-text`) |
| `LanguageDataProvider` | Any provider that reads assets, the classpath or the network |

`DigitalTextExtractor` living here rather than in `pdf-text` is deliberate: it is what lets
`runtime` use the digital path without depending on the module that implements it. Do not
"tidy" it into `pdf-text`.

## Rules

- **No third-party dependencies beyond coroutines.** Anything else is a dependency forced on
  every consumer of the SDK.
- **Never depend on an engine module, `raster`, `pdf-text` or `runtime`.** The arrows point
  the other way, always.
- **`OcrEngineRegistry` must stay lock-free.** On Android an `androidx.startup` initializer
  registers while application code may already be reading.
- **`isAvailable()` is allowed to say no.** Do not paper over a missing system library with
  an optimistic `true`; the JVM Tesseract binding depends on this being honest.
- **`materialize` returns the *parent* of `tessdata/`.** That is Tesseract's convention and
  the contract's; if you change it, every provider and both engines change with it.
- **`Raster` actuals retain and release.** The iOS actual retains the `CGImage` in its
  constructor and releases in `release()` — there is no ARC for a `CPointer`. Keep that
  symmetry in any new actual.
- **Five bindings name classes by string** and break silently: `UncialContextInitializer` in
  `androidMain/AndroidManifest.xml` and in `consumer-rules/*.pro`. A green build proves
  nothing about them — check the merged manifest and the dex of a release APK.
- **`OcrLogger.None` is the default.** Uncial logs nothing unless the consumer asks.

## Verify

```bash
./gradlew :sdk:core:allTests :sdk:core:checkKotlinAbi
./gradlew :samples:android-app:assembleRelease    # the only build that proves the keep rules
```
