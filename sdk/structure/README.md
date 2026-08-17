# structure

> Geometry back to semantics: turns an `OcrDocument` into headings and paragraphs.

Gradle `:sdk:structure` · Maven `io.github.andrew-malitchuk:uncial-structure` · [full docs](../../docs/modules/structure.md)

## Responsibility

Takes the lines an engine produced and decides what they *are*: which are headings, where
paragraphs begin and end, which lines are page furniture and should be dropped. It is a pure
function over geometry — 100 % `commonMain`, no platform code, no coroutines, no I/O — so it
is fully testable and entirely skippable by a caller who only wants raw text.

It works on either extraction path, because the digital layer and OCR produce comparable
geometry: a document whose cover page carries text and whose body was scanned reconstructs
as one.

## Layout

```
source/reconstruction/  DocumentStructure
source/options/         StructureOptions
core/builder/           BlockBuilder        (internal)
```

## Dependencies

`api(:sdk:model)`. Nothing else.

## Public API

| Type | Notes |
|---|---|
| `DocumentStructure.reconstruct(document, options)` | `List<DocBlock>` in reading order; empty for a document with no non-blank text |
| `StructureOptions` | `headingRatio` 1.25, `h1Ratio` 1.6, `paragraphGapRatio` 1.2, `chromeFrequency` 0.4, `maxHeadingWords` 20, `minPagesForChromeDetection` 4, `dropChrome` |
| `StructureOptions.Default` / `.KeepChrome` | presets; `KeepChrome` keeps running heads, footers *and* page numbers — one decision found by two rules |

What `reconstruct` does, in order: sorts lines into reading order; takes the **median** type
size as the body baseline; classifies a line as a heading when it clears `headingRatio` and
is short enough; drops page furniture; joins lines into paragraphs on a vertical-gap
threshold; and resolves end-of-line hyphenation (`сло-` + `во` → `слово`).

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

## Known behaviours and pitfalls

- **It assumes single-column, reflowable prose.** Given a two-column scan it interleaves the
  columns, because it sorts by vertical position. Tables and lists are why `DocBlock` is
  expected to grow.
- **A `0f` or `NaN` font size must never vote in the median.** PDFBox reports 0 pt for Type3
  fonts and degenerate text matrices; letting zeros count drags the median below the body
  size and turns ordinary prose into headings. `NaN` is the same trap from the other side —
  every comparison against it is false. Both are filtered out.
- **A median, not a mean**, so a handful of headings and page numbers cannot hide the very
  headings being looked for.

## Build

```bash
./gradlew :sdk:structure:jvmTest
./gradlew :sdk:structure:allTests
```
