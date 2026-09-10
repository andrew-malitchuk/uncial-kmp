# Quick Start

The smallest thing that works: build a client, extract a PDF, print the text.

!!! warning "Not on Maven Central yet"
    These snippets compile against the modules in this repository, or against the same
    artifacts installed with `./gradlew publishToMavenLocal`. Nothing is released publicly
    yet — see [Installation](installation.md).

---

## 1. Check capabilities first

`UncialClient.capabilities` is `null` when no engine is available, and otherwise reports
what this platform can *actually* do. Read it rather than assuming: Android, iOS and the
JVM run different engines with different metadata, and the languages reported back can be
a **subset** of the ones you asked for — Vision has no Ukrainian before iOS 16, and
Tesseract has none without the matching model.

```kotlin
val capabilities = ocr.capabilities
if (capabilities == null) {
    // No engine module on the classpath, or the one that is cannot run here
    // (a JVM with no system libtesseract, for instance).
    // Every extract() call will fail with OcrError.Unsupported.
    return
}
println("engine     ${capabilities.engineId} ${capabilities.engineVersion.orEmpty()}")
println("languages  ${capabilities.languages.joinToString("+") { it.tesseractCode }}")
println("words      ${capabilities.wordLevel}")
println("confidence ${capabilities.confidence}")
```

`UncialClient.isAvailable` is the same check as a boolean, and
`hasDigitalTextLayerSupport` says whether the digital fast path is wired up on this client.

---

## 2. Build a client

`UncialClient { }` is a function, not a constructor, and it **never throws** — a missing or
unusable engine surfaces at the first `extract` call instead. That makes it safe to build
one in an initializer or a DI graph.

Keep it: it holds a loaded recognition engine, which for `ukr+eng` is ~8 MB of language
model you do not want to reload per document.

=== "Android"

    ```kotlin
    import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
    import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
    import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor

    val ocr = UncialClient {
        languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
        renderDpi = 200
        preferDigitalLayer = true
        digitalTextExtractor = pdfTextExtractor()   // requires :sdk:pdf-text
    }
    ```

    No engine selection, no `tessdata` copying, no initialization: the Tesseract engine
    registers itself at process start through `androidx.startup`.

=== "JVM"

    ```kotlin
    import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
    import io.github.andrewmalitchuk.uncial.engine.tesseract.source.install.installTesseractEngine
    import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
    import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor

    // The JVM has no androidx.startup, so the engine is registered explicitly. Once.
    installTesseractEngine()

    val ocr = UncialClient {
        languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
        renderDpi = 200
        preferDigitalLayer = true
        digitalTextExtractor = pdfTextExtractor()
    }
    ```

The builder's recognition settings mirror `OcrOptions` one for one — `languages`,
`renderDpi`, `maxPageSide`, `preferDigitalLayer`, `recognitionQuality`, `rasterColor`,
`includeWords`, `pageRange` — or set `options` to supply a whole `OcrOptions` and ignore
the individual properties. The remaining properties (`engine`, `rasterizer`,
`digitalTextExtractor`, `languageData`, `logger`) are the composition root: Uncial ships no
DI framework on purpose, so this hand-written builder is the injection point.

!!! note "`digitalTextExtractor` is not wired automatically"
    `preferDigitalLayer = true` alone does nothing without an extractor. The module is
    optional because on Android it pulls in several MB of PDFBox-Android, which an app that
    only OCRs photographs should not pay for.

---

## 3. Extract

### One call

`extract` returns a `Result`, and a failure always carries an `OcrError` — never a raw
platform exception. Cancellation is never captured into the `Result`: cancelling the
calling coroutine throws `CancellationException` as any other suspend function would.

```kotlin
val document = ocr.extract(pdfBytes).getOrElse { error ->
    System.err.println("extraction failed: $error")
    return
}

println("source ${document.source}, ${document.pageCount} pages, ${document.lines.size} lines")
document.lines.forEach { println(it.text) }
```

There is a second overload for an image you already have, with no PDF involved — build a
`Raster` from your platform's image type (`Raster(bitmap)`, `Raster(bufferedImage)`,
`Raster(cgImage)`) and pass that instead. The caller keeps ownership.

### With progress

`extractAsFlow` emits per page, which is what makes a progress bar and cancellation
possible on a 300-page scan. The flow is cold, it **throws** `OcrError` rather than
emitting it, and cancelling the collector stops the work at the next page boundary — or
sooner, since the Tesseract engine can interrupt a page in flight.

