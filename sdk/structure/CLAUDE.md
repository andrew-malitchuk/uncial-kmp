# structure — Claude Instructions

## Module Purpose

Turns recognized geometry into `DocBlock`s — headings and paragraphs. A pure function over
an `OcrDocument`, 100 % `commonMain`.

## Package Layout

```
source/reconstruction/DocumentStructure.kt   the entry point
source/options/StructureOptions.kt           every threshold
core/builder/BlockBuilder.kt                 internal assembly
core/fixture/                                test fixtures (commonTest)
```

## Rules

- **No platform code, no coroutines, no I/O.** If a change here needs any of those, it is in
  the wrong module.
- **Every threshold goes in `StructureOptions`,** with a default and an `init` check. A
  number written into `DocumentStructure` is a number nobody can tune and no test can vary.
- **Never let `0f` or `NaN` vote in the median.** PDFBox reports 0 pt for Type3 fonts and
  degenerate text matrices; zeros drag the median below the body size and turn prose into
  headings. `NaN` fails every comparison, so it sails past ratio tests. Both are filtered.
- **Median, not mean.** A mean lets headings and page numbers hide the headings being
  looked for.
- **Page furniture detection is frequency-based and needs `minPagesForChromeDetection`.** Do
  not run it on a two-page document; and keep the digit-normalized key, or
  `Chapter 3 — page 41` and `… page 42` stop counting as one running head.
- **Paragraph gaps are meaningful only within a page.** Across a page break, assume the
  paragraph continues.
- **New heuristics need fixtures.** `core/fixture/` builds documents with real boxes; a test
  that passes on a document with plausible geometry is worth something, one built from
  `BoundingBox.Zero` is not.

## Known Limits (do not "fix" silently)

Single-column reflowable prose only. A two-column scan interleaves, because sorting is by
vertical position. Tables and lists are why `DocBlock` is expected to grow — that growth
belongs in `model`, and it breaks exhaustive `when`s.

## Verify

```bash
./gradlew :sdk:structure:jvmTest        # fastest loop
./gradlew :sdk:structure:allTests :sdk:structure:checkKotlinAbi
```
