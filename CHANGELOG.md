# Changelog

All notable changes to Uncial are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.1] - 2026-09-22

First public release. An on-device OCR SDK for Kotlin Multiplatform (Android / iOS / JVM)
that turns a PDF or a raster image into a structured document — pages, lines, words, boxes,
confidence, and reconstructed `Heading` / `Paragraph` blocks.

### Added

- **Core model** — the OCR document model: pages, lines, words, geometry, confidence, and
  the language/error types built on top of them.
- **Engine abstraction** — `OcrEngineRegistry` so engines announce themselves without the
  runtime taking a compile-time dependency on any of them, plus the per-platform page
  rasterizers that keep rasterization and recognition as separate roles.
- **Document structure reconstruction** — headings and paragraphs rebuilt from line geometry,
  with its thresholds exposed through `StructureOptions`.
- **PDF text extraction** — the digital fast path that reads a PDF's own text layer instead
  of rasterizing and recognizing it, scaled by `OcrOptions.renderScale` so digital and OCR'd
  pages are directly comparable.
- **Tesseract engine** for Android and the JVM, with per-line language, orientation and skew
  reported on the JVM binding.
- **Apple Vision engine** for iOS.
- **A deterministic fake engine** for tests.
- **`UncialClient`** and the extraction pipeline, plus runtime progress reporting.
- **Bundled Ukrainian and English language data** (`uncial-lang-ukr`, `uncial-lang-eng`), and
  **`uncial-lang-download`** for fetching and checksumming additional `.traineddata` at
  runtime.
- **`:dist:ios-framework`** — the iOS umbrella static framework re-exporting every module,
  with `UncialBootstrap` smoothing over the roughest edges of the generated Obj-C API.
- **Samples** — an Android app (five-screen design system, no tab bars) and a JVM CLI
  harness, both exercising the scanned-fixture, digital-fixture, PDF-picker, camera-photo and
  gallery-image paths; the CLI also generates the fixtures themselves.
- **Swift Package Manager support** — a generated `Package.swift` at the repository root,
  pinned by SHA-256 to a zipped XCFramework.
- **12 modules published to Maven Central** under `io.github.andrew-malitchuk`: `model`,
  `core`, `structure`, `raster`, `runtime`, `engine-tesseract`, `engine-vision`,
  `engine-fake`, `pdf-text`, `lang-ukr`, `lang-eng`, `lang-download`. Every artifact is
  signed, carries sources and javadoc jars, and ships an `explicitApi()` surface with a
  committed ABI dump.

### Known limitations

- **`uncial-engine-tesseract`'s POM references `tesseract4android`, which is published on
  JitPack only.** A consumer must add JitPack's repository, scoped to
  `com.github.adaptech-cz`, or resolution fails. A Fat AAR that removes this requirement is
  planned for a future release.
- **Platform capabilities are asymmetric and reported, not hidden** — read
  `UncialClient.capabilities`. Per-line language, orientation and skew are available on the
  JVM Tesseract binding only; Android's Tesseract4Android wrapper and iOS Vision report words
  and confidence but not those three. Vision also has no Ukrainian before iOS 16.
- **iOS Simulator returns zero Vision observations** — there is no Neural Engine to recognize
  text with. Verified working on hardware; the Simulator proves the pipeline and UI only.
- **Dokka-generated javadoc jars are not yet wired up** — each artifact ships an empty
  javadoc jar to satisfy Central's validation.

[0.0.1]: https://github.com/andrew-malitchuk/uncial-kmp/releases/tag/v0.0.1
