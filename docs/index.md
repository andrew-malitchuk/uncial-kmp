# Uncial

<p align="center">
  <img src="https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20JVM-informational" alt="Platforms"/>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform"/>
  <img src="https://img.shields.io/badge/status-pre--release-orange" alt="Status"/>
</p>

## Overview

**Uncial** is an on-device OCR SDK for Kotlin Multiplatform that turns a PDF or a raster
image into a **structured document** — pages, lines and words, each with a box and a
confidence, plus `Heading` and `Paragraph` blocks — rather than a blob of text.

Everything happens on the device. There is no networking in the recognition path, no
camera, and no UI: Uncial is a library, not a scanner app.

It was built for Ukrainian, which is the reason it exists at all — ML Kit does not
recognize it, so an off-the-shelf answer was not available.

```kotlin
val client = UncialClient { languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English) }
val document = client.extract(pdfBytes).getOrThrow()

println(document.text)
println("${document.pageCount} pages, ${document.lines.size} lines")
```

## What you get

- **Structure, not a string.** Pages → lines → words, each carrying a bounding box and a
  confidence. `DocumentStructure` turns that into headings and paragraphs.
- **The digital fast path.** A PDF that already carries its text layer is read directly,
  roughly a thousand times faster than rasterizing and recognizing it — and exactly, not
  approximately. A scan whose cover page was OCR'd by the scanner comes back as `Mixed`.
- **Images, not just PDFs.** Hand it a `Bitmap`, a `BufferedImage` or a `CGImage` and skip
  the PDF machinery entirely.
- **One coordinate model.** Top-down pixels, origin at the page's top-left, on every
  platform. Vision reports bottom-up normalized and PDF is bottom-up in points; each engine
  converts at its own edge so no caller ever has to.
- **Progress and cancellation.** `extractAsFlow` reports per page and honours cancellation,
  which is what makes a 300-page document something a UI can host.
- **Honest capabilities.** The platforms genuinely differ. Uncial reports the difference
  instead of hiding it.

## Platform asymmetry is reported, not hidden

Read `UncialClient.capabilities` rather than assuming the platforms are uniform:

| | words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|---|
| Tesseract (JVM) | yes | yes | **yes** | **yes** | **yes** |
| Tesseract (Android) | yes | yes | no | no | no |
| Vision (iOS) | yes¹ | yes | no | no | no |

¹ derived from `boundingBoxForRange`, not native.

The JVM column is richer because libtesseract's C API exposes things the Tesseract4Android
*Java wrapper* does not — a real asymmetry between two bindings of the same engine, and
precisely why the capability type exists.

See [Capabilities](platforms/capabilities.md) for the full matrix, including the iOS
version floors and why Vision's language list depends on the recognition level.

## Getting started

- [Installation](getting-started/installation.md) — what to depend on, and the current
  publishing status
- [Quick Start](getting-started/quick-start.md) — the smallest thing that works, per
  platform
- [Language Data](language-data.md) — bundled, system, downloaded, or bring your own

!!! warning "Not published yet"
    Uncial is pre-release. Nothing is on Maven Central and nothing is in `mavenLocal`;
    consuming it today means building from source. See the
    [Roadmap](roadmap.md) for what is and is not done.

## Architecture in one paragraph

Rasterization and recognition are separate roles — `PageRasterizer` and `TextRecognizer` —
which is what makes image input, engine swapping and PNG-based tests possible at all. The
runtime has **no compile-time dependency on any engine**: engines announce themselves
through `OcrEngineRegistry`, automatically on Android via `androidx.startup` and explicitly
elsewhere. See the [Architecture overview](architecture/overview.md).
