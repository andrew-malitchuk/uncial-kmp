# Document Structure

An `OcrDocument` is pages, lines and boxes. `DocumentStructure.reconstruct` turns that
geometry back into semantics: headings and paragraphs.

```kotlin
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure

val blocks = DocumentStructure.reconstruct(document)
```

That is the whole API surface for the common case. `blocks` is a `List<DocBlock>` in reading
order, empty when the document has no non-blank text.

---

## It is not run for you

`reconstruct` is a **pure function the caller invokes explicitly**. `extract` does not call it,
and `OcrDocument` does not carry blocks. That is deliberate: it lives in its own module
(`uncial-structure`), it is pure `commonMain` with no platform dependencies and no coroutines,
and a caller who only wants raw text never pays for it.

!!! warning "Run it off the main thread for anything large"
    Reconstruction walks every line of the document — twice, once to find repeating page
    furniture and once to build blocks. On a 300-page scan that is real work. It is a pure
    function with no suspension points of its own, so moving it off the main thread is the
    caller's job:

    ```kotlin
    val blocks = withContext(Dispatchers.Default) {
        DocumentStructure.reconstruct(document)
    }
    ```

    In Swift, a `nonisolated static func` on a `@MainActor` type runs on the concurrent
    executor rather than inheriting the actor:

    ```swift
    let blocks = UncialBootstrap.shared.reconstruct(document: document)
    ```

---

## `DocBlock`

`DocBlock` is a sealed interface with a single shared property, `text`, and two
implementations today:

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock

blocks.forEach { block ->
    when (block) {
        is DocBlock.Heading -> renderHeading(block.level, block.text)
        is DocBlock.Paragraph -> renderParagraph(block.text)
        else -> renderParagraph(block.text)
    }
}
```

**`DocBlock.Heading(level, text)`** — `level` is `1` for a top-level heading and `2` for a
subheading, derived from how far the line's type size exceeds the document's median. It is not
read from any document outline; a scanned page has none. The constructor requires `level >= 1`.

**`DocBlock.Paragraph(text)`** — a body paragraph, reflowed from one or more recognized lines.
Lines are joined and end-of-line hyphenation is resolved, so `сло-` + `во` becomes `слово`.

!!! warning "Always give the `when` an `else`"
    This hierarchy is expected to grow — lists, tables and multi-column handling each add a
    subtype in a minor release. An exhaustive `when` today is a broken build after a minor
    upgrade, even where the compiler currently accepts it.

---

## How headings are found

Heading detection is **relative to the document's own median type size**, not to any absolute
point size. A technical manual set in 11 pt with 12 pt headings and a novel set in 10 pt with
18 pt headings are both handled by the same ratios.

The median is taken over every non-blank line in the document. A line is a heading when:

1. its `fontSize` is at least `headingRatio` × the median, and
2. it is no longer than `maxHeadingWords` words — a whole paragraph set in large type is still
   a paragraph.

It is level 1 when the size also reaches `h1Ratio` × the median, level 2 otherwise.

!!! note "Where `fontSize` comes from"
    For the digital text layer it is a real font metric. For OCR it is a proxy derived from the
    line's height. The heuristics only ever compare it against the document's own median, so
    the proxy is good enough — but it is not a number to show a user.

    Lines whose size is `0` or not finite do not vote in the median and are never headings.
    PDFBox reports 0 pt for Type3 fonts and degenerate text matrices, and letting those count
    would drag the median below the body size and turn ordinary prose into headings. If a
    document reports no usable type sizes at all, the median is `0` and everything is a
    paragraph — which is the honest answer, not a failure.

### Paragraph breaks

Within a page, a vertical gap larger than `paragraphGapRatio` × the line's own height starts a
new paragraph. Across a page break the previous line's position says nothing, so a paragraph is
assumed to continue — the right default for books, where paragraphs routinely straddle the page
turn.

Lines are processed in reading order (top to bottom, then left to right) rather than in the
order the engine produced them; Vision in particular reorders by confidence.

---

## Running headers, footers and page numbers

By default, page furniture is **dropped**. Two rules do it, and both answer to `dropChrome`:

- **Repetition.** The first and last line of each page are keyed by their text with digits
  normalized away, so `Розділ 3 — с. 41` and `Розділ 3 — с. 42` count as the same header. A key
  appearing on at least `chromeFrequency` of the pages — and at least twice regardless — is
  treated as chrome. Detection needs at least `minPagesForChromeDetection` pages before
  repetition means anything.
- **Lone page numbers.** A line consisting only of digits and whitespace is a page number
  wherever it sits, with no page-count requirement.

A heading normally beats chrome, so a book whose pages open with `Розділ 3`, `Розділ 4`, … keeps
its chapter titles even though they collapse to one normalized key. The exception is a repeat
whose **raw** text is identical every time — `ІСТОРІЯ УКРАЇНИ` atop all 300 pages. That is a
running head, and setting it in display type does not make it content, so it is dropped rather
than emitted as a heading once per page.

To keep everything, use the preset:

```kotlin
import io.github.andrewmalitchuk.uncial.structure.source.options.StructureOptions

