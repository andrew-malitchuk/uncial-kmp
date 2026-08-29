# ios-framework

> The iOS umbrella: one static `Uncial.framework` containing everything an iOS app needs.

Gradle `:dist:ios-framework` · not published to Maven — it is the input to an XCFramework · [full docs](../../docs/modules/ios-framework.md)

## Responsibility

An iOS consumer cannot assemble a set of Gradle modules the way an Android one can: they get
one binary. This module bundles and re-exports `model`, `core`, `structure`, `runtime`,
`engine-vision` and `pdf-text` under `baseName = "Uncial"`, and adds a small Swift-facing
facade over the roughest edges of the generated Obj-C API.

**This is the one place the engine-isolation rule stops applying.** `runtime` must never see
an engine; a *distribution* artifact is exactly where they are allowed to meet.

## Layout

```
source/bootstrap/   UncialIos          — exported to Swift as UncialBootstrap
source/model/       IosDocumentSummary, IosPageSummary, IosRunProgress
core/client/        IosClientFactory
core/extraction/    ProgressCollector
core/interop/       NSDataConversion, UIImageOrientation
core/summary/       OcrDocumentSummarizing
```

`UncialIos` is a façade on purpose: a Kotlin `object` cannot span files, and splitting it into
several objects would rename the Swift API. Every member is a one-line delegation and the
bodies live in `core/`. Package moves are invisible to Swift — every exported type carries
`@ObjCName(exact = true)`.

## Dependencies

`api` on all six exported modules. `api`, not `implementation`: a dependency can only be
exported if it is part of this module's own API surface.

## Public API (as Swift sees it)

| Declaration | Notes |
|---|---|
| `start()` | registers the Vision engine. iOS has no `androidx.startup`, so this cannot be automatic. Idempotent |
| `registeredEngines()` | engine ids currently registered, for diagnostics |
| `createClient(languages, renderDpi, includeWords, preferDigitalLayer, onLog)` | the PDFKit digital fast path is already wired in — on iOS it costs nothing in binary size |
| `extract(client:pdf:onProgress:)` | suspending and `@Throws`, taking `NSData` rather than a `KotlinByteArray`. In Swift, `try await` |
| `extract(client:image:onProgress:)` | takes a `UIImage` and redraws it upright first — `CGImage` does not carry `imageOrientation`, so a camera photo would otherwise be recognized sideways. The `Raster` it builds is released for you |
| `summarize(document:)` | because `Confidence` is a value class and Swift cannot do arithmetic on one |
| `IosRunProgress` | what `onProgress` receives — `extractAsFlow`'s `OcrProgress`, flattened for export |
| `reconstruct(document:)` | `DocumentStructure.reconstruct` |

## Usage

```swift
UncialBootstrap.shared.start()
let client = UncialBootstrap.shared.createClient(...)
let document = try await UncialBootstrap.shared.extract(
    client: client, pdf: data, onProgress: { progress in /* … */ }
)
let blocks = UncialBootstrap.shared.reconstruct(document: document)
```

## The XCFramework and the Swift package

```bash
./gradlew :dist:ios-framework:updateSwiftPackage
```

`assembleUncialReleaseXCFramework` (KGP's own task: three slices, `lipo` of the two simulator
architectures, `xcodebuild -create-xcframework`) → `zipXcframework` (reproducible: no
timestamps, stable file order) → `updateSwiftPackage`, which writes the release-asset URL and
the zip's SHA-256 into `Package.swift` at the repository root.

`Package.swift` is **generated**; editing it by hand is undone by the next run. Two properties
of SwiftPM drive the release order: the manifest is read *at the git tag*, so it must be
committed before tagging and therefore before the GitHub Release it names exists; and the
checksum pins the exact bytes, so the uploaded zip must be the one that was hashed.
`PUBLISHING.md` §5 has the sequence.

## Known behaviours and pitfalls

- **Static, not dynamic**: nothing to embed-and-sign, no dSYM dance, and the linker drops what
  the app does not call.
- **Without `export`, Swift sees the types as opaque** and cannot name `OcrDocument` or
  `DocBlock` at all.
- **Obj-C export drops default arguments**, so Swift passes every parameter of `createClient`.
  That is one of the things the planned `:sdk:swift` module is meant to hide.
- **The iOS sample stays on the direct framework build phase**, not on the Swift package: a
  binary target is pinned to a published zip by checksum and cannot serve a dev loop.

## Build

```bash
./gradlew :dist:ios-framework:assemble
```
