# engine-tesseract — Claude Instructions

## Module Purpose

Tesseract behind the `core` contracts, on Android (Tesseract4Android) and the JVM
(Tess4J/JNA against a **system** libtesseract). No iOS target — there, `engine-vision` is
the engine.

## Package Layout

```
source/engine/     tesseractEngine, TESSERACT_ENGINE_ID, defaultTessDataProvider
source/install/    installTesseractEngine, TesseractEngineInitializer (androidMain)
source/options/    TesseractPageSegmentation
source/language/   SystemTessDataProvider, ClasspathTessDataProvider, plus   (jvmMain)
core/recognizer/   the two recognizers
core/native/       TessHandle, JnaLibraryPath                                (jvmMain)
core/language/     AndroidTessDataProvider                                   (androidMain)
core/cancel/       orElseOnFailure, runReportingFailure
```

## Rules That Have Already Cost Time

- **Do not route the JVM path through Tess4J's `getWords`.** `TessHandle` talks to
  libtesseract's C API directly because `getWords` goes via `lept4j`, whose bundled bindings
  are pinned to a Leptonica ABI and die with `symbol not found: pixFindBaselinesGen` against
  Homebrew's Leptonica 1.85. This is not an accident to be simplified away.
- **`SetSourceResolution` comes *after* `SetImage`,** or libtesseract ignores it and
  estimates the dpi. Pass `Raster.sourceDpi` — the effective dpi — and pass **nothing** when
  it is `null`, rather than asserting a number nobody rendered at.
- **The datapath convention differs by binding.** The Tesseract4Android wrapper appends
  `tessdata/` itself and wants the parent; Tess4J wants the `tessdata` directory. The
  provider returns the parent and the JVM engine descends.
- **`close()` takes a *blocking* lock.** `AutoCloseable.close()` cannot take a suspending
  mutex, and freeing a handle underneath a running `TessBaseAPIRecognize` is a SIGSEGV, not
  an exception. Android calls `stop()` first to cut the work short. Do not "fix" the block.
- **`kotlin.runCatching` is banned here too**, and this module must not depend on `:sdk:runtime`
  — use `orElseOnFailure` / `runReportingFailure` from `core/cancel/`.
- **Both engines init with `OEM_LSTM_ONLY`.** `RecognitionQuality` has no honest mapping;
  the combined mode needs legacy data neither `tessdata_fast` nor `tessdata_best` ships, so
  asking for it fails at init instead of running faster.

## Strings Nothing Checks

`TesseractEngineInitializer` is named as a string in `androidMain/AndroidManifest.xml` and in
`consumer-rules/*.pro`. The keep rules for the JNI classes are a **correctness** requirement:
without them a release build strips the classes libtesseract calls back into. A green debug
build proves nothing — check a release APK.

## Capability Honesty

The JVM column is richer than the Android one (per-line language, orientation, skew) because
the C API exposes what the Java wrapper does not. Report that difference; do not fake it.
`capabilities(options)` is optimistic by design — the truth after I/O is
`TextRecognizer.capabilities`.

## Verify

```bash
./gradlew :sdk:engine-tesseract:build
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:android-app:assembleRelease     # R8 + the keep rules
```

The JVM path needs `brew install tesseract tesseract-lang`.
