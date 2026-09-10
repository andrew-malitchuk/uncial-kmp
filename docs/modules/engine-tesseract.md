# engine-tesseract

Tesseract as an Uncial engine, on **Android and the JVM only**.

## Features

- **The same engine on two platforms**, so an Android result and a JVM result of the same scan agree — which is what makes the JVM the practical place to run a corpus.
- **Self-registering on Android**: an `androidx.startup` initializer puts the engine into `OcrEngineRegistry` before `Application.onCreate`, so adding the dependency is all that is required.
- **Honest availability**: the JVM binding needs a *system* `libtesseract`, and `isAvailable()` says so instead of letting an `UnsatisfiedLinkError` escape from inside JNA.
- Targets: android + jvm. No iOS target exists for this module — there, `engine-vision` is the engine.
- Depends on `core` (`api`), plus Tesseract4Android on Android and Tess4J/JNA on the JVM.

## Core Components

- `tesseractEngine(pageSegmentation = TesseractPageSegmentation.Auto): OcrEngineFactory` — the `expect`/`actual` factory.
- `installTesseractEngine(pageSegmentation = …)` — registers it. Idempotent, and unnecessary on Android unless the app strips `InitializationProvider` from its merged manifest.
- `TESSERACT_ENGINE_ID` — `"tesseract"`.
- `TesseractPageSegmentation`: `Auto`, `AutoWithOrientation`, `SingleBlock`, `SingleLine`, `SingleWord`, `SparseText`. Engine-specific knobs live on the engine rather than in `OcrOptions`; this is the single most effective lever on Tesseract's accuracy, and a receipt or a label recognizes far better as `SingleLine` than as `Auto`.
- `defaultTessDataProvider(): LanguageDataProvider` — the app's assets on Android; on the JVM `SystemTessDataProvider() + ClasspathTessDataProvider()`.

### JVM only

- `SystemTessDataProvider(extraSearchPaths)`: finds `.traineddata` where the OS keeps it — `TESSDATA_PREFIX` (in either of its two historical meanings), `/opt/homebrew/share`, `/usr/local/share`, `/usr/share`. Exposes `dataPath`.
- `ClasspathTessDataProvider(cacheDirectory, classLoader)`: unpacks models shipped as classpath resources by the `uncial-lang-*` artifacts into a cache directory, because Tesseract cannot read a resource stream. Defaults to `~/.cache/uncial/tessdata-<user>`.
- `LanguageDataProvider.plus(fallback)`: chains providers — "use what the OS already has, fall back to what we ship". Since Tesseract takes a single datapath, `materialize` picks the provider covering the most languages rather than splitting them.

### Android only

- `TesseractEngineInitializer`: the `androidx.startup` initializer, declared to depend on `UncialContextInitializer`. Registration only stores a factory — nothing loads `libtesseract` or reads `.traineddata` until an extraction starts.

The AAR ships consumer R8 rules keeping the Tesseract and Leptonica JNI classes, any class with native methods, and the initializer. These are a correctness requirement, not an optimisation hint: without them a release build strips the very classes libtesseract calls back into.

## Capabilities

| | words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|---|
| JVM (Tess4J / libtesseract C API) | yes | yes | **yes** | **yes** | **yes** |
| Android (Tesseract4Android) | yes | yes | no | no | no |

A real asymmetry between two bindings of the *same* engine: libtesseract's C API reports the recognition language, page orientation and deskew angle per element, and the Tesseract4Android Java wrapper's `ResultIterator` exposes only text, box and confidence. Where `perLineLanguage` is `false`, use `OcrLine.script`, which is derived from the text and therefore always available.

`capabilities(options)` is optimistic — it reports what the engine *would* do. Which languages are really available needs I/O, so read `TextRecognizer.capabilities` after `create()` for the truth.

## Usage

```kotlin
installTesseractEngine()                 // JVM; automatic on Android
val ocr = UncialClient { languages = OcrLanguage.Default }
```

On macOS the JVM path needs `brew install tesseract tesseract-lang`. See [Language data](../language-data.md) for the bundled, system, downloaded and bring-your-own options.

!!! note "`RecognitionQuality` is ignored here"
    Tesseract's only equivalent is `OEM_TESSERACT_LSTM_COMBINED`, which needs legacy data
    that neither `tessdata_fast` nor `tessdata_best` ships — asking for it fails at init
    instead of running faster. Both engines initialise with `OEM_LSTM_ONLY`.

!!! warning "The JVM binding talks to libtesseract's C API directly"
    Not through Tess4J's `getWords`, which routes via `lept4j` — whose bundled bindings are
    pinned to a Leptonica ABI and die with `symbol not found: pixFindBaselinesGen` against
    Homebrew's Leptonica 1.85. This is deliberate; do not "simplify" it back.

!!! note "Tell it the real resolution"
    The JVM recognizer passes `Raster.sourceDpi` to `SetSourceResolution` — Tesseract's
    layout heuristics take it as an input. A raster you built from your own image reports
    `null` there, and the engine then lets Tesseract estimate rather than asserting a dpi
    nobody rendered at. Construct rasters through `PageRasterizer` when the number matters.

!!! warning "Closing a recognizer blocks"
    `close()` takes a blocking native lock so a handle is never freed underneath work in
    flight. Android calls `stop()` first to cut a page short; the JVM waits out the native
    call in progress.
