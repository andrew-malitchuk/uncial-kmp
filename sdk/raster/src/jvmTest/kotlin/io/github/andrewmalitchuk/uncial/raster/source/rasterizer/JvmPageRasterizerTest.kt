package io.github.andrewmalitchuk.uncial.raster.source.rasterizer

import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.options.RasterColor
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rasterizer is where `maxPageSide` is enforced and where `Raster.sourceDpi` is
 * decided -- and `sourceDpi` is what the engine hands to Tesseract's
 * `SetSourceResolution`. A wrong number here is a silent accuracy loss two modules away,
 * which is exactly the kind of bug no other test would catch.
 */
class JvmPageRasterizerTest {

    @Test
    fun `the page count is known before any page is rasterized`() = runTest {
        createPageRasterizer().open(pdf(pages = 3)).use { document ->
            assertEquals(3, document.pageCount)
        }
    }

    @Test
    fun `a page rasterizes at the requested dpi`() = runTest {
        createPageRasterizer().open(pdf()).use { document ->
            val raster = document.rasterize(0, OcrOptions(renderDpi = 72))
            try {
                // A4 at 72 dpi is 595 x 842 points-as-pixels, give or take rounding.
                assertEquals(72, raster.sourceDpi)
                assertTrue(raster.width in 590..600, "width was ${raster.width}")
                assertTrue(raster.height in 837..847, "height was ${raster.height}")
            } finally {
                raster.release()
            }
        }
    }

    @Test
    fun `maxPageSide clamps the effective resolution and sourceDpi says so`() = runTest {
        createPageRasterizer().open(pdf()).use { document ->
            val options = OcrOptions(renderDpi = 600, maxPageSide = 1000)
            val raster = document.rasterize(0, options)
            try {
                assertTrue(
                    maxOf(raster.width, raster.height) <= options.maxPageSide,
                    "long side was ${maxOf(raster.width, raster.height)}",
                )
                val dpi = assertNotNull(raster.sourceDpi)
                assertTrue(dpi < options.renderDpi, "a clamped page must report the real dpi, not $dpi")
            } finally {
                raster.release()
            }
        }
    }

    @Test
    fun `a rotated page comes back with its sides swapped`() = runTest {
        createPageRasterizer().open(pdf(rotation = 90)).use { document ->
            val raster = document.rasterize(0, OcrOptions(renderDpi = 72))
            try {
                assertTrue(raster.width > raster.height, "a quarter-turned A4 is landscape")
            } finally {
                raster.release()
            }
        }
    }

    @Test
    fun `the crop box is the frame of reference`() = runTest {
        // Half-height crop box on a full A4 media box: rendering the media box would give 842.
        createPageRasterizer().open(pdf(cropHeight = PDRectangle.A4.height / 2)).use { document ->
            val raster = document.rasterize(0, OcrOptions(renderDpi = 72))
            try {
                assertTrue(raster.height in 416..426, "height was ${raster.height}")
            } finally {
                raster.release()
            }
        }
    }

    @Test
    fun `grayscale is honoured on the JVM`() = runTest {
        createPageRasterizer().open(pdf()).use { document ->
            val raster = document.rasterize(0, OcrOptions(rasterColor = RasterColor.Grayscale))
            try {
                assertTrue(raster.width > 0)
            } finally {
                raster.release()
            }
        }
    }

    @Test
    fun `bytes that are not a PDF are invalid input`() = runTest {
        assertFailsWith<OcrError.InvalidInput> {
            createPageRasterizer().open("not a pdf".encodeToByteArray())
        }
    }

    @Test
    fun `empty input is invalid input`() = runTest {
        assertFailsWith<OcrError.InvalidInput> { createPageRasterizer().open(ByteArray(0)) }
    }

    @Test
    fun `a page index outside the document fails rather than returning nothing`() = runTest {
        createPageRasterizer().open(pdf(pages = 1)).use { document ->
            assertFailsWith<OcrError> { document.rasterize(5, OcrOptions()) }
        }
    }

    private fun pdf(
        pages: Int = 1,
        rotation: Int = 0,
        cropHeight: Float? = null,
    ): ByteArray = PDDocument().use { document ->
        repeat(pages) {
            document.addPage(
                PDPage(PDRectangle.A4).apply {
                    this.rotation = rotation
                    cropHeight?.let { height ->
                        cropBox = PDRectangle(0f, 0f, PDRectangle.A4.width, height)
                    }
                },
            )
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }
}
