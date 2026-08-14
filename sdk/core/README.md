# core

> The engine contracts — the module you only open when writing your own engine, rasterizer or language-data provider.

Gradle `:sdk:core` · Maven `io.github.andrew-malitchuk:uncial-core` · [full docs](../../docs/modules/core.md)

## Responsibility

Defines the roles an OCR pipeline is assembled from, and nothing that implements them.
**Rasterization and recognition are separate** (`PageRasterizer` / `TextRecognizer`), which
is what makes image input, PNG-based tests and engine swapping possible. **Engines announce
themselves** through `OcrEngineRegistry`, which is how `runtime` drives Tesseract and Vision
without naming either.

It also owns the Android `Context` (`UncialContext` + `UncialContextInitializer`), because
`engine-tesseract` and `pdf-text` both need one and neither should ask the consumer for it.

## Layout

```
source/engine/       OcrEngineFactory, OcrEngineRegistry, OcrCapabilities, DigitalTextExtractor
source/raster/       PageRasterizer, RasterizedDocument, Raster (expect), placeholderRaster
source/recognizer/   TextRecognizer
source/language/     LanguageDataProvider
source/log/          OcrLogger, LogLevel, debug/info/warn/error
source/android/      UncialContext, UncialContextInitializer          (androidMain)
```

## Dependencies

| Scope | Dependency | Why |
|---|---|---|
| `api` | `:sdk:model` | every contract is expressed in those types |
| `api` | `kotlinx-coroutines-core` | the contracts are suspending |
| `api` (android) | `androidx.startup` | contributes a `ContentProvider` to the merged manifest |
| `implementation` (android) | `androidx.annotation` | |

## Public API

| Type | Notes |
|---|---|
| `OcrEngineFactory` | `id`, `isAvailable()`, `capabilities(options)`, `suspend create(...)` |
| `OcrEngineRegistry` | `register`/`unregister`/`engines()`/`firstAvailable()`/`lastProbeFailure()`, lock-free |
| `OcrCapabilities` | what an engine can really do here, plus `droppedAnyOf(requested)` |
| `TextRecognizer` | `AutoCloseable`; `capabilities`, `suspend recognize(raster, pageIndex, options)` |
| `PageRasterizer` / `RasterizedDocument` | `open(bytes)` then `rasterize(pageIndex, options)` — page count before pixels |
| `Raster` | `expect class` over `Bitmap` / `BufferedImage` / `CGImage`; `width`, `height`, `sourceDpi`, `release()` |
| `DigitalTextExtractor` | declared here so `runtime` can use the digital path without depending on `pdf-text` |
| `LanguageDataProvider` | `available(requested)`, `materialize(languages)` — the bring-your-own-`.traineddata` seam |
| `OcrLogger` | `fun interface`, `OcrLogger.None` by default; Uncial logs nothing unless asked |
| `UncialContext` | `get()` / `install(applicationContext)` for apps that strip `InitializationProvider` |

## Usage

```kotlin
OcrEngineRegistry.register(myEngineFactory)
```

## Known behaviours and pitfalls

- **`materialize` returns the *parent* of `tessdata/`.** That is Tesseract's own `datapath`
  convention, quirk included — and the two bindings disagree downstream, so the JVM engine
  descends one level while the Android wrapper appends it itself.
- **One recognizer serves concurrent pages.** `UncialClient` creates it once and hands the
  same instance to every concurrent `extract`; an implementation must tolerate re-entrant
  `recognize`. All three bundled recognizers serialize internally.
- **`Raster.sourceDpi` is not `OcrOptions.renderDpi`.** Rasterizers clamp the request
  whenever a page would exceed `maxPageSide`; it is `null` for a caller-supplied image, and
  then the engine should say nothing rather than assert a dpi nobody rendered at.
- **`consumer-rules/` is packaged into the AAR**, keeping `UncialContextInitializer`, which
  R8 would otherwise strip — it is only ever named as a string in the merged manifest.

## Build

```bash
./gradlew :sdk:core:allTests
./gradlew :sdk:core:checkKotlinAbi
```
