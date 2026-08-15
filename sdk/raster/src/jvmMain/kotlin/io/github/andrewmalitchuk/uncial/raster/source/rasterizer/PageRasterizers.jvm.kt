package io.github.andrewmalitchuk.uncial.raster.source.rasterizer

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.options.RasterColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import kotlin.math.roundToInt

public actual fun createPageRasterizer(logger: OcrLogger): PageRasterizer =
    JvmPageRasterizer(logger)

/** Rasterizes with PDFBox — the only platform where rasterization costs a dependency. */
private class JvmPageRasterizer(private val logger: OcrLogger) : PageRasterizer {

    override suspend fun open(bytes: ByteArray): RasterizedDocument {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        return withContext(Dispatchers.IO) {
            val document = runCatching { Loader.loadPDF(bytes) }
                .getOrElse { throw OcrError.InvalidInput("not a readable PDF", it) }
            JvmRasterizedDocument(document, logger)
        }
    }
}

private class JvmRasterizedDocument(
    private val document: PDDocument,
    private val logger: OcrLogger,
) : RasterizedDocument {

    // PDFRenderer keeps per-document state and is not safe to call concurrently.
    private val lock = Mutex()
    private val renderer = PDFRenderer(document)

    override val pageCount: Int = document.numberOfPages

    override suspend fun rasterize(pageIndex: Int, options: OcrOptions): Raster =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching { renderPage(pageIndex, options) }.getOrElse { cause ->
                    if (cause is OcrError) throw cause
                    throw OcrError.RenderFailed(pageIndex = pageIndex, cause = cause)
                }
            }
        }

    private fun renderPage(pageIndex: Int, options: OcrOptions): Raster {
        val imageType = when (options.rasterColor) {
            RasterColor.Grayscale -> ImageType.GRAY
            RasterColor.Color -> ImageType.RGB
        }
        // PDFBox takes a dpi rather than a scale, so maxPageSide has to be converted back
        // into an effective dpi before rendering instead of downscaling afterwards.
        val dpi = effectiveDpi(pageIndex, options)
        logger.debug("rasterize page $pageIndex at $dpi dpi ($imageType)")
        val image = renderer.renderImageWithDPI(pageIndex, dpi, imageType)
        // The effective dpi travels with the pixels: the recognizer needs the resolution
        // that was actually rendered, not the one that was asked for.
        return Raster(image, sourceDpi = dpi.roundToInt())
    }

    private fun effectiveDpi(pageIndex: Int, options: OcrOptions): Float {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        val longSidePoints = maxOf(box.width, box.height)
        if (longSidePoints <= 0f) return options.renderDpi.toFloat()

        val requested = options.renderDpi.toFloat()
        val longSidePixels = longSidePoints / OcrOptions.PDF_POINTS_PER_INCH * requested
        if (longSidePixels <= options.maxPageSide) return requested
        return options.maxPageSide * OcrOptions.PDF_POINTS_PER_INCH / longSidePoints
    }

    override fun close() {
        runCatching { document.close() }
    }
}
