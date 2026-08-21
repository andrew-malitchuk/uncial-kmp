# engine-tesseract

> Tesseract as an Uncial engine, on **Android and the JVM only**.

Gradle `:sdk:engine-tesseract` · Maven `io.github.andrew-malitchuk:uncial-engine-tesseract` · [full docs](../../docs/modules/engine-tesseract.md)

## Responsibility

Binds Tesseract to the `core` contracts on the two platforms that have no system OCR. The
same engine and the same `.traineddata` on both, which is what buys behavioural parity — and
what makes the JVM the practical place to run a corpus against results an Android device
will reproduce.

There is no iOS target: there, `engine-vision` is the engine.

## Layout

```
source/engine/     tesseractEngine (expect/actual), TESSERACT_ENGINE_ID, defaultTessDataProvider
source/install/    installTesseractEngine, TesseractEngineInitializer (androidMain)
source/options/    TesseractPageSegmentation
source/language/   SystemTessDataProvider, ClasspathTessDataProvider, plus   (jvmMain)
core/recognizer/   AndroidTesseractRecognizer, JvmTesseractRecognizer
core/native/       TessHandle, JnaLibraryPath                                (jvmMain)
core/language/     AndroidTessDataProvider                                   (androidMain)
core/cancel/       orElseOnFailure, runReportingFailure
```

## Dependencies

| Scope | Dependency | Why |
|---|---|---|
| `api` | `:sdk:core` | the contracts it implements |
| android | Tesseract4Android | bundles `libtesseract.so`; **JitPack-only** — see the root `settings.gradle.kts` |
| jvm | Tess4J + JNA | binds to the **system** `libtesseract` |

## Public API

| Declaration | Notes |
|---|---|
| `tesseractEngine(pageSegmentation = Auto)` | the `expect`/`actual` factory |
| `installTesseractEngine(pageSegmentation = …)` | idempotent; unnecessary on Android unless the app strips `InitializationProvider` |
| `TESSERACT_ENGINE_ID` | `"tesseract"` |
| `TesseractPageSegmentation` | `Auto`, `AutoWithOrientation`, `SingleBlock`, `SingleLine`, `SingleWord`, `SparseText` — the single most effective lever on accuracy |
| `defaultTessDataProvider()` | app assets on Android; `SystemTessDataProvider() + ClasspathTessDataProvider()` on the JVM |
| `SystemTessDataProvider(extraSearchPaths)` | `TESSDATA_PREFIX` in both historical meanings, Homebrew, `/usr/local/share`, `/usr/share` (JVM) |
| `ClasspathTessDataProvider(cacheDirectory, classLoader)` | unpacks models shipped by `uncial-lang-*`, because Tesseract cannot read a resource stream (JVM) |
| `LanguageDataProvider.plus(fallback)` | "use what the OS already has, fall back to what we ship" |

### Capabilities

| | words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|---|
| JVM (libtesseract C API) | yes | yes | **yes** | **yes** | **yes** |
| Android (Tesseract4Android) | yes | yes | no | no | no |

A real asymmetry between two bindings of the *same* engine: the Java wrapper's
`ResultIterator` exposes only text, box and confidence. Where `perLineLanguage` is false, use
`OcrLine.script`, which is derived from the text and always available.

## Usage

```kotlin
installTesseractEngine()                 // JVM; automatic on Android
val ocr = UncialClient { languages = OcrLanguage.Default }
```

On macOS the JVM path needs `brew install tesseract tesseract-lang`.

## Known behaviours and pitfalls

- **The JVM binding talks to libtesseract's C API directly** (`TessHandle`), not through
  Tess4J's `getWords` — that path goes via `lept4j`, whose bundled bindings are pinned to a
  Leptonica ABI and die with `symbol not found: pixFindBaselinesGen` against Homebrew's
  Leptonica 1.85. Do not "simplify" it back.
- **The datapath convention differs by binding.** The Tesseract4Android wrapper appends
  `tessdata/` itself and wants the parent; Tess4J wants the `tessdata` directory.
- **`SetSourceResolution` must be called after `SetImage`**, and must be told the *effective*
  dpi — `Raster.sourceDpi`, not `OcrOptions.renderDpi`. It is `null` for a caller-supplied
  image, and then Tesseract estimates instead.
- **`RecognitionQuality` is ignored here.** Tesseract's only equivalent needs legacy data
  that neither `tessdata_fast` nor `tessdata_best` ships; both engines init with `OEM_LSTM_ONLY`.
- **`close()` blocks**, taking a blocking native lock so a handle is never freed underneath
  work in flight. Android calls `stop()` first to cut a page short.
- **`consumer-rules/` is packaged into the AAR** and is a correctness requirement, not an
  optimisation hint: without it R8 strips the very classes libtesseract calls back into.

## Build

```bash
./gradlew :sdk:engine-tesseract:build
./gradlew :samples:cli:run --args="capabilities"    # what it actually reports on this machine
```