```kotlin
var document: OcrDocument? = null
ocr.extractAsFlow(pdfBytes).collect { progress ->
    when (progress) {
        is OcrProgress.Started ->
            println("processing ${progress.pageCount} pages (${progress.source})")
        is OcrProgress.Page ->
            println("  page ${progress.index + 1}/${progress.of} — " +
                "${progress.page.lines.size} lines (${(progress.fraction * 100).toInt()}%)")
        is OcrProgress.Done -> document = progress.document
        // OcrProgress gains subtypes in minor releases: always give the `when` an else.
        else -> Unit
    }
}
```

`OcrProgress.Started` carries the page count *after* `pageRange` is applied, and
`source` is `ExtractionSource.DigitalTextLayer`, `Ocr` or `Mixed`. `OcrProgress.Page` carries the finished page, so
results can be rendered incrementally.

### Headings and paragraphs

Lines are not a document. `:sdk:structure` turns them into blocks:

```kotlin
DocumentStructure.reconstruct(document).forEach { block ->
    when (block) {
        is DocBlock.Heading -> println("${"#".repeat(block.level)} ${block.text}")
        is DocBlock.Paragraph -> println("${block.text}\n")
        else -> println(block.text)
    }
}
```

---

## 4. Close it

```kotlin
ocr.close()
```

!!! warning "`close()` blocks the calling thread"
    The bundled engines take a blocking native lock so a handle is never freed underneath
    work in flight — freeing one mid-recognition is a SIGSEGV, not an exception. Closing
    therefore waits out whatever is running: on Android that can be a whole page. Do not
    call it on the main thread — or inside a `use { }` block on the main thread — without
    expecting a stall.

    It is safe to call at any time, including during an extraction, and safe to call twice.
    Afterwards every `extract` fails with `OcrError.Unsupported`.

---

## iOS, from Swift

Do not write Swift against the raw Kotlin API. Kotlin's generated Obj-C surface is usable
but unpleasant: `ByteArray` arrives as `KotlinByteArray` (so Swift cannot hand over a
`Data`), the `UncialClient { }` builder becomes a lambda-taking function on a `…Kt` class,
and `Result` does not survive the export at all.

`:dist:ios-framework` therefore ships `UncialIos`, exported to Swift as
**`UncialBootstrap`**, which hides exactly those edges. It is the seed of the `:sdk:swift`
module that PLAN.md §6.3 reserves.

```swift
import Uncial

// iOS has no androidx.startup, so Vision is registered by hand. Once, at launch.
UncialBootstrap.shared.start()

// Obj-C export drops Kotlin default arguments, so Swift passes every parameter.
let client = UncialBootstrap.shared.createClient(
    languages: [OcrLanguage.Companion.shared.Ukrainian,
                OcrLanguage.Companion.shared.English],
    renderDpi: 200,
    includeWords: false,
    preferDigitalLayer: true,
    onLog: { message in print("uncial \(message)") }
)

if let capabilities = client.capabilities {
    let languages = capabilities.languages.map { $0.tesseractCode }.joined(separator: "+")
    print("engine \(capabilities.engineId), languages \(languages)")
} else {
    print("no engine registered: \(UncialBootstrap.shared.registeredEngines())")
}

// Takes NSData so a Swift `Data` goes straight across, and throws rather than
// returning a Result. In Swift this is `try await`.
let document = try await UncialBootstrap.shared.extract(client: client, pdf: data)

for block in UncialBootstrap.shared.reconstruct(document: document) {
    if let heading = block as? DocBlockHeading {
        print(String(repeating: "#", count: Int(heading.level)) + " " + heading.text)
    } else {
        print(block.text)
    }
}
```

`createClient` wires `pdfTextExtractor()` in for you: PDFKit is a system framework, so on
iOS the digital fast path costs nothing in binary size.

!!! warning "The Simulator recognizes nothing"
    Vision text recognition returns **zero observations on the iOS Simulator** — there is
    no Neural Engine, `performRequests` succeeds, `error` is `nil`, and `results` is empty.
    A green Simulator run says nothing about recognition quality. Only a device does.

---

## Where the `.traineddata` comes from

Nothing above says where Tesseract's models live, because there are four different answers
— a bundled `uncial-lang-*` artifact, the machine's own Tesseract install, a runtime
download, or your own `LanguageDataProvider`. Vision needs none of them.

See [Language data](../language-data.md).

---

Next: [Samples](../samples.md) — the three runnable apps, and the only way to exercise
recognition end to end on a developer machine.
