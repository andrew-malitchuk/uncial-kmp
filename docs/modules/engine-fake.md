# engine-fake

A deterministic engine and rasterizer, so a consumer can unit-test code that calls Uncial without running OCR.

## Features

- **Published on purpose**: without it, testing "what does my screen do when OCR returns two pages" means installing Tesseract, supplying language data, and tolerating recognition drift between machines.
- **No I/O, no PDF, no engine**: `FakeOcrEngine` and `FakePageRasterizer` together drive a real `UncialClient` end to end.
- **Plausible geometry**: the fake lays lines and words out with real boxes, so `DocumentStructure` tests do not pass for the wrong reason.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64. Depends on `core` (`api`) only.

## Core Components

### `FakeOcrEngine`

An `OcrEngineFactory` configured entirely through its constructor:

- `text`: page index to the text that page recognizes as, one line per `\n`.
- `defaultText`: what a page with no entry produces (`FakeOcrEngine.DEFAULT_PAGE_TEXT`). Blank simulates an image-only page that OCR could not read.
- `capabilities`: what the engine claims. `FakeOcrEngine.DefaultCapabilities` claims words and confidence; override it to test how your code handles an engine without them.
- `failOnPage`: makes `recognize` throw `OcrError.RecognitionFailed` for that index.
- `available`: what `isAvailable()` returns, for the "no engine" path.
- `delayPerPageMillis`: an artificial per-page `delay`, for progress and cancellation tests — free under `runTest`.

`FAKE_ENGINE_ID` is `"fake"`.

### `FakePageRasterizer`

`pageCount`, `pageWidth`, `pageHeight` (defaults `DEFAULT_WIDTH` 1654 × `DEFAULT_HEIGHT` 2339, roughly A4 at 200 dpi) and `failOnPage`. The bytes handed to `open` are ignored entirely — pass `ByteArray(0)`.

### `blankRaster(width, height)`

A blank white raster with **real pixels**, for testing a real `TextRecognizer` that needs something to look at.

## Usage

```kotlin
val client = UncialClient {
    engine = FakeOcrEngine(text = mapOf(0 to "Розділ 1", 1 to "Текст сторінки"))
    rasterizer = FakePageRasterizer(pageCount = 2)
}
```

Passing the engine to the builder bypasses `OcrEngineRegistry` entirely, so a test needs no registration and cannot be disturbed by one.

!!! note "`FakePageRasterizer` hands out pixel-free placeholders"
    Android host unit tests cannot create a `Bitmap` — it is a stub in `android.jar` and
    `Bitmap.createBitmap` throws `not mocked`. The fake rasterizer therefore uses
    `placeholderRaster`, which carries dimensions and no pixels. Use `blankRaster` instead
    when the pixels actually matter, and expect it to need a device or an emulator on Android.
