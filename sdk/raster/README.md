# raster

> PDF page to pixels, using whatever renderer the platform already ships.

Gradle `:sdk:raster` · Maven `io.github.andrew-malitchuk:uncial-raster` · [full docs](../../docs/modules/raster.md)

## Responsibility

Implements `PageRasterizer` on all three platforms and nothing else. One public function —
the `expect`/`actual` factory — because the contract it satisfies lives in `core` and the
caller normally never sees this module at all: `UncialClient` creates the rasterizer itself.

`maxPageSide` is enforced here, and the resolution the pixels were really rendered at travels
with them as `Raster.sourceDpi`.

## Layout

```
source/rasterizer/PageRasterizers.kt        expect createPageRasterizer(logger)
source/rasterizer/PageRasterizers.{android,ios,jvm}.kt
```

No `core/`: every implementation type is `private` inside its own file.

## Dependencies

`api(:sdk:core)`, plus PDFBox on the JVM. Android's `PdfRenderer` and iOS's PDFKit are
framework APIs, so only the JVM adds a third-party dependency.

## Public API

| Declaration | Notes |
|---|---|
| `createPageRasterizer(logger = OcrLogger.None)` | the `expect`/`actual` factory for `PageRasterizer` |

| Platform | Renderer | Notes |
|---|---|---|
| Android | `android.graphics.pdf.PdfRenderer` | `ARGB_8888` only, so `rasterColor` cannot be honoured; `OutOfMemoryError` maps to `OcrError.RenderFailed` |
| iOS | PDFKit `thumbnailOfSize(_:for:)` | pages render inside an `autoreleasepool` so a 300-page document does not accumulate temporaries |
| JVM | PDFBox `renderImageWithDPI` | the only one that honours `rasterColor`; `maxPageSide` becomes an effective dpi rather than a downscale |

Failures are typed: unreadable or empty input is `OcrError.InvalidInput`, a page that cannot
be rendered is `OcrError.RenderFailed(pageIndex)`.

## Usage

```kotlin
createPageRasterizer().open(pdfBytes).use { document ->
    val raster = document.rasterize(pageIndex = 0, options = OcrOptions())
    try { /* … */ } finally { raster.release() }
}
```

## Known behaviours and pitfalls

- **Use the crop box, never the media box.** All three implementations render the crop box,
  so the same file reports the same page size everywhere — and `pdf-text` expresses every
  `TextPosition` relative to it too.
- **A quarter-turned page comes back with its sides swapped.** pdfium and PDFBox both apply
  `/Rotate`, so anything reporting a page size alongside these rasters has to swap width and
  height with them.
- **`rasterColor` is JVM-only in practice.** The other two produce colour regardless; engines
  binarize anyway, so the guard that matters there is `maxPageSide`.

## Build

```bash
./gradlew :sdk:raster:build
```
