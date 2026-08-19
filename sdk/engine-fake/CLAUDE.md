# engine-fake — Claude Instructions

## Module Purpose

A deterministic engine and rasterizer so a consumer can unit-test code that calls Uncial
without running OCR. Published — it is part of the SDK's DX, not a test fixture.

## Package Layout

```
source/engine/   FakeOcrEngine, FAKE_ENGINE_ID
source/raster/   FakePageRasterizer, blankRaster, FakeRaster.{android,ios,jvm}
```

No `core/`: everything here is the surface.

## Rules

- **It is published, so its API is public API.** Renaming a constructor parameter breaks
  somebody's test suite. Run `./gradlew updateKotlinAbi` and mean it.
- **Configuration goes through the constructor, not through mutable state.** A fake with
  setters is a fake that leaks between tests.
- **No I/O, no PDF parsing, no engine.** The bytes handed to `open` are ignored on purpose —
  a test should be able to pass `ByteArray(0)`.
- **Keep the geometry plausible.** Lines and words get real boxes, laid out at a real page
  size, so that `DocumentStructure` tests do not pass for the wrong reason. A fake that
  returned `BoundingBox.Zero` would make the structure tests meaningless.
- **`delayPerPageMillis` must stay a `delay`,** not a sleep: under `runTest` it is free, and
  that is what makes progress and cancellation testable.
- **`FakePageRasterizer` hands out `placeholderRaster`** — dimensions, no pixels — because
  Android host unit tests cannot create a `Bitmap` (`createBitmap` throws `not mocked`). Use
  `blankRaster` only where pixels genuinely matter, and expect it to need a device on Android.
- **New knobs mirror a real failure mode.** `failOnPage`, `available`, a stripped
  `capabilities` — each exists so a consumer can test an error path that really happens. Do
  not add knobs that no real engine could produce.

## Verify

```bash
./gradlew :sdk:engine-fake:build :sdk:engine-fake:checkKotlinAbi
./gradlew :sdk:runtime:allTests     # the real consumer of this module
```
