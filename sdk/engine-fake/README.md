# engine-fake

> A deterministic engine and rasterizer, so a consumer can unit-test code that calls Uncial without running OCR.

Gradle `:sdk:engine-fake` · Maven `io.github.andrew-malitchuk:uncial-engine-fake` · [full docs](../../docs/modules/engine-fake.md)

## Responsibility

Published on purpose. Without it, testing "what does my screen do when OCR returns two
pages" means installing Tesseract, supplying language data, and tolerating recognition drift
between machines. `FakeOcrEngine` and `FakePageRasterizer` together drive a real
`UncialClient` end to end with no I/O, no PDF and no engine — and they lay lines and words
out with plausible boxes, so `DocumentStructure` tests do not pass for the wrong reason.

## Layout

```
source/engine/   FakeOcrEngine, FAKE_ENGINE_ID
source/raster/   FakePageRasterizer, blankRaster, FakeRaster.{android,ios,jvm}
```

No `core/`: the module has no internal declarations.

## Dependencies

`api(:sdk:core)` only.

## Public API

| Declaration | Notes |
|---|---|
| `FakeOcrEngine(text, defaultText, capabilities, failOnPage, available, delayPerPageMillis)` | every knob is a constructor parameter |
| `FakeOcrEngine.DefaultCapabilities` | claims words and confidence; override it to test an engine without them |
| `FAKE_ENGINE_ID` | `"fake"` |
| `FakePageRasterizer(pageCount, pageWidth, pageHeight, failOnPage)` | defaults ≈ A4 at 200 dpi; the bytes handed to `open` are ignored |
| `blankRaster(width, height)` | a blank white raster with **real pixels**, for driving a real `TextRecognizer` |

`text` maps page index to the text that page recognizes as, one line per `\n`; a blank
`defaultText` simulates an image-only page OCR could not read. `delayPerPageMillis` is free
under `runTest`, which makes progress and cancellation testable.

## Usage

```kotlin
val client = UncialClient {
    engine = FakeOcrEngine(text = mapOf(0 to "Розділ 1", 1 to "Текст сторінки"))
    rasterizer = FakePageRasterizer(pageCount = 2)
}
```

Passing the engine to the builder bypasses `OcrEngineRegistry` entirely, so a test needs no
registration and cannot be disturbed by one.

## Known behaviours and pitfalls

- **`FakePageRasterizer` hands out pixel-free placeholders.** Android host unit tests cannot
  create a `Bitmap` — it is a stub in `android.jar` and `createBitmap` throws `not mocked` —
  so the fake uses `placeholderRaster`, which carries dimensions and no pixels. Use
  `blankRaster` when the pixels matter, and expect it to need a device on Android.

## Build

```bash
./gradlew :sdk:engine-fake:build
```
