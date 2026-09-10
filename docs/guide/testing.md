# Testing with the Fake Engine

Testing "what does my screen do when OCR returns two pages" should not require running
Tesseract, installing language data, and tolerating recognition drift between machines. The
`uncial-engine-fake` module exists so it does not: it gives you a deterministic engine and a
rasterizer that needs no PDF.

Add it as a **test** dependency of the module whose code you are testing, then point a
`UncialClient` at it:

```kotlin
import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.engine.fake.source.engine.FakeOcrEngine
import io.github.andrewmalitchuk.uncial.engine.fake.source.raster.FakePageRasterizer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTest {

    @Test
    fun `renders both pages`() = runTest {
        val client = UncialClient {
            engine = FakeOcrEngine(text = mapOf(0 to "Розділ перший", 1 to "Тіло тексту"))
            rasterizer = FakePageRasterizer(pageCount = 2)
        }

        val document = client.extract(ByteArray(0)).getOrThrow()

        assertEquals(2, document.pageCount)
        assertEquals("Розділ перший", document.pages[0].text)
        client.close()
    }
}
```

Passing `engine` explicitly bypasses `OcrEngineRegistry` entirely, so a test never depends on
what happens to be registered in the process.

!!! note "The bytes are ignored"
    `FakePageRasterizer.open` does not look at the bytes it is given. Pass `ByteArray(0)`.
    There is no PDF, no file, and no I/O anywhere in this setup.

---

## `FakeOcrEngine`

An `OcrEngineFactory` that recognizes whatever you tell it to.

```kotlin
FakeOcrEngine(
    text = emptyMap(),                                  // Map<Int, String>: page index -> text
    defaultText = FakeOcrEngine.DEFAULT_PAGE_TEXT,      // "Uncial fake page"
    capabilities = FakeOcrEngine.DefaultCapabilities,
    failOnPage = null,                                  // Int?
    available = true,
    delayPerPageMillis = 0L,
)
```

| Parameter | What it does |
|---|---|
| `text` | Page index to the text that page recognizes as, one `OcrLine` per `\n`. |
| `defaultText` | What a page with no `text` entry produces. **Blank means an empty page** — how you simulate an image-only scan that OCR could not read. |
| `capabilities` | What the engine claims it can do. Override to test how your code handles an engine without confidence or word support. |
| `failOnPage` | Makes `recognize` throw `OcrError.RecognitionFailed` for this page index. |
| `available` | What `isAvailable()` returns — the "engine missing" path. |
| `delayPerPageMillis` | Artificial per-page delay, for progress and cancellation tests. Uses `delay`, so it costs no real time under `runTest`. |

`FakeOcrEngine.FAKE_ENGINE_ID` is `"fake"`, which is what `capabilities.engineId` reports.
`DefaultCapabilities` claims `wordLevel` and `confidence` but not `perLineLanguage` or
`skewDetection`, so tests exercise the fully featured paths by default. The engine reports the
languages you asked for, whatever they are.

The lines it produces carry **real geometry** — evenly spaced boxes with plausible sizes, and
word boxes that tile the line without gaps when `includeWords` is on. That is deliberate: a
fake returning `BoundingBox.Zero` everywhere would make every `DocumentStructure` test pass for
the wrong reason.

### Testing the unhappy paths

```kotlin
// Engine missing entirely -> OcrError.Unsupported at the first extract, not at construction.
val noEngine = UncialClient {
    engine = FakeOcrEngine(available = false)
    rasterizer = FakePageRasterizer()
}
assertIs<OcrError.Unsupported>(noEngine.extract(ByteArray(0)).exceptionOrNull())

// Recognition blows up on page 1.
val failing = UncialClient {
    engine = FakeOcrEngine(failOnPage = 1)
    rasterizer = FakePageRasterizer(pageCount = 3)
}
assertEquals(1, assertIs<OcrError.RecognitionFailed>(
    failing.extract(ByteArray(0)).exceptionOrNull(),
).pageIndex)

// Rendering blows up on page 2.
val unrenderable = UncialClient {
    engine = FakeOcrEngine()
    rasterizer = FakePageRasterizer(pageCount = 3, failOnPage = 2)
}
assertIs<OcrError.RenderFailed>(unrenderable.extract(ByteArray(0)).exceptionOrNull())
```

### Testing progress and cancellation

`delayPerPageMillis` plus a long fake document is the whole setup. Under `runTest` the virtual
clock means this is instant:

```kotlin
@Test
fun `cancellation propagates instead of becoming a failed Result`() = runTest {
    val client = UncialClient {
        engine = FakeOcrEngine(delayPerPageMillis = 1_000)
        rasterizer = FakePageRasterizer(pageCount = 100)
    }
    assertFailsWith<CancellationException> {
        withTimeout(2_500) { client.extract(ByteArray(0)) }
    }
    client.close()
}
```

