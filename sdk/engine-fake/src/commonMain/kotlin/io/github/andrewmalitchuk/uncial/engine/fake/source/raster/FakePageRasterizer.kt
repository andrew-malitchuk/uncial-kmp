package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.core.source.raster.placeholderRaster
import io.github.andrewmalitchuk.uncial.engine.fake.source.engine.FakeOcrEngine
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions

/**
 * A rasterizer that needs no PDF.
 *
 * Pairs with [FakeOcrEngine]: together they let a consumer test their own code end to end
 * against `UncialClient` with no real document, no engine, and no I/O. The bytes handed to
 * [open] are ignored entirely — pass `ByteArray(0)`.
 *
 * @param pageCount how many pages the imaginary document has.
 * @param pageWidth width of each blank raster, in pixels.
 * @param pageHeight height of each blank raster, in pixels.
 * @param failOnPage make [RasterizedDocument.rasterize] throw
 *   [OcrError.RenderFailed] for this page index.
 */
public class FakePageRasterizer(
    private val pageCount: Int = 1,
    private val pageWidth: Int = DEFAULT_WIDTH,
    private val pageHeight: Int = DEFAULT_HEIGHT,
    private val failOnPage: Int? = null,
) : PageRasterizer {

    init {
        require(pageCount >= 0) { "pageCount must not be negative" }
    }

    override suspend fun open(bytes: ByteArray): RasterizedDocument = FakeDocument()

    private inner class FakeDocument : RasterizedDocument {

        override val pageCount: Int = this@FakePageRasterizer.pageCount

        override suspend fun rasterize(pageIndex: Int, options: OcrOptions): Raster {
            if (pageIndex == failOnPage) throw OcrError.RenderFailed(pageIndex)
            if (pageIndex !in 0 until pageCount) throw OcrError.RenderFailed(pageIndex)
            // Placeholders, not real bitmaps: this has to work in an Android host
            // unit test, where Bitmap.createBitmap does not exist.
            return placeholderRaster(pageWidth, pageHeight)
        }

        override fun close() {
            // Nothing to release.
        }
    }

    public companion object {
        /** Roughly A4 at 200 dpi, matching `OcrOptions` defaults. */
        public const val DEFAULT_WIDTH: Int = 1654

        /** Roughly A4 at 200 dpi. */
        public const val DEFAULT_HEIGHT: Int = 2339
    }
}
