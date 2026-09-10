# Options

`OcrOptions` holds everything tunable about an extraction. One client has one `OcrOptions`,
fixed at construction and readable afterwards as `UncialClient.options`.

Set the fields on the builder, which mirrors `OcrOptions` one for one:

```kotlin
import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.RasterColor
import io.github.andrewmalitchuk.uncial.model.source.options.RecognitionQuality

val ocr = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    renderDpi = 200
    maxPageSide = 2600
    preferDigitalLayer = true
    recognitionQuality = RecognitionQuality.Accurate
    rasterColor = RasterColor.Grayscale
    includeWords = false
    pageRange = null
}
```

…or hand over a whole `OcrOptions`, which **overrides every individual property above**:

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions

val ocr = UncialClient {
    options = OcrOptions(renderDpi = 300, includeWords = true)
}
```

`OcrOptions` is a `data class` and validates in its `init` block, so an illegal combination
throws `IllegalArgumentException` where you build it, not three pages into a document.
Building a `UncialClient` itself never throws for any other reason — a missing engine
surfaces at the first `extract` call.

---

## Reference

| Field | Type | Default | Validation |
|---|---|---|---|
| `languages` | `List<OcrLanguage>` | `OcrLanguage.Default` (`ukr`, `eng`) | must not be empty |
| `renderDpi` | `Int` | `200` | `72..1200` |
| `maxPageSide` | `Int` | `2600` | at least `64` |
| `preferDigitalLayer` | `Boolean` | `true` | — |
| `recognitionQuality` | `RecognitionQuality` | `Accurate` | — |
| `rasterColor` | `RasterColor` | `Grayscale` | — |
| `includeWords` | `Boolean` | `false` | — |
| `pageRange` | `IntRange?` | `null` (all pages) | must start at `0` or later, must not be reversed |

The bounds are constants on the companion: `MIN_RENDER_DPI` (72), `MAX_RENDER_DPI` (1200),
`MIN_PAGE_SIDE` (64), `DEFAULT_RENDER_DPI` (200), `DEFAULT_MAX_PAGE_SIDE` (2600),
`PDF_POINTS_PER_INCH` (72f).

There is one ready-made preset:

```kotlin
OcrOptions.Fast   // renderDpi = 150, recognitionQuality = RecognitionQuality.Fast
```

---

## `renderDpi` vs `maxPageSide`: the effective dpi

These two interact, and the interaction is the one thing worth understanding here.

`renderDpi` is the resolution you ask for. 200 is the sweet spot for printed text: below
about 150 Tesseract's accuracy falls off a cliff; above about 300 you pay memory and time
for nothing.

`maxPageSide` is a hard cap on the **longer side of the rasterized page, in pixels**, applied
**after** `renderDpi`. It is the OOM guard — an A0 poster at 200 dpi is 9000 px across, and
that allocation is not survivable on a phone.

When the requested dpi would push a page past the cap, the rasterizer lowers the dpi until it
fits, rather than rendering big and downscaling. The resolution actually used is the
**effective dpi**, and it travels with the pixels as `Raster.sourceDpi`:

```
effectiveDpi = min(renderDpi, maxPageSide × 72 / longerSideInPoints)
```

For A4 (595 × 842 pt) at the defaults, the long side at 200 dpi is ≈ 2339 px — comfortably
under 2600, so the effective dpi is the requested 200. A page twice that size would be
clamped.

!!! note "Why this exists as a number and not a scale"
    Tesseract's layout heuristics take source resolution as an input, and it must be told the
    effective figure. Telling it `renderDpi` would describe pixels that were never produced.
    `Raster.sourceDpi` carries the real number, and is `null` for an image the caller
    supplied — in which case the engine is told nothing and estimates.

### `renderScale`

`renderScale` is derived, not settable:

```kotlin
val scale: Float = options.renderScale     // renderDpi / 72f
```

PDF user space is defined in points, 72 per inch, so `renderScale` is the factor that turns
points into the pixel coordinates every box in an `OcrDocument` uses. The `pdf-text` module
multiplies by it, which is what makes digital and OCR'd pages of one document directly
comparable. See [Coordinates](coordinates.md).

---

## `pageRange`

Zero-based and inclusive; `null` means every page.

```kotlin
val ocr = UncialClient { pageRange = 2..4 }   // pages 3, 4 and 5 as a human counts them
```

Three behaviours to know:

- **Clamped to the document.** `1..99` on a 3-page PDF processes pages 1 and 2. Running past
  the end is fine.
- **A range that selects nothing fails.** `pageRange = 50..60` on a 3-page PDF fails the
  extraction with `OcrError.InvalidInput` rather than handing back an empty document. So does
  a PDF with no pages at all; the message distinguishes the two.
- **A reversed range is rejected at construction.** `5..2` throws `IllegalArgumentException`,
  because a silently empty document is a far worse answer to "give me pages 5 to 2" than a
  rejected argument. `pageRange.first` must also be `>= 0`.

`0..Int.MAX_VALUE` is a legal way to say "to the end of the document" and is intersected
arithmetically, not walked.

`pageRange` also narrows what the digital-layer decision is made over, and it is reflected in
progress reporting: `OcrProgress.Started.pageCount` is the number of **selected** pages, while
`OcrProgress.Page.index` stays the page's index in the source document.

---

## `preferDigitalLayer`

`true` by default. Tries the PDF's embedded text layer before OCR — exact, and roughly 1000×
faster when the PDF has one.

It requires a `digitalTextExtractor` on the client. Without one this flag does nothing at all:

```kotlin
import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor

