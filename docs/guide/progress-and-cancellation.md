# Progress and Cancellation

A 300-page scan takes minutes. `UncialClient` offers two shapes for that work:

| | Returns | Use when |
|---|---|---|
| `extract(bytes)` / `extract(raster)` | `Result<OcrDocument>` | You only want the finished document. |
| `extractAsFlow(bytes)` / `extractAsFlow(raster)` | `Flow<OcrProgress>` | A user has to see progress, or you want results page by page. |

They are the same pipeline. `extract` is literally `extractAsFlow` with everything but the
final document dropped, so switching between them changes nothing about the result.

---

## `extract`

```kotlin
val result = ocr.extract(pdfBytes)
val document = result.getOrElse { error -> return report(error) }
```

Failures arrive as a failed `Result` carrying an [`OcrError`](errors.md) — never a raw
platform exception. Cancellation is the exception to that rule and is never captured into a
`Result`; see below.

---

## `extractAsFlow`

The flow is **cold**: nothing happens until it is collected, and collecting it twice runs the
extraction twice.

```kotlin
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress

ocr.extractAsFlow(pdfBytes).collect { progress ->
    when (progress) {
        is OcrProgress.Started -> ui.showTotal(progress.pageCount)
        is OcrProgress.Page -> ui.update(progress.fraction)
        is OcrProgress.Done -> render(progress.document)
        else -> Unit
    }
}
```

!!! note "Always give the `when` an `else`"
    Both `OcrProgress` and `DocBlock` are documented as hierarchies that will grow in minor
    releases. An exhaustive `when` today is a broken build after a minor upgrade.

### The events

`OcrProgress` is a sealed interface with three subtypes, emitted in this order:

**`Started(pageCount, source)`** — once, before any page is processed.

| Property | Type | Meaning |
|---|---|---|
| `pageCount` | `Int` | How many pages will be processed, **after `pageRange` is applied**. Not necessarily the document's page count. |
| `source` | `ExtractionSource` | How the text is being obtained: `DigitalTextLayer`, `Ocr`, or `Mixed`. |

This event exists so a progress indicator has a total before the first page arrives. The
`source` is computed from what will actually happen, not from "a text layer exists" — a text
layer that covers none of the selected pages yields `Started(source = Ocr)`.

**`Page(index, completed, of, page)`** — after each page finishes.

| Property | Type | Meaning |
|---|---|---|
| `index` | `Int` | The page's zero-based index **in the source document**. With `pageRange = 2..4` the first `Page` event has `index == 2`. |
| `completed` | `Int` | How many pages are done, counting this one. Starts at 1. |
| `of` | `Int` | Total pages being processed; the same value as `Started.pageCount`. |
| `page` | `OcrPage` | The recognized page, so you can render incrementally. |

`fraction` is a convenience: `completed / of` as a `Float` in `0.0..1.0` (and `1f` when `of`
is not positive).

**`Done(document)`** — once, last, with the assembled `OcrDocument`.

A collector that only wants the end result can `filterIsInstance<OcrProgress.Done>().first()`
— which is exactly what `extract` does.

### Failures are not events

There is no `OcrProgress.Failed`. The flow **throws** `OcrError`, which is what a `Flow`
collector already knows how to handle:

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import kotlinx.coroutines.flow.catch

ocr.extractAsFlow(pdfBytes)
    .catch { cause -> if (cause is OcrError) ui.showError(cause) else throw cause }
    .collect { /* … */ }
```

### Image input

`extractAsFlow(raster)` emits the same three events for a single page: `Started(pageCount = 1,
source = Ocr)`, one `Page`, then `Done`.

---

## Cancellation

Cancellation is ordinary structured concurrency. Cancel the collecting coroutine and the
extraction stops:

```kotlin
val job = scope.launch {
    ocr.extractAsFlow(pdfBytes).collect { /* … */ }
}

// later
job.cancel()
```

The pipeline checks its coroutine's state at every page boundary, so work stops at the next
page at the latest — sooner where the engine can interrupt a page in flight (the Tesseract
engines can). `extract` and `extractOrThrow` behave identically; they run the same flow.

### Cancellation is never a failure

`CancellationException` propagates untouched. It is **not** converted into an
`OcrError`, it is **not** captured into a `Result`, and there is no `OcrError.Cancelled`.

```kotlin
// This throws CancellationException. It does not return a failed Result.
withTimeout(2_500) { ocr.extract(pdfBytes) }
```

Swallowing cancellation into a `Result` would leave the caller's scope believing the work
completed, which is why the SDK never uses `kotlin.runCatching` in the extraction path.

!!! warning "The same rule applies to your code"
    A hand-rolled `try { … } catch (t: Throwable) { showError(t) }` around an extraction is
    the same bug from the other side: it catches `CancellationException` and reports a
    superseded run as a failure. Catch `OcrError`, or re-throw cancellation explicitly.

    ```kotlin
    import kotlin.coroutines.cancellation.CancellationException

    try {
        render(ocr.extractOrThrow(pdfBytes))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: OcrError) {
        ui.showError(error)
    }
    ```

---

## Closing a client

`UncialClient` is `AutoCloseable`. Keep one for as long as you are extracting — creating a
recognizer loads the language model, which for `ukr+eng` is around 8 MB and takes real time —
and close it when you are done.

```kotlin
ocr.close()          // idempotent; closing twice is safe
```

After `close()` the client is unusable and every `extract` fails with `OcrError.Unsupported`.
It may be called at any time, including while an extraction is running, and the recognizer is
released exactly once however the two interleave.

!!! warning "`close()` blocks the calling thread"
    The bundled engines take a **blocking** native lock in `close()`. `AutoCloseable.close()`
    cannot take a suspending mutex, and freeing a handle underneath a running recognition is a
    SIGSEGV rather than an exception — so closing waits out the work in flight. On Android that
    is a whole page; on the JVM the native call currently executing.

    Do not call it casually from `onDestroy` or a `use { }` block on the main thread. Cancel
    first and close when the work has actually finished:

    ```kotlin
    // In a ViewModel: cancel the scope, and close once its children have completed.
    extractionScope.coroutineContext.job.invokeOnCompletion { ocr.close() }
    extractionScope.cancel()
    ```

    A cancelled `Job` completes only after all of its children have, so the completion handler
    is the wait — without blocking the main thread, which a `runBlocking { cancelAndJoin() }`
    here would do for the length of a page.

### Concurrent extractions

Sharing one client across coroutines is safe, and concurrent `extract` calls on it are safe.
The serialization that makes them safe belongs to the engines, not to the client: one client
creates one recognizer and hands that same instance to every call, and each bundled recognizer
holds a single native handle behind its own lock. The calls therefore **queue rather than run
in parallel**. If your UI lets a user start a second document, cancelling the first is usually
what you want rather than letting it queue.
