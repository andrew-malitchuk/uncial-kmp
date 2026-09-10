# Errors

Everything that can go wrong is a subclass of `OcrError`, a sealed class in
`io.github.andrewmalitchuk.uncial.model.source.error`. Each case is one a caller can act on differently —
"this PDF has no text layer" is a different problem from "you never installed the language
data", and both are different from "the file is corrupt".

`OcrError` extends `Exception`, which lets it travel two ways without being redefined: as the
failure of a `Result` on the Kotlin side, and as a thrown, typed error on the Swift side via
`@Throws`.

---

## The hierarchy

| Error | Thrown when | Extra properties |
|---|---|---|
| `NoLanguageData` | The engine has no language model for the requested languages. | `languages: List<OcrLanguage>`, `searchedPath: String?` |
| `RenderFailed` | A page could not be turned into a raster. | `pageIndex: Int?` (`null` if the document could not be opened at all) |
| `EngineInit` | The recognition engine could not be started. | — |
| `Unsupported` | The operation cannot be performed on this platform or with the modules on the classpath. | — |
| `InvalidInput` | The input is not something Uncial can read at all. | — |
| `RecognitionFailed` | Recognition ran but the engine failed on a specific page. | `pageIndex: Int` |

**`NoLanguageData`** is the single most common setup failure: `.traineddata` was never
supplied on Android, or `brew install tesseract` was never run on a JVM host. `searchedPath`
tells you where Uncial looked, when it knows.

**`RenderFailed`** is usually a damaged or password-protected PDF, or a page so large that even
`maxPageSide` could not save the allocation.

**`EngineInit`** is a missing native library or a `TessBaseAPI.init` refusal on Android, JNA
failing to find `libtesseract` on the JVM. It is also the fallback the runtime maps any
otherwise-unrecognized platform throwable to, with the original as its `cause` — so check
`cause` before concluding much from the type alone.

**`Unsupported`** covers three real situations: no engine module is on the classpath, an
engine is present but cannot run here (a JVM with no system `libtesseract`), and the client
has been closed. Its message says which, and when an engine blew up while being probed the
registry attaches that throwable as the `cause`.

**`InvalidInput`** means empty bytes, not a PDF, a PDF broken beyond recovery — or a
`pageRange` that selects no pages of a real document, which is reported here rather than
handed back as an empty result.

**`RecognitionFailed`** is distinguished from `RenderFailed` because the page rasterized fine.
The engine itself gave up, which usually means the page survives a retry at a different dpi.

!!! warning "Always give a `when` over `OcrError` an `else`"
    New subclasses may be added in minor releases. An exhaustive `when` today is a broken
    build after a minor upgrade.

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError

fun describe(error: OcrError): String = when (error) {
    is OcrError.NoLanguageData ->
        "missing language data for ${error.languages.joinToString("+")}" +
            (error.searchedPath?.let { " (searched $it)" } ?: "")
    is OcrError.RenderFailed -> "could not render page ${error.pageIndex ?: "?"}"
    is OcrError.RecognitionFailed -> "recognition failed on page ${error.pageIndex}"
    is OcrError.EngineInit -> "engine could not start: ${error.message}"
    is OcrError.Unsupported -> "not available here: ${error.message}"
    is OcrError.InvalidInput -> "unreadable input: ${error.message}"
    else -> error.message ?: "extraction failed"
}
```

---

## `Result` or `OrThrow`

The Kotlin surface returns `Result`:

```kotlin
suspend fun extract(bytes: ByteArray): Result<OcrDocument>
suspend fun extract(raster: Raster): Result<OcrDocument>
```

A failure always carries an `OcrError` — never a raw `UnsatisfiedLinkError` from JNA, an
`IllegalStateException` from `PdfRenderer`, or an `NSError`-derived failure from Vision. Those
are mapped at the runtime's edge, because a consumer cannot branch on them and should not have
to know them.

```kotlin
ocr.extract(pdfBytes)
    .onSuccess { document -> render(document) }
    .onFailure { cause -> ui.showError(describe(cause as OcrError)) }
```

The `...OrThrow` variants throw instead:

```kotlin
suspend fun extractOrThrow(bytes: ByteArray): OcrDocument
suspend fun extractOrThrow(raster: Raster): OcrDocument
```

They exist for Swift. Kotlin's `Result` is an inline value class and does not survive the trip
through Objective-C in any usable form, so the iOS-facing surface throws; both are annotated
`@Throws(OcrError::class, CancellationException::class)` so they arrive as `try await`:

```swift
let document = try await UncialBootstrap.shared.extract(client: client, pdf: data)
```

They are also perfectly reasonable from Kotlin when you are already inside a `try`/`catch` —
`extractOrThrow` is just `extract(…).getOrThrow()`.

!!! note "`extractAsFlow` throws, it does not emit failures"
    There is no `OcrProgress.Failed`. The flow throws `OcrError`, which a `Flow` collector
    already knows how to handle. See
    [Progress and cancellation](progress-and-cancellation.md#failures-are-not-events).

---

## Cancellation is never converted into a failure

There is no `OcrError.Cancelled`, on purpose.

Structured concurrency requires a cancelled coroutine's `CancellationException` to propagate
untouched — swallowing it into a `Result` would leave the caller's scope believing the work
completed. So Uncial never wraps cancellation: if you cancel an extraction you get
`CancellationException`, which is what every other suspend function in your codebase already
does.

```kotlin
// Throws CancellationException. Does NOT return Result.failure(...).
withTimeout(2_500) { ocr.extract(pdfBytes) }
```

Internally this is why the SDK bans `kotlin.runCatching` in the extraction path — it catches
`Throwable`, which includes `CancellationException`.

!!! warning "The rule applies to your code too"
    A hand-rolled `catch (t: Throwable)` around an extraction has exactly the bug the SDK
    avoids: it turns "my caller cancelled me" into "the extraction failed", and a superseded
    run overwrites the newer one's UI state. Re-throw cancellation explicitly:

    ```kotlin
    import kotlin.coroutines.cancellation.CancellationException

    try {
        render(ocr.extractOrThrow(pdfBytes))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: OcrError) {
        ui.showError(describe(error))
    }
    ```

---

## Failing early instead of catching

Two errors are better prevented than handled.

**No engine.** `UncialClient` construction never throws, so a missing engine surfaces at the
first `extract` as `OcrError.Unsupported`. Check up front instead:

```kotlin
if (!ocr.isAvailable) {
    // No engine module on the classpath, or the one present cannot run here.
    // On the JVM: call installTesseractEngine() and check for a system libtesseract.
    // On iOS: call installVisionEngine() (UncialBootstrap.start()) once at launch.
    return
}
```

**Dropped languages.** Engines skip a language they cannot handle rather than failing, so a
Ukrainian document on iOS 15 recognizes as Latin nonsense instead of raising `NoLanguageData`.
Read the capabilities:

```kotlin
val caps = ocr.capabilities
if (caps != null && caps.droppedAnyOf(ocr.options.languages)) {
    warnUser("recognizing only: ${caps.languages.joinToString("+")}")
}
```

**Bad options.** `OcrOptions` validates in its constructor and throws
`IllegalArgumentException`, not `OcrError` — a reversed `pageRange` or an out-of-range
`renderDpi` is a programming error, and it is raised where you build the options rather than
part-way through a document. The same is true of `StructureOptions` and of
`OcrLanguage.custom`. See [Options](options.md#reference).
