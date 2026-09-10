# ios-framework

The iOS umbrella (`:dist:ios-framework`): one static `Uncial.framework` containing everything an iOS app needs.

## Features

- **One binary instead of a module graph.** An iOS consumer cannot assemble Gradle modules the way an Android one can, so this module bundles and re-exports them.
- **Static**: nothing to embed-and-sign, no dSYM dance, and the linker drops what the app does not call.
- **The one place the engine-isolation rule stops applying.** `runtime` must never see an engine; a *distribution* artifact is exactly where they are allowed to meet.
- Targets: iosArm64, iosSimulatorArm64, iosX64.

## Core Components

### The framework

`baseName = "Uncial"`, re-exporting `model`, `core`, `structure`, `runtime`, `engine-vision` and `pdf-text`. Without `export`, Swift would see these as opaque types and could not name `OcrDocument` or `DocBlock` at all.

### `UncialIos`, exported to Swift as `UncialBootstrap`

A small facade over the roughest edges of the generated Obj-C API — `ByteArray` (which Swift cannot hand a `Data` to), the builder lambda, and `Result`, which does not survive export at all:

- `start()` — registers the Vision engine. Call once at launch; iOS has no `androidx.startup`, so this cannot be automatic. Idempotent.
- `registeredEngines(): List<String>` — engine ids currently registered, for diagnostics.
- `createClient(languages, renderDpi, includeWords, preferDigitalLayer, onLog)` — builds a `UncialClient` with the PDFKit digital fast path already wired in, since on iOS it costs nothing in binary size. `onLog` is a `(String) -> Unit` or `null`.
- `extract(client, pdf: NSData, onProgress:): OcrDocument` — suspending and `@Throws`, so Swift gets `try await` and an `NSData` parameter instead of a `KotlinByteArray`. `onProgress` is how `extractAsFlow` reaches Swift at all, since a `Flow` does not export; it is called on whichever thread the pipeline is on, so a caller updating UI hops to the main actor itself.
- `extract(client, image: UIImage, onProgress:): OcrDocument` — the image path, for a photo from the camera or the photo library. Takes a `UIImage` because `Raster`'s own parameter is a `CGImageRef`, which is a bare `CPointer` in Kotlin/Native and exports as an untyped pointer — from Swift that means `Unmanaged.passUnretained(_:)`. It also redraws the image upright first: `CGImage` does not carry `imageOrientation`, and Vision is handed the `CGImage`, so a camera photo would otherwise be recognized sideways. The raster it builds is released for you; the `UIImage` is untouched.
- `reconstruct(document): List<DocBlock>` — `DocumentStructure.reconstruct`.

This is the seed of the planned `:sdk:swift` module, and doubles as a statement of what that module has to cover.

## Usage

```swift
UncialBootstrap.shared.start()
let client = UncialBootstrap.shared.createClient(...)
let document = try await UncialBootstrap.shared.extract(
    client: client, pdf: data, onProgress: { progress in /* … */ }
)
let blocks = UncialBootstrap.shared.reconstruct(document: document)
```

The sample Xcode project is not a Gradle module: its first build phase runs Gradle to link this framework and stages `Uncial.framework` where `FRAMEWORK_SEARCH_PATHS` points, so the search path needs no per-configuration conditionals.

## The XCFramework and the Swift package

The same module produces what SwiftPM consumes. `updateSwiftPackage` runs three steps in
order and is the only command a release needs:

```bash
./gradlew :dist:ios-framework:updateSwiftPackage
```

`assembleUncialReleaseXCFramework` is KGP's own task — it builds all three iOS slices,
`lipo`s the two simulator architectures into one and runs `xcodebuild -create-xcframework`.
`zipXcframework` packs the result reproducibly, and `updateSwiftPackage` writes that zip's
SHA-256 and its release-asset URL into `Package.swift` at the repository root. The manifest
is generated: editing it by hand is undone by the next run.

Two properties of SwiftPM decide the release order. The manifest is read **at the git tag**,
so it has to be committed before tagging — and therefore before the GitHub Release it names
exists. And the checksum pins the exact bytes, so the uploaded zip must be the one that was
hashed. `PUBLISHING.md` §5 has the full sequence.

The sample stays on the direct framework build phase rather than on the package: a binary
target is pinned to a published zip by checksum and cannot serve a development loop.

!!! note "Obj-C export drops default arguments"
    Swift passes every parameter of `createClient`, even the ones Kotlin defaults. That is
    one of the things `:sdk:swift` is meant to hide.
