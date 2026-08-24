# runtime

> What a consumer actually calls: `UncialClient`, its builder, and the per-page progress it emits.

Gradle `:sdk:runtime` · Maven `io.github.andrew-malitchuk:uncial-runtime` · [full docs](../../docs/modules/runtime.md)

## Responsibility

The single entry point. It opens a document, decides page by page whether the digital text
layer is enough, drives the engine over the rest, reports progress, maps every failure to an
`OcrError`, and hands back one `OcrDocument`.

**The dependency list is this module's most important property: there is no dependency on
any engine.** Engines are reached only through `OcrEngineFactory`, resolved from
`OcrEngineRegistry` at first use, so `runtime` does not know their class names and cannot be
made to depend on Tesseract or Vision by accident.

## Layout

```
source/client/      UncialClient, UncialClientBuilder, UncialClientFactory
source/progress/    OcrProgress
core/pipeline/      ExtractionPipeline     (internal)
core/error/         ErrorMapper, runCatchingOcr
```

`UncialClient` deliberately does **not** sit at the bare package root: its internals would
otherwise land in `…uncial.core.*`, the package `:sdk:core` already owns — a split package
across two artifacts.

## Dependencies

| Scope | Dependency |
|---|---|
| `api` | `:sdk:model`, `:sdk:core` |
| `implementation` | `:sdk:raster` |
| `testImplementation` | `:sdk:engine-fake`, `kotlinx-coroutines-test`, Turbine |

## Public API

| Declaration | Notes |
|---|---|
| `UncialClient { … }` | the builder function; mirrors `OcrOptions` one for one, plus `engine`, `rasterizer`, `digitalTextExtractor`, `languageData`, `logger` |
| `extract(bytes)` / `extract(raster)` | `Result<OcrDocument>` in one call |
| `extractAsFlow(bytes)` / `extractAsFlow(raster)` | cold `Flow<OcrProgress>`; throws on failure rather than emitting it |
| `extractOrThrow(…)` | the same work as throwing functions, annotated for Swift — `Result` does not survive Obj-C export |
| `capabilities` / `isAvailable` | the engine's real capabilities, or `null` when none is available |
| `close()` | releases the recognizer; idempotent, safe to call during an extraction |
| `OcrProgress` | `Started(pageCount, source)`, `Page(index, completed, of, page)` with `fraction`, `Done(document)` |

Building never throws: a missing or unusable engine surfaces at the first `extract` as
`OcrError.Unsupported`, so a client is safe to construct in an initializer or a DI graph.

## Usage

```kotlin
val ocr = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
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

On Android the Tesseract engine registers itself through `androidx.startup`. Everywhere else,
call `installTesseractEngine()` or `installVisionEngine()` once before the first extraction.

## Known behaviours and pitfalls

- **`close()` blocks the calling thread.** The bundled engines take a *blocking* native lock,
  because freeing a handle underneath a running recognition is a SIGSEGV, not an exception.
  Do not call it from `onDestroy` and expect it to return instantly.
- **The digital fast path is opt-in.** `preferDigitalLayer` is inert without a
  `digitalTextExtractor`; wiring it automatically would pull several MB of PDFBox-Android
  into an app that may only ever OCR photographs.
- **`kotlin.runCatching` is banned in the extraction path** — it swallows
  `CancellationException`. Use `runCatchingOcr`.

## Build

```bash
./gradlew :sdk:runtime:allTests
```
