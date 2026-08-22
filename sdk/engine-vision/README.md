# engine-vision

> Apple Vision as an Uncial engine, on **iOS only**.

Gradle `:sdk:engine-vision` · Maven `io.github.andrew-malitchuk:uncial-engine-vision` · [full docs](../../docs/modules/engine-vision.md)

## Responsibility

Binds `VNRecognizeTextRequest` to the `core` contracts. Vision is a system framework and its
models ship with the OS, so on iOS there is no `.traineddata` to bundle, download or manage —
this module has zero third-party dependencies and the `lang-*` artifacts have no iOS target
at all.

It also converts coordinates at this edge: Vision reports normalized values with a
bottom-left origin, and the engine turns them into Uncial's top-down page pixels so no caller
has to.

## Layout

```
source/engine/      visionEngine (expect/actual), VISION_ENGINE_ID
source/install/     installVisionEngine
core/recognizer/    VisionRecognizer        (iosMain)
core/language/      VisionLanguageTag
core/fixture/       SamplePdf               (iosTest)
```

## Dependencies

`api(:sdk:core)` only. Targets: iosArm64, iosSimulatorArm64, iosX64.

## Public API

| Declaration | Notes |
|---|---|
| `visionEngine()` | the `expect`/`actual` factory |
| `installVisionEngine()` | idempotent — and **required**: iOS has no `androidx.startup` |
| `VISION_ENGINE_ID` | `"vision"` |

`isAvailable()` is always `true`: if the app runs, Vision is there. `engineVersion` reports
`vision-<revision>` — the request revision recognition really runs at, since Uncial pins none.

### Capabilities

| words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|
| yes¹ | yes | no | no | no |

¹ Vision has no native word results; the engine derives them from `boundingBoxForRange` for
each whitespace-separated span.

Recognition is serialized per recognizer with a mutex. There is no native handle to protect —
the serialization keeps peak memory a function of page size rather than document length.

## Usage

```kotlin
installVisionEngine()
val ocr = UncialClient { languages = OcrLanguage.Default }
```

From Swift, through the umbrella framework: `UncialBootstrap.shared.start()`.

## Known behaviours and pitfalls

- **Ukrainian needs iOS 16.** On 15 the request simply does not offer `uk-UA`, so a Ukrainian
  document recognizes as Latin nonsense rather than failing. The engine drops what it cannot
  do — read `UncialClient.capabilities` instead of assuming your languages took effect.
- **Language support is per request, not static.** Apple's own header says a language
  available at one recognition level may be absent at another, and Ukrainian is
  accurate-level-only, so the probe is configured exactly as the real request will be.
  `RecognitionQuality.Fast` genuinely reports a smaller set.
- **The iOS Simulator recognizes nothing.** No Neural Engine: `performRequests` succeeds,
  `error` is null, `results` is empty. A green simulator run proves the pipeline and the
  capability reporting, and nothing about recognition quality — only a device does.

## Build

```bash
./gradlew :sdk:engine-vision:iosSimulatorArm64Test
```
