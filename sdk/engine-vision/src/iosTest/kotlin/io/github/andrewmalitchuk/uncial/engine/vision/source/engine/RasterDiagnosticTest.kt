package io.github.andrewmalitchuk.uncial.engine.vision.source.engine

import io.github.andrewmalitchuk.uncial.engine.vision.core.fixture.SAMPLE_PDF_BYTES
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.raster.source.rasterizer.createPageRasterizer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake

/**
 * Checks that the iOS rasterizer actually produces ink.
 *
 * Separates "PDFKit rendered a blank page" from "Vision failed to read it" — two failures
 * that look identical from the outside and have completely different fixes.
 */
@OptIn(ExperimentalForeignApi::class)
class RasterDiagnosticTest {

    @Test
    fun `rasterizing the embedded PDF produces non-blank pixels`() = runTest {
        val options = OcrOptions()
        val document = createPageRasterizer().open(SAMPLE_PDF_BYTES)
        val stats = document.use { pdf ->
            val raster = pdf.rasterize(0, options)
            try {
                inkStats(raster.image, raster.width, raster.height)
            } finally {
                raster.release()
            }
        }
        println("RASTER ${stats.width}x${stats.height} dark=${stats.dark} light=${stats.light}")
        assertTrue(stats.light > 0, "page has no light pixels at all: $stats")
        assertTrue(stats.dark > 0, "page has no dark pixels — PDFKit rendered nothing: $stats")
    }

    private data class InkStats(val width: Int, val height: Int, val dark: Int, val light: Int)

    /** Redraws the image into a grayscale buffer and counts dark vs light pixels. */
    private fun inkStats(
        image: platform.CoreGraphics.CGImageRef,
        width: Int,
        height: Int,
    ): InkStats = memScoped {
        val colorSpace = CGColorSpaceCreateDeviceGray()
        val pixels = allocArray<kotlinx.cinterop.UByteVar>(width * height)
        val context = CGBitmapContextCreate(
            data = pixels,
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = width.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value,
        )
        try {
            checkNotNull(context) { "could not create a probe context" }
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                image,
            )
            var dark = 0
            var light = 0
            var index = 0
            val total = width * height
            while (index < total) {
                val value = pixels[index].toInt()
                if (value < 100) dark++ else if (value > 200) light++
                // Sampling every 7th pixel: enough to detect ink, ~7x faster.
                index += 7
            }
            InkStats(width, height, dark, light)
        } finally {
            context?.let { CGContextRelease(it) }
            CGColorSpaceRelease(colorSpace)
        }
    }
}