For the event stream itself, collect the flow and assert on the sequence:

```kotlin
val events = client.extractAsFlow(ByteArray(0)).toList()
val started = assertIs<OcrProgress.Started>(events.first())
assertEquals(3, started.pageCount)
assertEquals(listOf(0, 1, 2), events.filterIsInstance<OcrProgress.Page>().map { it.index })
assertEquals(3, assertIs<OcrProgress.Done>(events.last()).document.pageCount)
```

---

## `FakePageRasterizer`

A `PageRasterizer` that invents an imaginary document of the size you ask for.

```kotlin
FakePageRasterizer(
    pageCount = 1,
    pageWidth = FakePageRasterizer.DEFAULT_WIDTH,    // 1654
    pageHeight = FakePageRasterizer.DEFAULT_HEIGHT,  // 2339
    failOnPage = null,
)
```

The defaults are roughly A4 at 200 dpi, matching the `OcrOptions` defaults — so page sizes in
your assertions look like the ones you would see in production. `pageCount` must not be
negative; `pageCount = 0` is legal and makes the extraction fail with `OcrError.InvalidInput`,
which is how you test the empty-document path.

`rasterize` throws `OcrError.RenderFailed` for `failOnPage` and for any index outside
`0 until pageCount`.

Pair it with `FakeOcrEngine` and the two drive `UncialClient` end to end with no real document,
no engine, and no I/O.

---

## `placeholderRaster` and `blankRaster`

Two rasters, for two different jobs.

**`placeholderRaster(width, height)`** — from `io.github.andrewmalitchuk.uncial.core.source.raster` —
has a size and **no pixels**. Reading its platform image (`Raster.bitmap` on Android,
`Raster.image` on JVM and iOS) throws `IllegalStateException`. `release()` is harmless.

This is what `FakePageRasterizer` hands out, and the reason it exists is concrete:

!!! warning "Android host unit tests cannot create a `Bitmap`"
    In a host (unit) test, `Bitmap` is a stub in `android.jar` and `Bitmap.createBitmap` throws
    `not mocked`. A fake pipeline that allocated real bitmaps would therefore be unusable in
    exactly the place a consumer most wants to test their own code — which would defeat the
    point of shipping a fake engine at all. `placeholderRaster` carries dimensions and nothing
    else, so it works everywhere.

**`blankRaster(width, height)`** — from `io.github.andrewmalitchuk.uncial.engine.fake.source.raster` — is a
blank **white** raster with **real pixels**: a `BufferedImage` on the JVM, an ARGB `Bitmap` on
Android, a real image on iOS.

Use it when you are testing a real `TextRecognizer`, which needs something to look at:

```kotlin
import io.github.andrewmalitchuk.uncial.engine.fake.source.raster.blankRaster

val raster = blankRaster(800, 1000)
try {
    val page = recognizer.recognize(raster, pageIndex = 0, options = OcrOptions())
    println(page.lines.size)
} finally {
    raster.release()
}
```

Because it allocates a real `Bitmap`, on Android it belongs in an instrumented test, not a host
test. For driving `UncialClient` against `FakeOcrEngine`, prefer `FakePageRasterizer`.

| | Pixels | Works in an Android host test | Use for |
|---|---|---|---|
| `placeholderRaster` | no | yes | Fake pipelines; anything that ignores pixels |
| `blankRaster` | yes | no (instrumented only) | Exercising a real `TextRecognizer` |

---

## Testing structure without a client

`DocumentStructure.reconstruct` is a pure function over an `OcrDocument`, so it needs neither an
engine nor a rasterizer. Build the document by hand, or produce it with `FakeOcrEngine` — the
fake's line boxes are realistic enough that heading and paragraph heuristics behave as they do
in production:

```kotlin
val client = UncialClient {
    engine = FakeOcrEngine(text = mapOf(0 to "Розділ 1\nПерший абзац тексту."))
    rasterizer = FakePageRasterizer(pageCount = 1)
}
val blocks = DocumentStructure.reconstruct(client.extract(ByteArray(0)).getOrThrow())
client.close()
```

Note that the fake gives every line the same size, so nothing in such a document clears
`headingRatio`. To test heading detection, construct `OcrLine`s with explicit `fontSize` values
yourself — the model types in `uncial-model` are plain data classes with no platform
dependencies.

---

## Registry hygiene

If a test needs to exercise engine discovery rather than bypass it, `OcrEngineRegistry` has
`register(factory)` and `unregister(id)`; the latter is there for tests. Registration is
process-global, so unregister what you registered:

```kotlin
OcrEngineRegistry.register(FakeOcrEngine())
try {
    // code under test resolves the engine through the registry
} finally {
    OcrEngineRegistry.unregister(FakeOcrEngine.FAKE_ENGINE_ID)
}
```

For everything else, passing `engine` to the builder is simpler and cannot leak between tests.
