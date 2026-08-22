# engine-vision — Claude Instructions

## Module Purpose

Apple Vision behind the `core` contracts, iOS only. Zero third-party dependencies: Vision is
a system framework and its models ship with the OS.

## Package Layout

```
source/engine/      visionEngine, VISION_ENGINE_ID
source/install/     installVisionEngine
core/recognizer/    VisionRecognizer        (iosMain)
core/language/      VisionLanguageTag
core/fixture/       SamplePdf               (iosTest)
```

## Rules

- **Probe languages exactly as the real request will be configured.** Apple's header says a
  language available at one recognition level may be absent at another, and Ukrainian is
  accurate-level-only. A probe with different settings reports `uk-UA` and then quietly
  recognizes Latin. This is the single easiest bug to reintroduce here.
- **Drop what Vision cannot do and report what survived** through `OcrCapabilities.languages`.
  Never silently substitute a language.
- **Convert coordinates at this edge.** Vision reports normalized, bottom-left-origin values;
  everything leaving this module is top-down page pixels. No caller converts anything.
- **Word boxes are derived, not native.** They come from `boundingBoxForRange` over
  whitespace-separated spans — keep `wordLevel` honest about that.
- **Do not pin a request revision.** `engineVersion` reports `vision-<revision>` precisely
  because the build's default is what recognition really runs at.
- **Recognition is serialized with a mutex** even though there is no native handle: it keeps
  peak memory a function of page size rather than document length.
- **`installVisionEngine()` is required on iOS.** There is no `androidx.startup` here. The
  umbrella framework's `UncialBootstrap.start()` calls it; nothing else does.

## Testing on iOS

**The Simulator recognizes nothing** — no Neural Engine, so `performRequests` succeeds,
`error` is null and `results` is empty. Tests therefore assert the pipeline and the
capability reporting, and check text only when Vision returned some. Do not write a test
that fails when the text is empty, and do not conclude from a green simulator run that
recognition works: only a device proves that.

Backtick test names must not contain a comma — Kotlin/Native rejects it while the JVM target
accepts it, so the break only appears when the iOS targets compile.

## Verify

```bash
./gradlew :sdk:engine-vision:iosSimulatorArm64Test
./gradlew :sdk:engine-vision:checkKotlinAbi
```
