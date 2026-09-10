# model

The SDK's vocabulary — the types that appear in the signature of every other Uncial module.

## Features

- **Zero dependencies**: Kotlin stdlib only. No coroutines, no serialization, no io, no platform code.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64.
- **One coordinate model**: every box is top-down pixels with the origin at the page's top-left, whichever engine produced it.
- **A closed error set**: `OcrError` instead of `null` plus a printed reason.

## Core Components

### Recognized text (`…uncial.model.text`)

- `OcrDocument`: `pages`, plus derived `text`, `lines`, `pageCount`, `isEmpty`, and a `source`.
- `OcrPage`: `index`, `size` (in the same pixel units as the boxes on it), `lines`, `orientation`, `source`.
- `OcrLine`: `text`, `box`, `fontSize`, `confidence`, `words`, `language`, `skewDegrees`, plus `script` computed on demand.
- `OcrWord`: `text`, `box`, `confidence`. Only populated when `OcrOptions.includeWords` is set *and* the engine supports it.
- `ExtractionSource`: `DigitalTextLayer`, `Ocr`, `Mixed` — which path produced a page or document. `ExtractionSource.of(pageSources)` reduces per-page sources to a document-level one.
- `Confidence`: a `@JvmInline value class` over `Float`, normalized to `0.0..1.0`. `Unknown` (backed by `NaN`), `Certain`, `of(normalized)`, `ofPercent(percent)` for Tesseract's `0..100`, plus `isKnown` and `orElse(fallback)`. Comparable, with `Unknown` sorting below every known value.
- `TextScript`: `Latin`, `Cyrillic`, `Mixed`, `Unknown`, and `TextScript.of(text)` — coarser than a language on purpose, because engines tell scripts apart far more reliably than languages.

### Geometry (`…uncial.model.geometry`)

- `Point`, `Size` (with `isEmpty`).
- `BoundingBox`: `left`, `top`, `width`, `height`, with `right`, `bottom`, `center`, `area`, `union(other)` and `BoundingBox.Zero`.
- `Orientation`: `Up`, `Right`, `Down`, `Left`, each with `degrees`. Coarse 90° page rotation — fine skew lives on `OcrLine.skewDegrees`.

### Structure (`…uncial.model.structure`)

- `DocBlock`: a sealed interface with `text`, implemented by `Heading(level, text)` and `Paragraph(text)`. Produced by `structure`, never by an engine.

### Configuration and errors

- `OcrLanguage`: deliberately **not** an enum, so bring-your-own `.traineddata` stays possible. Constants `Ukrainian`, `English`, `Default`; anything else goes through `OcrLanguage.custom(tesseractCode, bcp47)`. Carries both identifiers the engines need — `tesseractCode` for Tesseract, `bcp47` (nullable) for Vision. Custom codes are restricted to `[A-Za-z0-9_-]{1,32}` because the code becomes a filename and a URL segment.
- `OcrOptions`: `languages`, `renderDpi` (default 200), `maxPageSide` (default 2600), `preferDigitalLayer`, `recognitionQuality`, `rasterColor`, `includeWords`, `pageRange`, plus the derived `renderScale`. Validated in `init` — an out-of-range dpi or a reversed `pageRange` is rejected rather than silently producing nothing. `OcrOptions.Fast` is a preset at 150 dpi with `RecognitionQuality.Fast`.
- `RecognitionQuality` (`Fast`/`Accurate`) and `RasterColor` (`Grayscale`/`Color`).
- `OcrError`: a sealed class extending `Exception`, so the same type works as a `Result` failure in Kotlin and as a thrown, typed error in Swift. Cases: `NoLanguageData(languages, searchedPath)`, `RenderFailed(pageIndex)`, `EngineInit`, `Unsupported`, `InvalidInput`, `RecognitionFailed(pageIndex)`.

## Usage

```kotlin
document.pages.forEach { page ->
    page.lines.forEach { line ->
        if (line.confidence.isKnown && line.confidence.value < 0.6f) flagForReview(line)
    }
}
```

!!! note "There is no `OcrError.Cancelled`"
    Cancelling an extraction throws `CancellationException`, exactly as any other suspend
    function would. Uncial never wraps it, so structured concurrency keeps working.

!!! warning "`when` over `OcrError` and `DocBlock` needs an `else`"
    Both hierarchies are expected to gain subtypes in minor releases — lists and tables for
    `DocBlock`, new failure cases for `OcrError`.

!!! warning "`OcrDocument.copy(pages = …)` keeps the old `source`"
    `source` is a constructor property with a default derived from the pages, and `copy()`
    does not re-run default arguments. Narrowing a `Mixed` document with `copy` leaves it
    reporting `Mixed`; use the constructor instead so the default runs again.
