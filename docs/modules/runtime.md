# runtime

What a consumer actually calls: `UncialClient`, its builder, and the per-page progress it emits.

## Features

- **No compile-time dependency on any engine.** Its dependencies are `model` and `core` (`api`) plus `raster` (`implementation`); engines are reached only through `OcrEngineFactory`, resolved from `OcrEngineRegistry` at first use.
- **Digital layer first, OCR for the rest**: when a `DigitalTextExtractor` is wired in and `preferDigitalLayer` is set, pages that already carry text skip OCR entirely and the document reports `ExtractionSource.Mixed` if only some did.
- **Progress and cancellation**: `extractAsFlow` emits per page and checks the coroutine's state at every page boundary.
- **Typed failures**: every `Result` failure is an `OcrError`, never a raw platform exception.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64.

## Core Components

### `UncialClient`

- `extract(bytes)` / `extract(raster)`: `Result<OcrDocument>` in one call.
- `extractAsFlow(bytes)` / `extractAsFlow(raster)`: a cold `Flow<OcrProgress>` that throws on failure rather than emitting it.
- `extractOrThrow(bytes)` / `extractOrThrow(raster)`: the same work as throwing functions annotated for Swift, because Kotlin's `Result` does not survive Obj-C export.
- `capabilities`: the engine's `OcrCapabilities`, or `null` when no engine is available; `isAvailable` is the boolean form.
- `hasDigitalTextLayerSupport`, `options`.
- `close()`: releases the recognizer. Idempotent, and safe to call while an extraction is running.

### `UncialClientBuilder`

Created through the `UncialClient { … }` function. It mirrors `OcrOptions` one for one — `languages`, `renderDpi`, `maxPageSide`, `preferDigitalLayer`, `recognitionQuality`, `rasterColor`, `includeWords`, `pageRange` — or takes a whole `options` object, which overrides the individual properties.

The rest is the composition root, hand-written because a DI framework inside a library is a dependency forced on the consumer: `engine`, `rasterizer`, `digitalTextExtractor`, `languageData`, `logger`. All default to `null` (or `OcrLogger.None`), meaning "resolve from the registry / use the platform default / do nothing".

Building never throws: a missing or unusable engine surfaces at the first `extract` as `OcrError.Unsupported`, so a client is safe to construct in an initializer or a DI graph.

### `OcrProgress`

- `Started(pageCount, source)`: emitted once, before any page, so a progress bar has a total.
- `Page(index, completed, of, page)`: emitted per page, with `fraction` for a progress bar and the recognized page for incremental rendering.
- `Done(document)`: emitted last.

## Usage

```kotlin
val ocr = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    renderDpi = 200
    digitalTextExtractor = pdfTextExtractor()   // needs uncial-pdf-text
}

ocr.extractAsFlow(pdfBytes).collect { progress ->
    when (progress) {
        is OcrProgress.Started -> ui.showTotal(progress.pageCount)
        is OcrProgress.Page -> ui.update(progress.fraction)
        is OcrProgress.Done -> render(progress.document)
        else -> Unit
    }
}
```

On Android the engine registers itself through `androidx.startup`, so nothing has to be set. Everywhere else, call `installTesseractEngine()` or `installVisionEngine()` once before the first extraction.

!!! warning "`close()` blocks the calling thread"
    The bundled engines take a *blocking* native lock in `close()`, because freeing a handle
    underneath a running recognition is a SIGSEGV rather than an exception. Closing therefore
    waits out the work in flight — on Android a whole page, on the JVM the native call in
    progress. Do not call it from `onDestroy` or a main-thread `use { }` and expect it to
    return instantly.

!!! note "The digital fast path is opt-in"
    `preferDigitalLayer` is inert without a `digitalTextExtractor`. It is not wired
    automatically because `uncial-pdf-text` pulls several MB of PDFBox-Android into an app
    that may only ever OCR photographs.
