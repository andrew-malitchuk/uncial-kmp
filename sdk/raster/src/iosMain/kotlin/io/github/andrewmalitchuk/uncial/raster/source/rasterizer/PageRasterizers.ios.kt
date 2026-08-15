package io.github.andrewmalitchuk.uncial.raster.source.rasterizer

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import kotlin.math.roundToInt
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.kPDFDisplayBoxCropBox

@OptIn(ExperimentalForeignApi::class)
public actual fun createPageRasterizer(logger: OcrLogger): PageRasterizer =
    IosPageRasterizer(logger)

/** Rasterizes with PDFKit — a system framework, so zero third-party dependencies. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPageRasterizer(private val logger: OcrLogger) : PageRasterizer {

    // Parsing and rendering are as blocking here as they are on the other platforms, and
    // the caller is typically SwiftUI -- i.e. the main thread. Android and the JVM both
    // dispatch; iOS was the odd one out.
    override suspend fun open(bytes: ByteArray): RasterizedDocument = withContext(Dispatchers.Default) {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        // PDFKit's initialiser is declared without a nullability annotation, so Kotlin
        // types it as non-null and an `?: throw` here is dead code -- while PDFKit really
        // does return nil for input it cannot parse. Typing the local as nullable keeps the
        // check alive, and the page count catches a document that opens but is unusable.
        val opened: PDFDocument? = PDFDocument(data = data)
        val document = opened ?: throw OcrError.InvalidInput("not a readable PDF")
        if (document.pageCount.toInt() <= 0) {
            throw OcrError.InvalidInput("PDF contains no pages")
        }
        IosRasterizedDocument(document, logger)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosRasterizedDocument(
    private val document: PDFDocument,
    private val logger: OcrLogger,
) : RasterizedDocument {

    override val pageCount: Int = document.pageCount.toInt()

    override suspend fun rasterize(pageIndex: Int, options: OcrOptions): Raster = withContext(Dispatchers.Default) {
        // PDFKit hands back autoreleased temporaries per page; across a 300-page document
        // they would otherwise accumulate until the enclosing pool drains.
        autoreleasepool {
            currentCoroutineContext().ensureActive()
            rasterizePage(pageIndex, options)
        }
    }

    private fun rasterizePage(pageIndex: Int, options: OcrOptions): Raster {
        val page: PDFPage = document.pageAtIndex(pageIndex.toULong())
            ?: throw OcrError.RenderFailed(pageIndex)

        // The crop box, matching PDFBox's PDFRenderer on the JVM and pdfium on Android.
        // For the overwhelming majority of PDFs it equals the media box; where it does
        // not, the media box would make this platform report a different page size than
        // the other two for the same file, which the one-coordinate-model rule forbids.
        val bounds = page.boundsForBox(kPDFDisplayBoxCropBox)
        val pointsWidth = bounds.useContents { size.width }
        val pointsHeight = bounds.useContents { size.height }
        val scale = scaleFor(pointsWidth, pointsHeight, options)
        val width = (pointsWidth * scale).coerceAtLeast(1.0)
        val height = (pointsHeight * scale).coerceAtLeast(1.0)
        logger.debug("rasterize page $pageIndex at ${width}x$height (scale $scale)")

        // thumbnailOfSize renders the page at an arbitrary size — it is PDFKit's
        // rasterizer, not a preview cache.
        val image = page.thumbnailOfSize(CGSizeMake(width, height), kPDFDisplayBoxCropBox)
        val cgImage = image.CGImage ?: throw OcrError.RenderFailed(pageIndex)
        // scaleFor clamps against maxPageSide, so this is the resolution actually
        // rendered rather than the one requested.
        return Raster(cgImage, sourceDpi = (scale * OcrOptions.PDF_POINTS_PER_INCH.toDouble()).roundToInt())
    }

    /**
     * Unlike the POC's fixed 2x, the scale is derived from [OcrOptions.renderDpi] so iOS
     * matches Android and the JVM instead of quietly rendering at a third resolution.
     * (PLAN.md §5.8)
     */
    private fun scaleFor(pointsWidth: Double, pointsHeight: Double, options: OcrOptions): Double {
        var scale = options.renderScale.toDouble()
        val longSide = maxOf(pointsWidth, pointsHeight) * scale
        if (longSide > options.maxPageSide) scale *= options.maxPageSide / longSide
        return scale
    }

    override fun close() {
        // PDFDocument is released with this wrapper.
    }
}
