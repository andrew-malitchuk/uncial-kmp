# pdf-text

> The digital fast path: reads a PDF's embedded text layer instead of OCR'ing a picture of it.

Gradle `:sdk:pdf-text` · Maven `io.github.andrew-malitchuk:uncial-pdf-text` · [full docs](../../docs/modules/pdf-text.md)

## Responsibility

Implements `DigitalTextExtractor` on all three platforms. For any PDF that carries its own
text this is exact and roughly a thousand times faster than recognition — milliseconds
instead of seconds per page.

It is **optional and deliberately not wired by default**: on Android it costs several MB of
PDFBox-Android, which an app that only OCRs photographs should not pay. `UncialClient` uses
it only when you set `digitalTextExtractor`.

Points are scaled by `OcrOptions.renderScale`, so a digital page and an OCR'd page of the
same document carry directly comparable geometry — which is what lets `structure` compare
type sizes across a mixed document.

## Layout

```
source/extractor/   pdfTextExtractor (expect/actual), PDF_TEXT_EXTRACTOR_ID
                    PdfTextExtractor.{android,ios,jvm}
core/assembly/      DigitalPageAssembly     (internal)
```

## Dependencies

| Scope | Dependency |
|---|---|
| `api` | `:sdk:core` |
| android | PDFBox-Android (several MB; needs the application `Context` via `UncialContext`) |
| ios | PDFKit — a system framework, nothing added to the binary |
| jvm | PDFBox |

## Public API

| Declaration | Notes |
|---|---|
| `pdfTextExtractor(logger = OcrLogger.None)` | the `expect`/`actual` factory |
| `PDF_TEXT_EXTRACTOR_ID` | the `id` the extractor reports |

`extract` returns **one page per page of the document**, in order, with `lines` empty for
pages that carry no usable text, or `null` when no page did — the runtime's signal to OCR the
whole thing. "Usable" is a judgement about volume, not presence: a page needs at least 24
characters, because a scanned PDF routinely carries a stray watermark or a stamped page
number that would otherwise pass for a text layer and suppress OCR.

## Usage

```kotlin
val client = UncialClient {
    digitalTextExtractor = pdfTextExtractor()
    preferDigitalLayer = true
}
```

A document whose scanner OCR'd only the cover page comes back as `ExtractionSource.Mixed`:
page 1 from the text layer, the rest through the engine.

## Known behaviours and pitfalls

- **`PDFTextStripper.writeString` is not a per-line callback.** With `sortByPosition` it
  fires once per *word* whenever a PDF positions words with `Td`/`TJ` offsets — which is
  common. Chunks are buffered and flushed on `writeLineSeparator`, plus `endArticle` and
  `endPage`, which are not followed by a separator.
- **`TextPosition.yDirAdj` is the baseline, not the top of the glyphs.** A box anchored
  straight to it sits a cap-height below its own text.
- **Use the crop box, never the media box** — `LegacyPDFStreamEngine` expresses every
  `TextPosition` relative to it, and `/Rotate 90|270` swaps the reported page size too.
- **Confidence is `Certain` on this path.** A text layer is not a guess, and `fontSize` is
  the type size the PDF reports rather than a proxy derived from line height. No `OcrWord`s
  are produced here.
- **`consumer-rules/` is packaged into the AAR**, silencing PDFBox-Android's dangling
  reference to the optional JP2 decoder (which R8 otherwise fails the build over) and keeping
  its reflectively loaded font resources.

## Build

```bash
./gradlew :sdk:pdf-text:build
./gradlew :samples:cli:run --args="fixture /tmp/text.pdf --text --pages 2"   # a PDF with a text layer
```
