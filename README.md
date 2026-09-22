<p align="center">
  <img src="assets/img_logo.png" alt="Uncial" width="160"/>
</p>

# Uncial

<p align="center">
  <img src="https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20JVM-informational" alt="Platforms"/>
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Multiplatform"/>
  <img src="https://img.shields.io/badge/status-pre--release-orange" alt="Status"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License"/>
</p>

## Overview

**Uncial** is an on-device OCR SDK for Kotlin Multiplatform that turns a PDF or a raster image into a **structured document** — pages, lines and words, each with a box and a confidence, plus `Heading` and `Paragraph` blocks — rather than a blob of text.

Everything happens on the device. There is no networking in the recognition path, no camera, and no UI: Uncial is a library, not a scanner app. It runs Tesseract on Android and the JVM and Apple Vision on iOS, behind one API, and it **reports the differences between them** instead of pretending the platforms are equal.

It was built for Ukrainian, which is the reason it exists at all — ML Kit does not recognize it, so an off-the-shelf answer was not available.

Full documentation: **[andrew-malitchuk.github.io/uncial-kmp](https://andrew-malitchuk.github.io/uncial-kmp/)**

## Quick Start

```kotlin
val ocr = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    renderDpi = 200
}

// One call, typed failures — no exception escapes as a surprise.
val document: OcrDocument = ocr.extract(pdfBytes).getOrThrow()

// Or page by page, with progress and cancellation.
ocr.extractAsFlow(pdfBytes).collect { progress ->
    when (progress) {
        is OcrProgress.Started -> ui.showTotal(progress.pageCount)
        is OcrProgress.Page -> ui.update(progress.fraction)
        is OcrProgress.Done -> render(progress.document)
        else -> Unit
    }
}

// Geometry back to semantics — optional, and a separate artifact.
DocumentStructure.reconstruct(document).forEach { block ->
    when (block) {
        is DocBlock.Heading -> println("#".repeat(block.level) + " " + block.text)
        is DocBlock.Paragraph -> println(block.text)
        else -> Unit
    }
}
```

On Android the engine registers itself through `androidx.startup`; everywhere else call `installTesseractEngine()` or `installVisionEngine()` once.

## Core Features

* **Structure, not a string:** pages → lines → words, each with a `BoundingBox` and a `Confidence`, plus reconstructed headings and paragraphs.
* **Digital fast path:** a PDF that already carries a text layer is read, not recognized — exact, and roughly a thousand times faster. A document where only the cover was scanned comes back as `ExtractionSource.Mixed`.
* **Image input:** camera frames and gallery picks go straight in as a `Raster`, with no PDF machinery involved.
* **Progress and cancellation:** `extractAsFlow` emits per page and honours structured concurrency — cancelling throws `CancellationException`, never a swallowed error.
* **Honest capabilities:** `UncialClient.capabilities` states what the engine on *this* platform can actually do, including which requested languages it had to drop.
* **Language data, four ways:** bundled artifacts, the system Tesseract installation, a runtime download with pinned checksums, or bring your own `.traineddata`.
* **Engine-agnostic runtime:** the runtime has no compile-time dependency on any engine; a third-party engine is a first-class citizen.
* **Testable by design:** `uncial-engine-fake` is published, so a consumer's unit tests never need Tesseract installed.

## Functional Capabilities

| Feature | Description |
|:---|:---|
| **PDF extraction** | Rasterize and recognize, or read the embedded text layer when there is one. |
| **Image extraction** | Recognize a `Raster` built from a `Bitmap`, `BufferedImage` or `CGImage`. |
| **Document structure** | Median-type-size heading detection, paragraph joining, hyphenation repair, running-header and page-number removal. |
| **Word-level boxes** | Per-word geometry and confidence, where the engine supports it. |
| **Coordinates** | One model everywhere: top-down pixels, origin at the page's top-left — each engine converts at its own edge. |
| **Typed errors** | A sealed `OcrError` (`NoLanguageData`, `RenderFailed`, `EngineInit`, `Unsupported`, `InvalidInput`, `RecognitionFailed`), never `null` plus a printed reason. |
| **Page ranges and quality** | `OcrOptions` covers dpi, `maxPageSide`, page range, colour, recognition quality, word inclusion — validated at construction. |
| **Language data download** | `uncial-lang-download` fetches models at runtime and verifies SHA-256 before they reach the native layer. |
| **Swift interop** | An iOS umbrella framework plus `UncialBootstrap`, which hides `Result`, `Flow` and `ByteArray` — none of which survive Obj-C export. |

## Platform Capabilities

The asymmetry is reported, not hidden — read `UncialClient.capabilities` at runtime:

| | words | confidence | per-line language | orientation | skew |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Tesseract (JVM)** | yes | yes | **yes** | **yes** | **yes** |
| **Tesseract (Android)** | yes | yes | no | no | no |
| **Vision (iOS)** | yes¹ | yes | no | no | no |

¹ Derived from `boundingBoxForRange`, not native. The JVM column is richer because libtesseract's C API exposes what the Tesseract4Android *Java wrapper* does not — a real asymmetry between two bindings of the same engine. Vision also has no Ukrainian before iOS 16, and the engine drops what it cannot do rather than substituting silently.

## Technical Specifications

| Aspect | Details |
|:---|:---|
| **Framework** | Kotlin Multiplatform 2.4.20 — no UI framework, no DI container, no networking |
| **Targets** | `android`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `iosX64` |
| **Engines** | Tesseract4Android (Android), Tess4J + JNA against system libtesseract (JVM), Apple Vision (iOS) |
| **Rasterizers** | `PdfRenderer` (Android), PDFKit (iOS), PDFBox (JVM) |
| **Digital text layer** | PDFBox-Android, PDFKit, PDFBox — optional, in its own artifact |
| **Concurrency** | kotlinx-coroutines 1.11.0; every extraction is suspending and cancellable |
| **Public API** | `explicitApi()` strict, with a committed ABI dump per module (`checkKotlinAbi` runs as part of `build`) |
| **Build System** | Gradle 9.7.1 with class-based convention plugins in `build-logic/convention` |
| **Toolchain** | JDK 21 toolchain, JVM target 11, AGP 9.3.0 |
| **Floors** | Android minSdk 24, iOS 15 |
| **Tests** | 172 tests, run across jvm + androidHostTest + iosSimulatorArm64 (394 executions) |

## Architecture

Twelve published modules with one entry point and a deliberately thin contract layer:

```
┌─────────────────────────────────────────────────┐
│           runtime  (UncialClient)               │  ← the only entry point
├─────────────────────────────────────────────────┤
│        core  (contracts: engine, raster,        │  ← what everything plugs into
│        recognizer, language, log)               │
├─────────────────────────────────────────────────┤
│  model  (pages, lines, words, boxes, errors)    │  ← the vocabulary
├──────────────┬──────────────┬───────────────────┤
│  raster      │  pdf-text    │  structure        │  ← optional capabilities
├──────────────┴──────────────┴───────────────────┤
│  engine-tesseract │ engine-vision │ engine-fake │  ← reached via the registry only
├─────────────────────────────────────────────────┤
│  lang-ukr │ lang-eng │ lang-download            │  ← language data
├─────────────────────────────────────────────────┤
│  dist:ios-framework   →   Uncial.xcframework    │  ← distribution
└─────────────────────────────────────────────────┘
```

Two rules hold the graph together:

- **The runtime never names an engine.** Engines announce themselves through `OcrEngineRegistry`; `runtime`'s dependency list has no edge to any of them. The one place that rule stops applying is `:dist:ios-framework`, because an iOS consumer gets one binary rather than a module graph.
- **Rasterization is not recognition.** `PageRasterizer` and `TextRecognizer` are separate roles meeting at `Raster`, which is what makes image input, PNG-based tests and engine swapping possible.

**Module breakdown:**
- **`sdk/model`** — the vocabulary every other module's signatures are written in; Kotlin stdlib only
- **`sdk/core`** — engine, rasterizer, recognizer, language-data and logging contracts
- **`sdk/runtime`** — `UncialClient`, its builder and `OcrProgress`
- **`sdk/raster`**, **`sdk/pdf-text`**, **`sdk/structure`** — rasterization, the digital text layer, and structure reconstruction
- **`sdk/engine-*`** (3 modules) — Tesseract, Vision, and a deterministic fake
- **`sdk/lang-*`** (3 modules) — bundled Ukrainian and English models, and the runtime downloader
- **`dist/ios-framework`** — the iOS umbrella, XCFramework and Swift package
- **`samples/`** — a JVM CLI, an Android Compose app, an iOS SwiftUI app
- **`build-logic/convention`** — the Gradle convention plugins

Every module carries its own `README.md` and `CLAUDE.md`.

## Installation & Deployment

> **Status: prepared, not published.** All 12 modules produce signed, Central-valid artifacts and the aggregated bundle builds, but nothing has been uploaded yet. Until then, consume the SDK from `mavenLocal()` or as an included build. See [PUBLISHING.md](PUBLISHING.md).

### Prerequisites

* JDK 21 (the build pins its daemon via `gradle/gradle-daemon-jvm.properties`)
* Android Studio Ladybug or newer, for the Android sample
* Xcode 16+, for the iOS sample
* `brew install tesseract tesseract-lang`, for the JVM path only

### Build Instructions

```bash
# Clone the repository
git clone https://github.com/andrew-malitchuk/uncial-kmp.git

# Everything: compile, test, ABI check
./gradlew build

# Tests on jvm + androidHostTest + iosSimulatorArm64
./gradlew allTests

# Publish to ~/.m2 for local consumption
./gradlew publishToMavenLocal

# Run OCR for real, on the JVM
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"

# The Android sample — R8 on, which is the build that proves the consumer rules
./gradlew :samples:android-app:assembleRelease
```

For iOS, open `samples/ios-app/UncialSample.xcodeproj` in Xcode and run — its first build phase links the framework for you.

### Android / JVM — Maven coordinates (once published)

```kotlin
dependencies {
    implementation("io.github.andrew-malitchuk:uncial-runtime:<version>")
    implementation("io.github.andrew-malitchuk:uncial-engine-tesseract:<version>")  // Android / JVM
    implementation("io.github.andrew-malitchuk:uncial-lang-ukr:<version>")          // language data
    implementation("io.github.andrew-malitchuk:uncial-structure:<version>")         // optional
    implementation("io.github.andrew-malitchuk:uncial-pdf-text:<version>")          // optional
}
```

`uncial-engine-tesseract`'s POM references `tesseract4android`, which is published on
JitPack only — add its repository, scoped to `com.github.adaptech-cz`, or resolution fails.

### iOS — Swift Package Manager (once released)

In Xcode: *File → Add Package Dependencies…*, then the repository URL:

```
https://github.com/andrew-malitchuk/uncial-kmp.git
```

"Up to Next Major" from `<version>`. Then:

```swift
import Uncial

UncialBootstrap.shared.start()
let client = UncialBootstrap.shared.createClient(...)
```

`Package.swift` at the repository root is a binary target pinned by SHA-256 to a zipped
XCFramework attached to the matching GitHub Release — SwiftPM downloads and verifies it, no
Gradle or Kotlin toolchain needed on the consumer side. `uncial-engine-vision` is Apple
Vision under the hood, bundled into the framework; there is no separate engine artifact to
add. The `samples/ios-app/UncialSample.xcodeproj` build phase that links the framework
directly is for developing *inside this repository* — a real consumer always goes through
the Swift package.

## Roadmap

- [x] Module scaffolding, convention plugins, strict public API with ABI dumps
- [x] Suspend + `Flow` contracts, sealed errors, rasterizer/recognizer split
- [x] Language data: bundled artifacts, `androidx.startup`, runtime downloader
- [x] Maven Central and Swift package machinery, verified locally
- [ ] Golden corpus and a character-error-rate harness
- [ ] First release to Maven Central
- [ ] Fat AAR, so an Android consumer needs one dependency rather than four
- [ ] Dokka API reference
- [ ] `:sdk:swift` — a hand-written Swift layer over the exported Obj-C API

## Feedback & Feature Requests

This started as a tool for a specific problem: Ukrainian documents that ML Kit will not read. If you need a language, a layout or a platform that is not covered, open an issue and let's discuss it.

## Troubleshooting

| Problem | Solution |
|:---|:---|
| `isAvailable()` returns `false` on the JVM | No system libtesseract. `brew install tesseract tesseract-lang`, then re-run `:samples:cli:run --args="capabilities"`. |
| `OcrError.NoLanguageData` | The provider returns the **parent** of `tessdata/`. Add `uncial-lang-ukr` / `-eng`, or point a `LanguageDataProvider` at your own models. |
| Ukrainian comes back as Latin nonsense on iOS | Vision has no Ukrainian before iOS 16, and none at `RecognitionQuality.Fast`. Read `capabilities` rather than assuming. |
| No text at all from a camera photo | Apply EXIF orientation before building the `Raster`; a sideways page under `PSM_AUTO` yields nothing, not garbage. |
| Recognition works in debug, fails in a release APK | Consumer R8 rules travel inside each AAR — verify with `assembleRelease`, not `installDebug`. |
| The iOS Simulator recognizes nothing | It has no Neural Engine. Vision returns zero observations there; only a device proves recognition. |
| Gradle sync issues | JDK 25 is not supported by AGP/KGP. The build pins JDK 21; set `JAVA_HOME` if a tool bypasses that. |

## Contributing

Contributions are welcome. Please follow the standard pull request process:

1. Fork the repository.
2. Create a feature branch.
3. Submit a PR with a detailed description of changes.

Before submitting: `./gradlew build` must pass, and an intentional API change needs `./gradlew updateKotlinAbi` with the diff reviewed. Each module's `CLAUDE.md` documents the invariants that module expects you to keep.

---

Built with Kotlin Multiplatform, Tesseract, Apple Vision, PDFBox and PDFKit.

## License

Apache 2.0 License

```
Copyright (c) [2026] [Andrew Malitchuk]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
