package io.github.andrewmalitchuk.uncial.core.source.raster

/**
 * A rasterized page, backed by the platform's native image type.
 *
 * `Raster` is the seam between the two halves of recognition. Splitting the POC's single
 * `ocr(bytes)` call into "rasterize" and "recognize" is what buys three things at once
 * (PLAN.md §6.1): a caller can hand Uncial an image they already have instead of a PDF,
 * recognition can be tested against a PNG with no PDF machinery involved, and either half
 * can be replaced without touching the other.
 *
 * The backing type is platform-specific and exposed on each platform's actual:
 * `android.graphics.Bitmap`, `java.awt.image.BufferedImage`, and `CGImageRef` on iOS.
 * Construct one from your own image via those platform constructors.
 *
 * ### Lifetime
 *
 * A raster of a 200 dpi A4 page is ~5 MB, and a 300-page document rasterized eagerly is
 * not something a phone survives. Uncial's pipeline therefore rasterizes one page at a
 * time and calls [release] as soon as the recognizer is done with it. If you create a
 * `Raster` yourself, you own it — release it when you are finished, or let your own image
 * lifecycle handle it.
 */
public expect class Raster {
    /** Width in pixels. */
    public val width: Int

    /** Height in pixels. */
    public val height: Int

    /**
     * The resolution these pixels were actually rendered at, or `null` if unknown.
     *
     * This is **not** `OcrOptions.renderDpi`. A rasterizer clamps the requested dpi
     * whenever the page would exceed `OcrOptions.maxPageSide`, so on a large page the two
     * differ — and Tesseract's layout heuristics take the source resolution as an input,
     * so telling it the requested figure describes pixels that were never produced. A
     * raster the caller built from their own image has no meaningful answer here.
     */
    public val sourceDpi: Int?

    /**
     * Frees the backing image where the platform requires it (Android's
     * `Bitmap.recycle()`); a no-op where the runtime handles it.
     *
     * Calling this more than once is safe. Using the raster afterwards is not.
     */
    public fun release()
}
