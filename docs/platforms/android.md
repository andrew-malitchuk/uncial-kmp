# Android

Uncial on Android runs Tesseract through Tesseract4Android, rasterizes with the framework's
`PdfRenderer`, and needs no initialization code from the consumer.

## Requirements

| | |
|---|---|
| `minSdk` | **24** |
| `compileSdk` of the SDK modules | 36 |
| Kotlin JVM target | 11 |
| Native ABIs | whatever Tesseract4Android 4.8.0 bundles — `libtesseract.so` ships inside its AAR |

`compileSdk` propagates to consumers through AAR metadata, so the SDK stays on 36
deliberately rather than forcing everyone onto the newest API level.

## Startup: the SDK initializes itself

There is no `Uncial.init(this)`. Two `androidx.startup` initializers do the work, and both
are declared in the SDK's own manifests, so they merge into the consumer's app without any
wiring:

| Initializer | Module | What it does |
|---|---|---|
| `UncialContextInitializer` | `uncial-core` | Captures the **application** `Context` into `UncialContext` |
| `TesseractEngineInitializer` | `uncial-engine-tesseract` | Registers the Tesseract factory in `OcrEngineRegistry` |

`TesseractEngineInitializer.dependencies()` returns `UncialContextInitializer`, so ordering
is guaranteed: the context is in place before the engine that needs it is registered.
Registration itself only stores a factory — nothing loads `libtesseract` or reads
`.traineddata` until an extraction starts — so this costs no measurable startup time.

Adding the dependency really is all that is required on Android:

```kotlin
implementation("io.github.andrew-malitchuk:uncial-runtime:<version>")
implementation("io.github.andrew-malitchuk:uncial-engine-tesseract:<version>")
implementation("io.github.andrew-malitchuk:uncial-lang-ukr:<version>")
```

### Why `core` owns the `Context`

Both `engine-tesseract` (to find `filesDir` and open the language artifacts' assets) and
`pdf-text` (PDFBox-Android) need an application context. Putting the holder in `core`
means one initializer and one `startup-runtime` dependency (~15 KB, declared `api` because
it contributes a `ContentProvider` to the merged manifest) instead of one per module.

Holding an application context in a process-wide object leaks nothing: it outlives every
consumer of it.

## If `InitializationProvider` is not in your merged manifest

Some apps strip `androidx.startup`'s provider — usually to shave a content provider off
cold start, sometimes with `tools:node="remove"`. When that happens the SDK gets neither a
context nor a registered engine, and extraction fails with an `OcrError.EngineInit` whose
message says exactly that.

Both halves have a public escape hatch. Do both from `Application.onCreate`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        UncialContext.install(this)   // replaces UncialContextInitializer
        installTesseractEngine()      // replaces TesseractEngineInitializer
    }
}
```

Both are idempotent. `UncialContext.install` normalizes to the application context itself,
so passing any `Context` is safe. `UncialContext.get()` returns `null` when neither the
initializer nor `install` ran — that `null` is what the engine's lazy language-data provider
turns into the diagnostic above.

!!! note "A third option"
    If you cannot supply a context at all, pass your own `LanguageDataProvider` to the
    `UncialClient` builder. The Android default provider is the only part of the engine
    that needs a `Context`.

## R8 and consumer ProGuard rules

Every SDK module's `consumer-rules/*.pro` is packaged **inside its AAR** by the
`uncial.target.android` convention plugin, so a consumer's release build gets the right
keeps automatically — nothing to copy into your own `proguard-rules.pro`.

What travels, and why:

| Module | Keeps |
|---|---|
| `uncial-core` | `UncialContextInitializer` — named as a string in the merged manifest and instantiated reflectively, so R8 sees no reference to it |
| `uncial-engine-tesseract` | `com.googlecode.tesseract.android.**`, `com.googlecode.leptonica.android.**`, any class with `native <methods>`, and `TesseractEngineInitializer` |
| `uncial-pdf-text` | `-dontwarn com.gemalto.jp2.**` (PDFBox-Android's optional JPEG-2000 decoder), the PDFBox font classes it resolves reflectively, and `-dontwarn` for the `javax.imageio` / `java.awt` names it never reaches |

The Tesseract keeps are a correctness requirement, not an optimization hint: without them a
minified build strips the classes JNI calls back into, and OCR fails with an
`UnsatisfiedLinkError` that points nowhere useful.

!!! warning "Only a release build proves the rules"
    `./gradlew :samples:android-app:assembleRelease` is the build that runs R8. A debug
    build tells you nothing about whether the keeps are sufficient.

## `Bitmap` lifetime

`Raster` on Android is backed by an `android.graphics.Bitmap`:

```kotlin
val raster = Raster(bitmap, sourceDpi = 300)   // does not copy
val document = ocr.extract(raster).getOrThrow()
raster.release()                               // recycles the bitmap
```

The rules:

- **Wrapping does not take ownership.** `Raster(bitmap)` keeps the reference; the bitmap is
  recycled only when you call `release()`.
- **`release()` calls `Bitmap.recycle()`**, and skips an already-recycled bitmap. Using the
  raster afterwards is not supported.
- **A raster the pipeline creates, the pipeline releases** — immediately after the page is
  recognized, not at the end of the document. A 200 dpi A4 page is ~5 MB, and holding 300
  of them is not survivable on a phone. Android is the one platform where this genuinely
  matters; the JVM's `release()` is a no-op.
- `OcrOptions.rasterColor` defaults to `Grayscale` for the same reason: a quarter of the
  memory of ARGB, and OCR engines binarize anyway.

### Host unit tests cannot make a `Bitmap`

`Bitmap` is a stub in `android.jar`, so `Bitmap.createBitmap` throws *not mocked* in a host
(JVM) unit test. That is why `core` exposes `placeholderRaster(width, height)` — a raster
with dimensions and no pixels — and why `FakePageRasterizer` hands those out. Reading
`Raster.bitmap` on a placeholder throws `IllegalStateException`.

Use the top-level `blankRaster(width, height)` from `uncial-engine-fake` instead when you are
testing a real `TextRecognizer` and the pixels actually matter. On Android it creates a real
white `ARGB_8888` bitmap, so it belongs in an instrumented test, not a host one.

## `close()` blocks

`UncialClient.close()` releases the recognizer, and the Android recognizer takes a blocking
`ReentrantLock` around its native calls: `AutoCloseable.close()` cannot take a suspending
mutex, and freeing a `TessBaseAPI` handle underneath a running recognition is a SIGSEGV,
not an exception. So closing waits out the work in flight.

The Android recognizer calls `TessBaseAPI.stop()` **before** queueing for that lock, which
makes the in-flight page abandon its work early — `close()` from `ViewModel.onCleared()`
would otherwise wait out a multi-second page on the main thread, which is an ANR.

!!! warning "Do not call `close()` on the main thread without expecting a stall"
    `stop()` shortens the wait; it does not remove it. Close from a background dispatcher
    if an extraction may be running.

Closing twice is safe. After closing, every `extract` fails with `OcrError.Unsupported`.

## Language data

The `uncial-lang-*` artifacts ship their models as Android assets. AGP merges a library's
assets into the consumer's APK, so adding `uncial-lang-ukr` puts
`assets/tessdata/ukr.traineddata` in the app. Tesseract cannot read an asset stream — it
needs a real directory — so `AndroidTessDataProvider` copies what it finds into `filesDir`
once (via a `.part` temp file and a rename, so a half-copied 3.8 MB model is never mistaken
for a complete one) and reuses it afterwards.

Full details, including the runtime downloader and bring-your-own models, are in
[Language data](../language-data.md).