val blocks = DocumentStructure.reconstruct(document, StructureOptions.KeepChrome)
```

`KeepChrome` is `StructureOptions(dropChrome = false)`. It switches off both rules — a
dictionary's guide words and a numbered index are content, and an option called *Keep* that
silently ate the page numbers would be a lie.

---

## `StructureOptions`

```kotlin
val blocks = DocumentStructure.reconstruct(
    document,
    StructureOptions(headingRatio = 1.15f, maxHeadingWords = 12),
)
```

| Field | Type | Default | What it does |
|---|---|---|---|
| `headingRatio` | `Float` | `1.25f` | A line whose type size is at least this multiple of the document's median is a heading. |
| `h1Ratio` | `Float` | `1.6f` | A heading at least this large is level 1 rather than level 2. |
| `paragraphGapRatio` | `Float` | `1.2f` | A vertical gap larger than this multiple of the line's own height starts a new paragraph. |
| `chromeFrequency` | `Float` | `0.4f` | A repeating first/last line appearing on at least this fraction of pages is a running header or footer. |
| `maxHeadingWords` | `Int` | `20` | A longer line is prose in large type, not a heading. |
| `minPagesForChromeDetection` | `Int` | `4` | Chrome detection needs at least this many pages. |
| `dropChrome` | `Boolean` | `true` | Whether page furniture — running headers, footers *and* lone page numbers — is removed at all. |

Validation, checked in the constructor and thrown as `IllegalArgumentException`:

- `headingRatio > 1`
- `h1Ratio >= headingRatio`
- `paragraphGapRatio > 0`
- `chromeFrequency` in `0f..1f`
- `maxHeadingWords > 0`

Two presets are provided: `StructureOptions.Default` (the defaults above, tuned on Ukrainian
scanned books) and `StructureOptions.KeepChrome`.

### Tuning

**Body text is coming back as headings** — raise `headingRatio`. This is the usual symptom of a
document with an unusually flat type hierarchy.

**Headings are being missed** — lower `headingRatio` toward `1.1f`. A manual with 11 pt body and
12 pt headings needs about `1.05`–`1.1`; `1.25` will never fire.

**Everything is level 2** — lower `h1Ratio`. It must stay at or above `headingRatio`.

**Paragraphs run together** — lower `paragraphGapRatio`. **Every line is its own paragraph** —
raise it; tightly leaded scans need more.

**Running headers survive** — lower `chromeFrequency`, or check that the document has at least
`minPagesForChromeDetection` pages. **Real content is disappearing from page edges** — raise
`chromeFrequency`, or switch to `KeepChrome`.

---

## What it assumes

Single-column, reflowable prose. Multi-column layouts, tables and lists are **not handled** —
they are the reason `DocBlock` is expected to grow. Given a two-column scan, reconstruction
interleaves the columns, because it sorts by vertical position.
