# iOS

Uncial on iOS runs Apple Vision for recognition and PDFKit for rasterization and for the
digital text layer. Both are system frameworks, so `uncial-engine-vision` has **zero
third-party dependencies** and iOS needs no language data at all — the models ship with the
OS.

## Targets and deployment floor

The SDK builds for `iosArm64`, `iosSimulatorArm64` and `iosX64`.

No Gradle file pins a deployment target; the floor is the project's product decision of
**iOS 15**, which is what the sample app records
(`IPHONEOS_DEPLOYMENT_TARGET = 15.0`). Two consequences of that floor show up immediately:

- Vision has no Ukrainian before iOS 16 (see below).
- `NavigationStack` is iOS 16, which is why the sample uses a plain `VStack`. Expect that
  class of paper cut for anything SwiftUI.

## Registering the engine

iOS has no equivalent of `androidx.startup`, so unlike Android this step is required — once,
early:

```kotlin
installVisionEngine()
```

It registers the Vision factory in `OcrEngineRegistry` and is idempotent. From Swift, call
`UncialBootstrap.shared.start()`, which does the same thing.

`visionEngine()` returns the factory directly if you want to pass it to the
`UncialClient` builder explicitly instead of going through the registry. Its
`isAvailable()` is unconditionally `true`: Vision is part of the OS, so if the app runs,
the engine is there.

## The `:dist:ios-framework` umbrella

An iOS consumer cannot assemble a set of Gradle modules the way an Android consumer can —
they get one binary. `:dist:ios-framework` is that binary:

- **`Uncial.framework`**, `isStatic = true`. Nothing to embed-and-sign, no dSYM dance, and
  the linker drops what the app does not call.
- It `export`s `model`, `core`, `structure`, `runtime`, `engine-vision` and `pdf-text`.
  Without `export`, Swift sees those types as opaque and cannot name `OcrDocument` or
  `DocBlock` at all.
- This is the one place the engine-isolation rule stops applying. `runtime` must never see
  an engine, but a *distribution* artifact is exactly where they are allowed to meet.

## `UncialBootstrap` — the Swift-facing surface

Kotlin's generated Objective-C API is usable but not pleasant: `ByteArray` arrives as
`KotlinByteArray`, the `UncialClient { }` builder becomes a lambda-taking top-level function
on a `…Kt` class, and `Result` does not survive the trip at all. `UncialIos` — exported as
**`UncialBootstrap`** via `@ObjCName` — is the minimum that makes iOS code readable, and
doubles as a statement of what the planned `:sdk:swift` module has to cover.

```swift
UncialBootstrap.shared.start()

let client = UncialBootstrap.shared.createClient(
    languages: [OcrLanguage.Companion.shared.Ukrainian, OcrLanguage.Companion.shared.English],
    renderDpi: 200,
    includeWords: false,
    preferDigitalLayer: true,
    onLog: { message in print("uncial \(message)") }
)

let document = try await UncialBootstrap.shared.extract(client: client, pdf: data)
let blocks = UncialBootstrap.shared.reconstruct(document: document)
```

| Member | Purpose |
|---|---|
| `start()` | `installVisionEngine()`. Call once at launch; idempotent. |
| `registeredEngines()` | `List<String>` of registered engine ids, for diagnostics. |
| `createClient(languages:renderDpi:includeWords:preferDigitalLayer:onLog:)` | Builds a `UncialClient` **with the PDFKit digital text-layer fast path already wired in** — on iOS that path costs nothing in binary size, so there is no reason not to. `onLog` is a `((String) -> Unit)?`; pass `null` to discard diagnostics. |
| `extract(client:pdf:)` | Takes `NSData` (so Swift can hand over a `Data` straight from `Bundle` or a file) and **throws** rather than returning a `Result`. `try await` in Swift. |
| `extract(client:image:)` | The image path: a `UIImage` from the camera or the photo library, recognized with no PDF involved. Redraws the image upright first — see below — and releases the raster it builds. |
| `reconstruct(document:)` | `DocumentStructure.reconstruct` — turns a recognized document into headings and paragraphs. |

