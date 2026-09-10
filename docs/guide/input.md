# Input: PDFs and Images

Uncial takes two kinds of input, and both end up as the same `OcrDocument`:

| Input | Call | What happens |
|---|---|---|
| PDF bytes | `extract(bytes: ByteArray)` | Optional digital text layer first, then page-by-page rasterize + recognize. |
| An image you already have | `extract(raster: Raster)` | One page, straight to the recognizer. No PDF machinery. |

The split exists because rasterization and recognition are separate roles inside the SDK
(`PageRasterizer` and `TextRecognizer`). Giving the recognizer pixels directly is therefore
almost free, and it is what makes camera frames, screenshots and PNG fixtures work.

---

## Extracting a PDF

```kotlin
import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

val ocr = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    renderDpi = 200
}

val document = ocr.extract(pdfBytes).getOrThrow()
println("${document.pageCount} pages, ${document.lines.size} lines")

ocr.close()
```

`extract` returns `Result<OcrDocument>`; a failure always carries an
[`OcrError`](errors.md). Keep one client for as long as you are extracting — it owns a
loaded engine and, on Tesseract, several MB of language model.

!!! warning "`close()` blocks"
    The bundled engines take a blocking native lock so a handle is never freed underneath
    work in flight. `close()` therefore waits out whatever page is running. Do not call it
    on the main thread without expecting a stall — see
    [Progress and cancellation](progress-and-cancellation.md#closing-a-client).

---

## The digital text layer fast path

A PDF produced by a word processor already contains its text, with coordinates. Running OCR
over a raster of it is roughly a thousand times slower and strictly less accurate, so Uncial
reads the text layer instead when it can.

This is **not wired up by default**. The reader lives in the optional `uncial-pdf-text`
module — on Android it pulls in several MB of PDFBox-Android, which an app that only OCRs
photographs should not pay for. You opt in by handing the client an extractor:

```kotlin
import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor

val ocr = UncialClient {
    digitalTextExtractor = pdfTextExtractor()
    preferDigitalLayer = true          // the default
}
```

`UncialClient.hasDigitalTextLayerSupport` tells you whether an extractor is present on a
given client. With `digitalTextExtractor` left `null`, `preferDigitalLayer` is inert and OCR
always runs.

On iOS, `UncialBootstrap.createClient(...)` wires `pdfTextExtractor()` in for you — PDFKit is
a system framework, so the digital path costs nothing in binary size there.

### What you get back

Every page and every document carries an `ExtractionSource`, so you never have to infer the
path from timings:

| Value | Meaning |
|---|---|
| `DigitalTextLayer` | Read from the PDF's embedded text. Exact; `Confidence.Certain` throughout. |
| `Ocr` | Recognized from pixels by an engine. |
| `Mixed` | Some pages came from the text layer, the rest were OCR'd. |

```kotlin
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource

when (document.source) {
    ExtractionSource.DigitalTextLayer -> ui.hideScannedBadge()
    ExtractionSource.Ocr -> ui.showScannedBadge()
    ExtractionSource.Mixed -> ui.showPartiallyScannedBadge()
}
```

`Mixed` is the ordinary outcome for a scan whose cover page was OCR'd by the scanner: page 1
has text, the rest do not, so page 1 is reused and the others go through the engine. The
decision is per page, not per document.

!!! note "Coverage is judged over the pages you asked for"
    When `pageRange` is set, "does the text layer cover everything?" is answered over the
    selected pages only. `pageRange = 2..4` on a document whose text layer covers pages 2–4
    skips OCR entirely, even if pages 0 and 1 carry nothing.

Per-page source is on the page itself, which is how you split a `Mixed` document:

```kotlin
val scannedPages = document.pages.filter { it.source == ExtractionSource.Ocr }
```

!!! warning "Narrowing a document with `copy()` keeps the old source"
    `OcrDocument.source` is derived from the pages **only when the default argument runs**,
    and `copy()` does not re-run default arguments. A `Mixed` document narrowed with
    `document.copy(pages = …)` still reports `Mixed`. Use the constructor instead:

    ```kotlin
    import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument

    OcrDocument(document.pages.filter { it.source == ExtractionSource.DigitalTextLayer })
    ```

---

## Extracting an image you already have

`Raster` wraps the platform's native image type. Construct one and hand it to `extract`:

=== "Android"

    ```kotlin
    import android.graphics.Bitmap
    import io.github.andrewmalitchuk.uncial.core.source.raster.Raster

    suspend fun recognize(ocr: UncialClient, bitmap: Bitmap) {
        val raster = Raster(bitmap)
        try {
            val document = ocr.extract(raster).getOrThrow()
            println(document.text)
        } finally {
            // You created it, so you decide when it dies:
            // release() calls Bitmap.recycle() on the bitmap you passed in.
            raster.release()
        }
    }
    ```

=== "JVM"

    ```kotlin
    import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
    import java.io.File
    import javax.imageio.ImageIO

    suspend fun recognize(ocr: UncialClient, file: File) {
        val raster = Raster(ImageIO.read(file), sourceDpi = 300)
        val document = ocr.extract(raster).getOrThrow()
        println(document.text)
    }
    ```

=== "iOS (Kotlin)"

    ```kotlin
    import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
    import platform.CoreGraphics.CGImageRef

    suspend fun recognize(ocr: UncialClient, image: CGImageRef) {
        val raster = Raster(image)   // retains the CGImage
        try {
            val document = ocr.extract(raster).getOrThrow()
            println(document.text)
        } finally {
            raster.release()          // releases it again
        }
    }
    ```

Both constructors take an optional `sourceDpi`. See below for when it matters.

An image extraction always produces exactly one page, and its source is always
`ExtractionSource.Ocr`. `pageRange` and `preferDigitalLayer` do not apply — there is no
document to page through and no text layer to read.

---

## Raster lifetime and ownership

The rule is short: **whoever created the `Raster` releases it.**

- **Rasters Uncial creates** — one per page, inside `extract(bytes)` — are released by the
  pipeline as soon as the recognizer is done with that page. A 200 dpi A4 raster is several
  MB, and holding 300 of them is not survivable on a phone, so they are freed per page
  rather than at the end of the document. You never see these.
- **Rasters you create** are yours. `extract(raster)` does not release the one you passed
  in; neither does `TextRecognizer.recognize`. Call `release()` yourself, or let your own
  image lifecycle handle it.

What `release()` actually does differs by platform:

| Platform | Backing type | `release()` |
|---|---|---|
| Android | `android.graphics.Bitmap` | `Bitmap.recycle()` — the one platform where this really matters. |
| JVM | `java.awt.image.BufferedImage` | No-op; the GC reclaims it. |
| iOS | `CGImageRef` | `CGImageRelease` of the retain the constructor took. |

Calling `release()` more than once is safe. Using the raster afterwards is not.

!!! warning "iOS: the constructor retains, and that is load-bearing"
    `CGImageRef` is a raw pointer in Kotlin/Native with no ARC — holding one in a Kotlin
    field neither retains it nor keeps its owner alive. `Raster`'s constructor calls
    `CGImageRetain` and `release()` calls `CGImageRelease`, which is what stops a `CGImage`
    taken from a short-lived `UIImage` from being freed while a recognizer is still reading
    it. The flip side: on iOS, after `release()` the raster has no pixels and reading
    `image` throws.

---

## `sourceDpi` is `null` for images you supply

`Raster.sourceDpi` is the resolution the pixels were **actually** rendered at. It is not
`OcrOptions.renderDpi`: a rasterizer clamps the requested dpi whenever the page would exceed
`maxPageSide`, so on a large page the two differ. Tesseract takes source resolution as a
layout input, so the SDK passes the effective figure rather than the requested one.

For a raster the caller built from their own image there is no honest answer, so the default
is `null` — and the engine is told nothing and estimates instead, which is better than
asserting a number nobody rendered at. Pass `sourceDpi` yourself only if you genuinely know
it (a 300 dpi flatbed scan, a page you rendered at a known resolution).

```kotlin
val raster = Raster(scannedImage, sourceDpi = 300)   // you know this
val unknown = Raster(cameraFrame)                    // sourceDpi == null, and that is correct
```

---

## Photos are not scans

A photo of a page reaches the recognizer through the same `Raster`, but two things about it
are the **caller's** job, and both are silent failures rather than errors.

**Orientation.** A camera writes the sensor's pixels and records how the device was held
somewhere alongside them — an EXIF tag on Android, `UIImage.imageOrientation` on iOS.
Neither Tesseract nor Vision looks at either: Tesseract is handed a `Bitmap` and Vision is
handed a `CGImage`, and both of those are just pixels. So a portrait photo arrives on its
side, and a sideways page under `PSM_AUTO` mostly recognizes as nothing at all. Rotate the
pixels before wrapping them:

- **Android** — read `ExifInterface.TAG_ORIENTATION` and apply it with a `Matrix`.
- **iOS** — redraw the `UIImage` into a context, which bakes `imageOrientation` into the
  pixels. `UncialBootstrap.extract(client:image:)` does this for you; a `Raster` you build
  from `uiImage.CGImage` yourself does not.

**Size.** `OcrOptions.maxPageSide` guards the *rasterizers*, and a raster you supply never
goes through one — so nothing clamps a 48 MP phone photo. Downscale it yourself. Roughly
200 dpi across the page is plenty; on Android, `BitmapFactory.Options.inSampleSize` does it
without ever materializing the full image.

Neither engine does perspective correction, deskewing or binarization beyond its own
internal thresholding, so a photo taken at an angle or under uneven light will read worse
than a scan of the same page. Where that matters, run the image through a document scanner
first (ML Kit's on Android, `VNDocumentCameraViewController` on iOS) and hand Uncial its
output. Both sample apps show the plain camera-and-gallery path — see
[Samples](../samples.md).

---

## What is next

- [Options](options.md) — `renderDpi`, `maxPageSide`, `pageRange` and the rest.
- [Progress and cancellation](progress-and-cancellation.md) — `extractAsFlow` for long documents.
- [Coordinates](coordinates.md) — what the boxes on a page mean.
