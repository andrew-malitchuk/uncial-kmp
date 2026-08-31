# samples/cli — Claude Instructions

## Module Purpose

The JVM harness: the only way to run recognition end to end on a developer machine, with no
emulator and no device. It is also where fixtures are generated, and the practical home for a
golden corpus.

## Package Layout

```
source/entrypoint/Main.kt    argument dispatch, nothing else
core/command/                CapabilitiesCommand, FixtureCommand, OcrCommand, DownloadCommand
core/fixture/                ScannedPdf, DigitalPdf — the generators
core/report/                 printing
core/args/                   argument parsing
core/log/                    ConsoleLogger — errors to stderr, everything else to stdout
```

A new command is a file in `core/command/` plus a branch in `Main.kt`. `application.mainClass`
names `…samples.cli.source.entrypoint.MainKt` **as a string** — moving or renaming `Main.kt`
breaks the run task and nothing catches it at compile time.

## Rules

- **`fixture` must keep generating an image-only PDF by default.** A normally generated PDF
  carries its text, so Uncial takes the digital fast path and proves nothing about
  recognition. The scanned fixture rasterizes text into an image and puts *the image* in the
  PDF — that is the input the SDK exists for. `--text` is the opposite case; `--ascii` forces
  Helvetica and English for a small, byte-stable file.
- **Keep the fixture pages structurally interesting**: a large heading, a subheading, body
  text, a repeated running header and a page number, so `DocumentStructure` has something
  real to reconstruct.
- **`java.awt.headless` stays set.** The generator draws through AWT and would otherwise
  reach for a display server — harmless locally, a crash on CI.
- **No `catch (Throwable)` around an extraction.** That is the banned `runCatching` in
  another shape: it reports a cancelled or superseded run as a failure.
- **Print what the SDK reports, not what we hope.** `capabilities` exits non-zero when no
  engine is available; the report prints `not reported` rather than `0` for unknown
  confidence. A sample that flattered the SDK would be useless for diagnosing it.
- **Consume the modules a real consumer would**, in the same shape: `runtime` + an engine +
  optional `pdf-text`, with `lang-*` as `runtimeOnly`.

## Verify

```bash
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"
```

Needs `brew install tesseract tesseract-lang`.
