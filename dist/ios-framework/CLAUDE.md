# ios-framework — Claude Instructions

## Module Purpose

The iOS umbrella: one static `Uncial.framework` re-exporting every module an iOS app needs,
plus `UncialIos` — exported to Swift as `UncialBootstrap` — which hides the roughest edges of
the generated Obj-C API. It is also the seed of the planned `:sdk:swift` module.

## Package Layout

```
source/bootstrap/UncialIos.kt   the façade — one-line delegations only
source/model/                   IosDocumentSummary, IosPageSummary, IosRunProgress
core/client/                    IosClientFactory
core/extraction/                ProgressCollector
core/interop/                   NSDataConversion, UIImageOrientation
core/summary/                   OcrDocumentSummarizing
```

**`UncialIos` is a façade on purpose.** A Kotlin `object` cannot span files, and splitting it
into several objects would rename the Swift API — so every member is a one-line delegation
and the body lives in `core/`. Do not inline a body back into the object, and do not split
the object.

## Rules

- **This is the one place the engine-isolation rule stops applying.** `runtime` must never
  see an engine; a *distribution* artifact is where they are allowed to meet.
- **Dependencies are `api`, not `implementation`** — a dependency can only be `export`ed if
  it is part of this module's own API surface. Without `export`, Swift sees the types as
  opaque and cannot name `OcrDocument` or `DocBlock` at all.
- **Every exported type carries `@ObjCName(exact = true)`,** which is what makes Kotlin
  package moves invisible to Swift. Renaming an `@ObjCName` is a breaking change for every
  Swift caller, including the sample.
- **Three things do not survive Obj-C export**, and each is why a member here exists:
  `Result` (so the API throws), `Flow` (so progress is an `onProgress` callback), and
  `ByteArray` (so PDFs arrive as `NSData`). Adding a member that returns any of them defeats
  the module.
- **`Raster` takes a `CGImageRef`, which exports as an untyped pointer** — hence
  `extract(client:image:)` taking a `UIImage`. It redraws the image upright first, because
  `CGImage` does not carry `imageOrientation` and Vision is handed the `CGImage`; a camera
  photo would otherwise recognize sideways. The raster it builds is released in a `finally`;
  the caller's `UIImage` is untouched.
- **Nothing rescales a caller-supplied image.** `renderDpi` and `maxPageSide` govern
  rasterization, and nothing was rasterized — a 48 MP photo is recognized at 48 MP unless
  Swift downsizes first. Say so in the KDoc rather than silently adding a resize.
- **Obj-C export drops default arguments**, so Swift passes every parameter. Keep parameter
  lists short for that reason.
- **Unannotated Obj-C signatures type as non-null but really return null** (`UIImage.CGImage`
  is the live example). Assign to an explicitly nullable local so the guard is not dead code
  the compiler warns about.

## Package.swift Is Generated

```bash
./gradlew :dist:ios-framework:updateSwiftPackage
```

Editing `Package.swift` by hand is undone by the next run. The release order is dictated by
SwiftPM: the manifest is read *at the git tag*, so it is committed before tagging — before
the GitHub Release it names exists — and the checksum pins the exact bytes, so the uploaded
zip must be the one that was hashed. `PUBLISHING.md` §5 has the sequence. Keep
`zipXcframework` reproducible (`isPreserveFileTimestamps = false`, `isReproducibleFileOrder = true`).

## Verify

```bash
./gradlew :dist:ios-framework:assemble :dist:ios-framework:checkKotlinAbi
cd samples/ios-app && xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

The sample is the only consumer that proves the exported API is usable from Swift — a green
Gradle build does not.
