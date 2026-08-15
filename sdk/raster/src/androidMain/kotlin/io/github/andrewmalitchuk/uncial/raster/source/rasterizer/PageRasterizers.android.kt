package io.github.andrewmalitchuk.uncial.raster.source.rasterizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

public actual fun createPageRasterizer(logger: OcrLogger): PageRasterizer =
    AndroidPageRasterizer(logger)

/**
 * Rasterizes with the framework's [PdfRenderer] — no third-party dependency, and nothing
 * to ship in the AAR.
 */
private class AndroidPageRasterizer(private val logger: OcrLogger) : PageRasterizer {

    override suspend fun open(bytes: ByteArray): RasterizedDocument {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        return withContext(Dispatchers.IO) {
            // PdfRenderer needs a seekable file descriptor, so the bytes have to land on
            // disk. java.io.tmpdir is the app's cache directory on Android, which is why
            // this works without a Context.
            // Created first, then written: if writeBytes fails -- and the reason it fails
            // is a full disk -- the file still exists and has to be cleaned up, which a
            // single runCatching around both steps could not do.
            val file = try {
                File.createTempFile("uncial", ".pdf")
            } catch (cause: Throwable) {
                throw OcrError.RenderFailed(cause = cause)
            }
            try {
                file.writeBytes(bytes)
            } catch (cause: Throwable) {
                file.delete()
                throw OcrError.RenderFailed(cause = cause)
            }

            val descriptor = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrElse {
                file.delete()
                throw OcrError.RenderFailed(cause = it)
            }

            val renderer = runCatching { PdfRenderer(descriptor) }.getOrElse {
                descriptor.close()
                file.delete()
                // PdfRenderer throws for encrypted and malformed files alike.
                throw OcrError.InvalidInput("not a readable PDF", it)
            }
            AndroidRasterizedDocument(renderer, descriptor, file, logger)
        }
    }
}

private class AndroidRasterizedDocument(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val tempFile: File,
    private val logger: OcrLogger,
) : RasterizedDocument {

    // PdfRenderer allows exactly one open page at a time and is not thread-safe.
    private val lock = Mutex()

    override val pageCount: Int = renderer.pageCount

    override suspend fun rasterize(pageIndex: Int, options: OcrOptions): Raster =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching { renderPage(pageIndex, options) }
                    .getOrElse { throw asRenderFailure(it, pageIndex) }
            }
        }

    private fun renderPage(pageIndex: Int, options: OcrOptions): Raster =
        renderer.openPage(pageIndex).use { page ->
            val scale = scaleFor(page.width, page.height, options)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            logger.debug("rasterize page $pageIndex at ${width}x$height (scale $scale)")

            // PdfRenderer only renders into ARGB_8888, so OcrOptions.rasterColor cannot be
            // honoured here; the memory guard is maxPageSide instead. Tesseract binarizes
            // the bitmap itself, so the extra channels cost memory but not accuracy.
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // Scans assume paper: an unpainted bitmap is transparent black, which
            // binarizes to a solid page and recognizes as nothing at all.
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(
                bitmap,
                null,
                Matrix().apply { setScale(scale, scale) },
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )
            // The scale is clamped by maxPageSide, so the dpi that reaches the pixels is
            // not necessarily options.renderDpi. Carry the real one.
            Raster(bitmap, sourceDpi = (scale * OcrOptions.PDF_POINTS_PER_INCH).roundToInt())
        }

    /**
     * The scale to render at: [OcrOptions.renderDpi] relative to PDF's 72 dpi user space,
     * reduced further if that would exceed [OcrOptions.maxPageSide].
     */
    private fun scaleFor(pageWidth: Int, pageHeight: Int, options: OcrOptions): Float {
        var scale = options.renderScale
        val longSide = maxOf(pageWidth, pageHeight) * scale
        if (longSide > options.maxPageSide) scale *= options.maxPageSide / longSide
        return scale
    }

    private fun asRenderFailure(cause: Throwable, pageIndex: Int): Throwable = when (cause) {
        is OcrError -> cause
        is OutOfMemoryError -> OcrError.RenderFailed(
            pageIndex = pageIndex,
            message = "out of memory rasterizing page $pageIndex; lower renderDpi or maxPageSide",
            cause = cause,
        )
        else -> OcrError.RenderFailed(pageIndex = pageIndex, cause = cause)
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        tempFile.delete()
    }
}
