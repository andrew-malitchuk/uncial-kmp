# raster — Claude Instructions

## Module Purpose

The three `PageRasterizer` implementations: `PdfRenderer` on Android, PDFKit on iOS, PDFBox
on the JVM. One public `expect` function; everything else is `private` inside its own actual.

## Package Layout

```
source/rasterizer/PageRasterizers.kt                 expect createPageRasterizer(logger)
source/rasterizer/PageRasterizers.{android,ios,jvm}.kt
```

Implementation classes stay `private` in the actual file. There is no `core/` here and there
should not be one — a rasterizer that needs shared internals is a sign the shared part
belongs in `core`'s contract instead.

## The Three Actuals Must Agree

Any change to one is a change to all three. What they must keep agreeing on:

- **The crop box, never the media box.** `pdf-text` expresses every `TextPosition` relative
  to the crop box, so a rasterizer that renders the media box puts the boxes in the wrong
  place.
- **`/Rotate 90|270` swaps the sides**, and whatever reports the page size swaps with it.
- **`maxPageSide` is enforced here**, and the resulting *effective* dpi is what
  `Raster.sourceDpi` carries — it is what Tesseract is told, so a wrong value here is a
  silent accuracy loss over there.
- **Typed failures:** unreadable or empty input is `OcrError.InvalidInput`; a page that
  cannot render is `OcrError.RenderFailed(pageIndex)`. Android maps `OutOfMemoryError` into
  the latter, with a message naming `renderDpi` and `maxPageSide`.
- **`open(bytes)` must not allocate pixels.** The page count has to be knowable before any
  page is rendered; that is the whole reason `RasterizedDocument` exists.

## Rules

- **The caller owns the `Raster`.** `rasterize` hands it over and the caller calls
  `release()`. Do not pool, cache or release rasters here.
- **`rasterColor` is honoured only on the JVM.** Say so rather than silently converting;
  Android's `PdfRenderer` is `ARGB_8888`-only and PDFKit is colour regardless.
- **iOS renders inside an `autoreleasepool`,** or a 300-page document accumulates
  temporaries until it dies.
- **No new third-party dependency on Android or iOS.** Both renderers are framework APIs;
  that is the module's main property.

## Verify

```bash
./gradlew :sdk:raster:build
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf"   # the JVM path, end to end
```
