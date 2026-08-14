package io.github.andrewmalitchuk.uncial.core.source.raster

import io.github.andrewmalitchuk.uncial.model.source.error.OcrError

/**
 * Turns PDF pages into rasters.
 *
 * Implemented per platform with whatever is already there: `PdfRenderer` on Android,
 * PDFKit on iOS, PDFBox on the JVM. (PLAN.md §6.1)
 */
public interface PageRasterizer {

    /**
     * Opens [bytes] as a PDF and returns a handle to it **without rasterizing anything**.
     *
     * Opening is separate from rasterizing so that page count is known before any pixels
     * are allocated — which is what makes a meaningful `Flow<OcrProgress>` possible.
     *
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.InvalidInput if the bytes
     *   are not a readable PDF.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.RenderFailed if the document
     *   opens but cannot be prepared for rendering.
     */
    public suspend fun open(bytes: ByteArray): RasterizedDocument
}
