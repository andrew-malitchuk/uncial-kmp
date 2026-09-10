# pdf-text

The digital fast path: reads a PDF's embedded text layer instead of OCR'ing a picture of it.

## Features

- **Exact and roughly a thousand times faster** than recognition, for any PDF that carries its own text.
- **Optional and not wired by default.** `UncialClient` uses it only if you set `digitalTextExtractor`; on Android it costs several MB of PDFBox-Android, which an app that only OCRs photographs should not pay.
- **One coordinate model**: points are scaled by `OcrOptions.renderScale`, so a digital page and an OCR'd page of the same document carry directly comparable geometry — which is what lets `structure` compare type sizes across a mixed document.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64. Depends on `core` (`api`).

## Core Components

- `pdfTextExtractor(logger): DigitalTextExtractor` — the `expect`/`actual` factory.
- `PDF_TEXT_EXTRACTOR_ID` — the `id` the extractor reports.

| Platform | Backend | Cost |
|---|---|---|
| Android | PDFBox-Android | Several MB; needs the application `Context` (via `UncialContext`) to initialise `PDFBoxResourceLoader`. |
| iOS | PDFKit (`PDFSelection`) | A system framework — nothing added to the binary. |
| JVM | PDFBox | — |

`extract` returns **one page per page of the document**, in order, with `lines` empty for pages that carry no usable text, or `null` when no page did — the runtime's signal to OCR the whole thing. "Usable" is a judgement about volume, not presence: a page needs at least 24 characters, because a scanned PDF routinely carries a stray watermark or a stamped page number that would otherwise pass for a text layer and suppress OCR.

The AAR ships consumer R8 rules for PDFBox-Android: they silence its dangling reference to the optional JP2 decoder (`com.gemalto.jp2`, which R8 otherwise fails the build over), keep its reflectively loaded font resources, and ignore the `javax.imageio` / `java.awt` names it never reaches on Android.

## Usage

```kotlin
val client = UncialClient {
    digitalTextExtractor = pdfTextExtractor()
    preferDigitalLayer = true
}
```

A document whose scanner OCR'd only the cover page comes back as `ExtractionSource.Mixed`: page 1 from the text layer, the rest through the engine.

!!! note "Confidence is `Certain` on this path"
    A text layer is not a guess. Lines produced here carry `Confidence.Certain`, and their
    `fontSize` is the type size the PDF reports rather than the OCR path's proxy derived from
    line height. This path produces no `OcrWord`s — `OcrOptions.includeWords` affects
    recognition only.

!!! warning "Two PDF quirks this module absorbs for you"
    `PDFTextStripper.writeString` is not a per-line callback — with `sortByPosition` it fires
    once per *word* whenever a PDF positions words with `Td`/`TJ` offsets, so chunks are
    buffered and flushed on the line separator (plus `endArticle` and `endPage`, which are
    not followed by one). And `TextPosition.yDirAdj` is the baseline, not the top of the
    glyphs, so a box anchored straight to it sits a cap-height below its own text.
