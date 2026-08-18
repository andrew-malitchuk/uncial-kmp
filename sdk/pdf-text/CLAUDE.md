# pdf-text — Claude Instructions

## Module Purpose

The digital fast path: reads a PDF's embedded text layer instead of OCR'ing a picture of it.
Implements `core`'s `DigitalTextExtractor` on all three platforms.

## Package Layout

```
source/extractor/   pdfTextExtractor, PDF_TEXT_EXTRACTOR_ID, PdfTextExtractor.{android,ios,jvm}
core/assembly/      DigitalPageAssembly — chunks into lines, lines into pages
```

## Rules

- **This module is optional and must stay optional.** Nothing in `runtime` may depend on it;
  the consumer wires `digitalTextExtractor` themselves. On Android it costs several MB of
  PDFBox-Android.
- **Scale points by `OcrOptions.renderScale`.** A digital page and an OCR'd page of the same
  document must carry directly comparable geometry, or `structure` cannot compare type sizes
  across a mixed document. This is the module's contract with `structure`, and it is not
  visible in any signature.
- **Return one entry per page, in order.** `lines` empty for a page with no usable text;
  `null` for the whole document only when *no* page had any — that is the runtime's signal
  to OCR everything.
- **"Usable" is about volume, not presence.** The 24-character threshold exists because a
  scanned PDF routinely carries a stray watermark or a stamped page number that would
  otherwise pass for a text layer and suppress OCR. Do not lower it to 1.
- **Confidence is `Certain` here.** A text layer is not a guess. `fontSize` is the size the
  PDF reports, not the OCR path's line-height proxy. This path produces no `OcrWord`s —
  `includeWords` is a recognition option.

## PDFBox Traps This Module Absorbs

- **`PDFTextStripper.writeString` is not a per-line callback.** With `sortByPosition` it
  fires once per *word* whenever a PDF positions words with `Td`/`TJ` offsets. Buffer the
  chunks and flush on `writeLineSeparator`, plus `endArticle` and `endPage`, which are not
  followed by a separator.
- **`TextPosition.yDirAdj` is the baseline, not the top of the glyphs.** `PDFTextStripper`
  itself uses `positionY - positionHeight`; a box anchored to `yDirAdj` sits a cap-height
  below its own text.
- **Crop box, not media box**, and `/Rotate 90|270` swaps the reported page size — the same
  rule `raster` follows, for the same reason.
- **A `0f` font size is real** (Type3 fonts, degenerate text matrices). Report it as it is;
  filtering happens in `structure`, which is where the median lives.

## Android Specifics

PDFBox-Android needs the application `Context` to initialise `PDFBoxResourceLoader` — take it
from `UncialContext`, never from a parameter. The AAR's consumer rules silence the dangling
`com.gemalto.jp2` reference (R8 fails the build over it otherwise), keep the reflectively
loaded font resources, and ignore the `javax.imageio` / `java.awt` names it never reaches.

## Verify

```bash
./gradlew :sdk:pdf-text:build :sdk:pdf-text:checkKotlinAbi
./gradlew :samples:cli:run --args="fixture /tmp/text.pdf --text --pages 2"
./gradlew :samples:cli:run --args="ocr /tmp/text.pdf"     # must report DigitalTextLayer, in ms
```