!!! note "Objective-C export drops default arguments"
    Every parameter of `createClient` must be passed from Swift, even the ones that have
    Kotlin defaults. That is a limitation of the export, not a design choice.

`UncialClient` itself also offers `extractOrThrow(bytes:)` / `extractOrThrow(raster:)`,
annotated `@Throws` so they arrive in Swift as `try await`.

## Vision returns zero observations on the Simulator

The Simulator has no Neural Engine. `performRequests` succeeds, `error` is `null`, and
`results` is **empty**. This is verified against the framework, not inferred.

!!! warning "A green Simulator run proves nothing about recognition"
    The iOS tests assert the pipeline and the capability reporting, and only check text when
    Vision actually returned some. Recognition quality can only be verified on hardware —
    where this SDK has been confirmed working, recognizing Ukrainian from the bundled scan
    and reporting `uk-UA+en-US`.

## Ukrainian needs iOS 16, and the language list is per request

Vision gained Ukrainian in text-recognition revision 3, which is iOS 16. On iOS 15 the
request simply does not offer `uk-UA`, so a Ukrainian document recognizes as garbage Latin
rather than failing.

The engine therefore asks Vision at runtime — `supportedRecognitionLanguagesAndReturnError`
— which languages it can do, drops the rest, and reports what is left through
`OcrCapabilities.languages`.

That query is answered **per request, not statically**, and the answer depends on how the
request is configured: Apple's header states that a language supported at one recognition
level might not be available at another, and Ukrainian is accurate-level-only. So the
probe is built with the same `recognitionLevel` the real request will use. A probe
configured differently would report `uk-UA` under `RecognitionQuality.Fast` and then quietly
recognize Latin. See [Capabilities](capabilities.md).

`engineVersion` is reported as `vision-<revision>`, read from the request rather than
hardcoded. Note what that revision is: the one this *build* defaults to — Apple defines it
as the latest for the SDK the app was linked against — not necessarily the newest the
running OS could offer. It is still the honest answer, because the recognizer leaves the
revision alone too, so it is the revision recognition actually runs at.

## `UIImage` orientation

A `CGImage` is pixels and nothing else. The rotation a camera recorded lives on the
`UIImage` as `imageOrientation`, and `VNImageRequestHandler` here is constructed from the
`CGImage`, so it never sees it — a portrait photo is recognized sideways, which usually
means recognized as nothing.

`UncialBootstrap.extract(client:image:)` takes the `UIImage` and redraws it upright before
wrapping it, which bakes the orientation into the pixels. A `Raster` you build yourself from
`uiImage.CGImage` does not get that for free: either go through `UncialBootstrap`, or redraw
the image first.

## `CGImage` ownership

`Raster` on iOS is backed by a `CGImageRef`:

```kotlin
val raster = Raster(uiImage.CGImage!!, sourceDpi = 300)
val document = ocr.extract(raster).getOrThrow()
raster.release()
```

`CGImageRef` is a plain `CPointer` in Kotlin/Native, **not** an Objective-C object, so
nothing about it is under ARC: holding one in a Kotlin field neither retains it nor keeps
its owner alive. A `CGImage` taken from a `UIImage` that then goes out of scope is a
use-after-free waiting for the GC.

`Raster` therefore does the retain/release itself:

- The constructor calls `CGImageRetain`, so the raster keeps the image alive for its own
  lifetime regardless of what happens to the `UIImage` it came from.
- `release()` calls `CGImageRelease` and clears the backing pointer. It is idempotent, and a
  no-op for a `placeholderRaster`, which never had an image.
- Reading `Raster.image` after `release()` — or on a placeholder — throws
  `IllegalStateException` rather than handing back a dangling pointer.

!!! warning "Release every raster you create"
    A raster the pipeline produced is released by the pipeline, immediately after the page
    is recognized. One you constructed yourself is yours to release; the retain guarantees
    it will not be freed early, which also means it will not be freed at all until you say
    so.
