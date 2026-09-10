# engine-vision

Apple Vision as an Uncial engine, on **iOS only**.

## Features

- **Zero third-party dependencies**: Vision is a system framework and its models ship with the OS, so there is no `.traineddata` to bundle, download or manage on iOS.
- **Runtime language negotiation**: the engine asks Vision which languages it can actually recognize and drops the rest, reporting what survived through `OcrCapabilities.languages`.
- **Honours `RecognitionQuality`**: it maps to Vision's real `VNRequestTextRecognitionLevel`, and to `usesLanguageCorrection`.
- Targets: iosArm64, iosSimulatorArm64, iosX64. Depends on `core` (`api`) only.

## Core Components

- `visionEngine(): OcrEngineFactory` — the `expect`/`actual` factory.
- `installVisionEngine()` — registers it in `OcrEngineRegistry`. Idempotent. iOS has no `androidx.startup`, so unlike Android **this step is required**; `UncialBootstrap.start()` in the iOS umbrella framework calls it for you.
- `VISION_ENGINE_ID` — `"vision"`.

`isAvailable()` is always `true`: Vision is part of the OS, so if the app runs the engine is there. `engineVersion` is reported as `vision-<revision>` — the request revision this build defaults to, which is also the revision recognition really runs at, since Uncial deliberately pins none.

## Capabilities

| words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|
| yes¹ | yes | no | no | no |

¹ Vision has no native word results. The engine derives them by asking `boundingBoxForRange` for each whitespace-separated span of the recognized text.

Coordinates are converted at this edge: Vision reports normalized values with a bottom-left origin, and the engine turns them into Uncial's top-down page pixels so no caller has to.

Recognition is serialized per recognizer with a mutex. There is no native handle to protect — the serialization exists so peak memory stays a function of page size rather than of document length, since each in-flight request pins a full raster plus Vision's own buffers.

## Usage

```kotlin
installVisionEngine()
val ocr = UncialClient { languages = OcrLanguage.Default }
```

From Swift, via the umbrella framework: `UncialBootstrap.shared.start()`.

!!! warning "Ukrainian needs iOS 16"
    Vision gained Ukrainian in text-recognition revision 3. On iOS 15 the request simply does
    not offer `uk-UA`, so a Ukrainian document recognizes as Latin nonsense rather than
    failing. The engine drops what it cannot do and reports the rest — read
    `UncialClient.capabilities` instead of assuming your requested languages took effect.

!!! warning "Language support is per request, not static"
    Apple's own header says a language available at one recognition level may be absent at
    another, and Ukrainian is accurate-level-only. The capability probe is therefore
    configured exactly as the real request will be; asking with `RecognitionQuality.Fast`
    genuinely reports a smaller language set.

!!! warning "The iOS Simulator recognizes nothing"
    With no Neural Engine, `performRequests` succeeds, `error` is null and `results` is empty.
    A green simulator test run proves the pipeline and the capability reporting, and nothing
    at all about recognition quality — only a device does.
