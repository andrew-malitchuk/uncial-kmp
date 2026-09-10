# structure

Geometry back to semantics: turns an `OcrDocument` into headings and paragraphs.

## Features

- **Pure `commonMain`**: no platform code, no coroutines, no I/O. Its only dependency is `model`.
- **A single pure function**, so it is fully testable and entirely skippable by callers who only want raw text.
- **Works on either extraction path**: the digital layer and OCR produce comparable geometry, so a mixed document reconstructs as one.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64.

## Core Components

### `DocumentStructure`

`DocumentStructure.reconstruct(document, options = StructureOptions.Default): List<DocBlock>` — blocks in reading order, empty if the document has no non-blank text.

What it does, in order:

- Sorts each page's lines into reading order (top to bottom, then left to right), because engines emit in their own order and Vision in particular reorders.
- Takes the **median** type size over the document as the body baseline — a median, not a mean, so that headings and page numbers cannot hide the very headings being looked for.
- Classifies a line as a heading when its size clears `headingRatio` (level 1 above `h1Ratio`, otherwise level 2) and it is no longer than `maxHeadingWords`; a whole paragraph set in large type is still a paragraph.
- Drops page furniture: lines that are nothing but digits, and first/last lines whose text repeats on enough pages. Repetition is measured with digits normalized away, so `Chapter 3 — page 41` and `… page 42` count as one key; a repeat whose **raw** text is identical every time is treated as a running head rather than as a chapter title.
- Joins lines into paragraphs, starting a new one when the vertical gap exceeds `paragraphGapRatio` times the line height. Gaps are only meaningful within a page — across a page break the paragraph is assumed to continue.
- Resolves end-of-line hyphenation by dropping a trailing hyphen (`сло-` + `во` becomes `слово`).

### `StructureOptions`

`headingRatio` (1.25), `h1Ratio` (1.6), `paragraphGapRatio` (1.2), `chromeFrequency` (0.4), `maxHeadingWords` (20), `minPagesForChromeDetection` (4), `dropChrome` (true). Validated in `init`, e.g. `h1Ratio` must be at least `headingRatio`.

Presets: `StructureOptions.Default` and `StructureOptions.KeepChrome`, which keeps running headers, running footers *and* page numbers — one flag, because they are one decision found by two different rules.

## Usage

```kotlin
val blocks = DocumentStructure.reconstruct(document)
blocks.forEach { block ->
    when (block) {
        is DocBlock.Heading -> println("#".repeat(block.level) + " " + block.text)
        is DocBlock.Paragraph -> println(block.text)
        else -> Unit
    }
}
```

!!! note "It assumes single-column, reflowable prose"
    Multi-column layouts, tables and lists are not handled yet — they are why `DocBlock` is
    expected to grow. Given a two-column scan it will interleave the columns, because it
    sorts by vertical position.

!!! warning "A `0f` or `NaN` font size must never vote"
    PDFBox reports 0 pt for Type3 fonts and degenerate text matrices, so a document can mix
    real sizes with zeros; letting them count drags the median below the body size and turns
    ordinary prose into headings. `NaN` is the same trap from the other side — every
    comparison against it is false, so an unreported size would sail past the ratio test.
    Both are filtered out of the median and rejected as headings.
