# samples/cli

> The JVM harness — the only way to exercise recognition end to end on a developer machine, with no emulator and no device.

Gradle `:samples:cli` · not published

## Responsibility

Runs the whole SDK on the JVM: PDFBox rasterizing, the system `libtesseract` recognizing,
`pdf-text` taking the fast path when a PDF carries its own text, and `structure`
reconstructing the result. Tesseract behaves here the way it does on Android, which is what
makes this the practical home for a golden corpus.

It also **generates its own fixtures**, so a check needs no sample document lying around.

## Requirements

A system Tesseract. On macOS:

```bash
brew install tesseract tesseract-lang
```

Without it the engine reports itself unavailable — honestly, through `isAvailable()`, rather
than letting an `UnsatisfiedLinkError` escape from inside JNA.

## Layout

```
source/entrypoint/   main
core/command/        capabilities, fixture, ocr, download
core/fixture/        ScannedPdf, DigitalPdf — the generators
core/report/         the printed output
core/args/           argument parsing
core/log/            an OcrLogger — errors to stderr, everything else to stdout
```

## Dependencies

`:sdk:runtime`, `:sdk:structure`, `:sdk:engine-tesseract`, `:sdk:pdf-text`,
`:sdk:lang-download`, with `:sdk:lang-ukr` and `:sdk:lang-eng` as `runtimeOnly`. PDFBox is
used by the fixture generator only.

## Commands

```bash
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"        # image-only PDF
./gradlew :samples:cli:run --args="fixture /tmp/text.pdf --text --pages 2" # with a text layer
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"
./gradlew :samples:cli:run --args="download /tmp/scan.pdf --cache /tmp/td"
```

- **`capabilities`** — the engine, its version, the languages it actually got, and whether it
  can do word boxes, confidence, per-line language, orientation and skew. Exits non-zero when
  no engine is available.
- **`fixture <out.pdf> [--pages N] [--text] [--ascii]`** — generates a test document.
- **`ocr <file.pdf>`** — extracts and prints source, page count, line count, confidence range,
  per-language line counts, scripts, orientation, skew, elapsed time, then the reconstructed
  `Heading` / `Paragraph` structure. Flags: `--dpi N`, `--words`, `--no-digital`, `--quiet`.
- **`download <file.pdf> [--cache DIR]`** — the same as `ocr`, but fetches `.traineddata` over
  the network into an empty cache instead of using the system Tesseract data. This is what
  proves the runtime-download path.

Running with no arguments prints the usage text.

## Known behaviours and pitfalls

- **`fixture` generates an image-only PDF on purpose.** A normally generated PDF carries its
  text, so Uncial takes the digital fast path and proves nothing about recognition. The
  scanned fixture rasterizes its Ukrainian text into an image and puts *the image* in the PDF
  — the input the SDK actually exists for. `--text` generates the opposite case, and
  `--ascii` forces Helvetica and English for a small, byte-stable file that does not depend
  on the machine's fonts.
- **The generated pages carry a large heading, a subheading, body text, a repeated running
  header and a page number**, so `DocumentStructure` has something real to reconstruct.
- **`java.awt.headless` is set** for every `JavaExec`: the fixture generator draws through
  AWT, which would otherwise try to reach a display server and crash a CI runner.

## Build

```bash
./gradlew :samples:cli:installDist    # a launcher script, if you would rather not go through Gradle
```
