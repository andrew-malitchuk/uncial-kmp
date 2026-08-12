# model

> The SDK's vocabulary — the types that appear in the signature of every other Uncial module.

Gradle `:sdk:model` · Maven `io.github.andrew-malitchuk:uncial-model` · [full docs](../../docs/modules/model.md)

## Responsibility

Names the things Uncial talks about: pages, lines, words, boxes, confidence, languages,
options and errors. It is the one module every other module depends on, so it is kept small
enough that nobody wants to change it — Kotlin stdlib only, no coroutines, no serialization,
no I/O, no platform code.

Two decisions here are binding on everything else. **One coordinate model:** every box is
top-down pixels with the origin at the page's top-left, whatever the engine underneath
reported. **A closed error set:** `OcrError` instead of `null` plus a printed reason.

## Layout

```
source/geometry/     Point, Size, BoundingBox, Orientation
source/text/         OcrDocument, OcrPage, OcrLine, OcrWord, Confidence, ExtractionSource, TextScript
source/structure/    DocBlock
source/options/      OcrOptions, RecognitionQuality, RasterColor
source/language/     OcrLanguage
source/error/        OcrError
```

No `core/`: the module has no internal declarations at all.

## Dependencies

None. Kotlin stdlib only.

## Public API

| Type | Notes |
|---|---|
| `OcrDocument` | `pages`, plus derived `text`, `lines`, `pageCount`, `isEmpty`, `source` |
| `OcrPage` | `index`, `size`, `lines`, `orientation`, `source` |
| `OcrLine` | `text`, `box`, `fontSize`, `confidence`, `words`, `language`, `skewDegrees`, `script` |
| `OcrWord` | `text`, `box`, `confidence` — only when `includeWords` is set *and* the engine supports it |
| `Confidence` | `@JvmInline value class` over `Float`, `0.0..1.0`; `Unknown` (NaN), `Certain`, `ofPercent` |
| `ExtractionSource` | `DigitalTextLayer`, `Ocr`, `Mixed`, plus `of(pageSources)` |
| `TextScript` | `Latin`, `Cyrillic`, `Mixed`, `Unknown`, `of(text)` |
| `BoundingBox` | `left`/`top`/`width`/`height` with `right`, `bottom`, `center`, `area`, `union` |
| `Orientation` | `Up`/`Right`/`Down`/`Left`, coarse 90° rotation; fine skew is `OcrLine.skewDegrees` |
| `DocBlock` | sealed: `Heading(level, text)`, `Paragraph(text)` — produced by `structure`, never by an engine |
| `OcrLanguage` | not an enum, so bring-your-own `.traineddata` stays possible; `custom(tesseractCode, bcp47)` |
| `OcrOptions` | `languages`, `renderDpi`, `maxPageSide`, `preferDigitalLayer`, `pageRange`, … validated in `init` |
| `OcrError` | sealed `Exception`: `NoLanguageData`, `RenderFailed`, `EngineInit`, `Unsupported`, `InvalidInput`, `RecognitionFailed` |

## Usage

```kotlin
document.pages.forEach { page ->
    page.lines.forEach { line ->
        if (line.confidence.isKnown && line.confidence.value < 0.6f) flagForReview(line)
    }
}
```

## Known behaviours and pitfalls

- **There is no `OcrError.Cancelled`.** Cancelling throws `CancellationException`, exactly
  as any other suspend function would, so structured concurrency keeps working.
- **`when` over `OcrError` and `DocBlock` needs an `else`.** Both are expected to gain
  subtypes in minor releases — lists and tables, new failure cases.
- **`OcrDocument.copy(pages = …)` keeps the old `source`.** `source` has a default derived
  from the pages, and `copy()` does not re-run default arguments. Use the constructor.

## Build

```bash
./gradlew :sdk:model:allTests
./gradlew :sdk:model:checkKotlinAbi     # the committed api/*.api must match
```