val ocr = UncialClient {
    digitalTextExtractor = pdfTextExtractor()   // from the uncial-pdf-text module
    preferDigitalLayer = true
}
```

Set it to `false` to force OCR even on a PDF that carries its text — useful when you suspect
the embedded layer is itself bad OCR output. See [Input](input.md#the-digital-text-layer-fast-path).

---

## `recognitionQuality`

```kotlin
RecognitionQuality.Fast       // roughly an order of magnitude faster, worse on small or noisy type
RecognitionQuality.Accurate   // the default; what you want for scanned documents
```

!!! warning "Only Vision honours this"
    Vision has a real `VNRequestTextRecognitionLevel` switch. The Tesseract engines ignore
    the field: their only equivalent needs legacy data that neither `tessdata_fast` nor
    `tessdata_best` ships, so asking for it would fail at init rather than run faster. Check
    `UncialClient.capabilities` rather than assuming a setting took effect.

    On iOS there is a second trap: Vision's language support is queried **per request**, and
    Ukrainian is accurate-level-only. Asking for `Fast` can therefore reduce the language set
    you actually get. `capabilities` reports what survived.

---

## `rasterColor`

```kotlin
RasterColor.Grayscale   // the default: one channel
RasterColor.Color       // four channels
```

Grayscale is the default because OCR engines binarize anyway, and one channel costs a quarter
of the memory of ARGB — which is what keeps a 300-page scan from an OOM on a cheap phone.
`Color` is only useful for a custom engine that reads colour.

---

## `includeWords`

Off by default. When on, engines also produce per-word boxes and confidences in
`OcrLine.words`; it costs extra work in every engine, and a caller who only wants reflowable
text never looks at them.

```kotlin
val ocr = UncialClient { includeWords = true }

val words = document.lines.flatMap { it.words }
```

An empty `words` list means "not requested or not supported" — never "this line has no
words". Check `capabilities.wordLevel` before concluding anything from an empty list.

---

## `languages`

A list in preference order; the default is `OcrLanguage.Default`, which is Ukrainian then
English.

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

languages = listOf(OcrLanguage.Ukrainian)
languages = listOf(OcrLanguage.custom("pol", bcp47 = "pl-PL"))
```

`OcrLanguage` is deliberately not an enum: Tesseract loads any `.traineddata` you supply, and
closing the set would break the bring-your-own-language-data promise. `custom` takes the
`.traineddata` basename and, optionally, the BCP-47 tag Vision expects — the code becomes a
filename and a URL path segment, so it is restricted to `[A-Za-z0-9_-]` (1–32 characters) and
anything else is rejected.

!!! note "You may not get everything you asked for"
    Engines that cannot handle a language **skip** it rather than failing the extraction.
    Vision has no Ukrainian before iOS 16; Tesseract has none without the matching
    `.traineddata`. `UncialClient.capabilities?.languages` reports what is actually available,
    and `capabilities.droppedAnyOf(requested)` answers the question directly:

    ```kotlin
    val caps = ocr.capabilities
    if (caps != null && caps.droppedAnyOf(ocr.options.languages)) {
        warnUser(caps.languages)
    }
    ```

    Supplying language data is covered in [Language Data](../language-data.md).
