# core

The engine contracts — the module a consumer only opens when writing their own engine, rasterizer or language-data provider.

## Features

- **Roles, not an engine**: rasterization (`PageRasterizer`) and recognition (`TextRecognizer`) are separate interfaces, which is what makes image input, PNG-based tests and engine swapping possible.
- **Engine discovery without a dependency**: `OcrEngineRegistry` lets engine modules announce themselves, so `runtime` never names Tesseract or Vision.
- **Honest capabilities**: `OcrCapabilities` states what an engine can actually do here, instead of pretending the platforms are equal.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64.
- Depends on `model` and `kotlinx-coroutines-core` (both `api`), plus `androidx.startup` on Android.

## Core Components

### Engine (`…uncial.core.engine`)

- `OcrEngineFactory`: `id`, `isAvailable()`, `capabilities(options)`, and `suspend create(options, logger, languageData)`. `isAvailable()` is public and honest on purpose — the JVM engine binds to a system `libtesseract` that may simply not be installed.
- `OcrCapabilities`: `engineId`, `engineVersion`, `languages`, `wordLevel`, `confidence`, `perLineLanguage`, `orientationDetection`, `skewDetection`, plus `droppedAnyOf(requested)`.
- `OcrEngineRegistry`: an object with `register(factory)` (idempotent, keyed by `id`), `unregister(id)`, `engines()`, `firstAvailable()` and `lastProbeFailure()`. Registration is lock-free because on Android an initializer registers while application code may already be reading.
- `DigitalTextExtractor`: `id` and `suspend extract(bytes, options): OcrDocument?`. Declared here rather than in `pdf-text` so the runtime can use the digital path without depending on the module that implements it.

### Recognition and rasterization

- `TextRecognizer` (`AutoCloseable`): `capabilities` and `suspend recognize(raster, pageIndex, options): OcrPage`.
- `PageRasterizer`: `suspend open(bytes): RasterizedDocument`. Opening is separate from rasterizing so the page count is known before any pixels are allocated.
- `RasterizedDocument` (`AutoCloseable`): `pageCount` and `suspend rasterize(pageIndex, options): Raster`.
- `Raster`: an `expect class` with `width`, `height`, `sourceDpi` and `release()`. Each platform's actual exposes and accepts the native image — `Raster(bitmap)` on Android, `Raster(bufferedImage)` on the JVM, `Raster(cgImage)` on iOS.
- `placeholderRaster(width, height)`: a raster with a size and **no pixels**, for fakes.

### Language data and logging

- `LanguageDataProvider`: `suspend available(requested)` and `suspend materialize(languages): String`. This is the bring-your-own-`.traineddata` seam.
- `OcrLogger`: a `fun interface` taking `(LogLevel, message, throwable)`, with `OcrLogger.None` as the default, plus the `debug` / `info` / `warn` / `error` extensions. Uncial logs nothing unless a logger is supplied.

### Android plumbing

- `UncialContextInitializer`: an `androidx.startup` `Initializer` that captures the application context before `Application.onCreate`, so no public API has to ask for a `Context`.
- `UncialContext`: `get()` and `install(applicationContext)` — the escape hatch for apps that strip `InitializationProvider` from their merged manifest.

The AAR ships a consumer rule keeping `UncialContextInitializer`, which R8 would otherwise
remove: it is only ever named as a string in the merged manifest.

## Usage

Implement `OcrEngineFactory` and register it, or hand it to `UncialClient`'s builder directly:

```kotlin
OcrEngineRegistry.register(myEngineFactory)
```

!!! warning "`materialize` returns the *parent* of `tessdata/`"
    If it returns `/data/user/0/app/files`, then `/data/user/0/app/files/tessdata/ukr.traineddata`
    must exist. That mirrors Tesseract's own `datapath` convention, quirk included — and note
    that the two bindings disagree downstream: the Tesseract4Android wrapper appends
    `tessdata/` itself, while Tess4J/libtesseract wants the directory, so the JVM engine
    descends one level.

!!! warning "One recognizer serves concurrent pages"
    `UncialClient` creates a `TextRecognizer` once and hands the same instance to every
    concurrent `extract`. An implementation must tolerate re-entrant `recognize` calls; all
    three bundled recognizers hold a single native handle and serialize internally.

!!! warning "`Raster.sourceDpi` is not `OcrOptions.renderDpi`"
    Rasterizers clamp the requested dpi whenever a page would exceed `maxPageSide`, so
    `sourceDpi` is the resolution the pixels were really rendered at. It is `null` for a
    raster the caller built from their own image — in which case say nothing to the engine
    rather than asserting a number nobody rendered at.
