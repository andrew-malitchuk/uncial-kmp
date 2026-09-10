# raster

PDF page to pixels, using whatever renderer the platform already ships.

## Features

- **One public function**: `createPageRasterizer(logger)`, the `expect`/`actual` factory for `PageRasterizer`.
- **Almost no dependencies**: `PdfRenderer` on Android and PDFKit on iOS are framework APIs; only the JVM pulls in PDFBox.
- **Crop box everywhere**: all three implementations render the crop box, so the same file reports the same page size on every platform.
- **`maxPageSide` is enforced here**, and the resulting effective resolution travels with the pixels as `Raster.sourceDpi`.
- **All targets**: android, jvm, iosArm64, iosSimulatorArm64, iosX64. Depends on `core` (`api`).

## Core Components

| Platform | Renderer | Notes |
|---|---|---|
| Android | `android.graphics.pdf.PdfRenderer` | Renders into `ARGB_8888` only, so `OcrOptions.rasterColor` cannot be honoured; `OutOfMemoryError` is mapped to `OcrError.RenderFailed` with a message naming `renderDpi` and `maxPageSide`. |
| iOS | PDFKit `PDFPage.thumbnailOfSize(_:for:)` | Pages are rendered inside an `autoreleasepool` so a 300-page document does not accumulate temporaries. |
| JVM | PDFBox `PDFRenderer.renderImageWithDPI` | The only implementation that honours `rasterColor` (`ImageType.GRAY` / `ImageType.RGB`). `maxPageSide` is converted back into an effective dpi rather than downscaling afterwards. |

Everything else is the `core` contract: `open(bytes)` returns a `RasterizedDocument` whose `pageCount` is known before any pixels exist, and `rasterize(pageIndex, options)` hands back a `Raster` the caller owns and must `release()`.

Failures are typed: unreadable or empty input is `OcrError.InvalidInput`, a page that cannot be rendered is `OcrError.RenderFailed(pageIndex)`.

## Usage

The runtime creates the rasterizer for you; call it directly only when you want one without a `UncialClient`:

```kotlin
createPageRasterizer().open(pdfBytes).use { document ->
    val raster = document.rasterize(pageIndex = 0, options = OcrOptions())
    try { /* … */ } finally { raster.release() }
}
```

!!! note "`rasterColor` is JVM-only in practice"
    Android's `PdfRenderer` and iOS's PDFKit both produce colour bitmaps regardless. OCR
    engines binarize anyway, so the memory guard that matters on those platforms is
    `maxPageSide`, not the pixel format.

!!! warning "A quarter-turned page comes back with its sides swapped"
    Android's pdfium and the JVM's PDFBox both apply `/Rotate`, so a `90°` or `270°` page
    rasterizes into a turned frame. Anything reporting a page size alongside these rasters —
    notably `pdf-text` — has to swap width and height with them, or the boxes run off the
    page.
