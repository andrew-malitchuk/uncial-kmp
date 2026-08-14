package io.github.andrewmalitchuk.uncial.core.source.raster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one raster that has to be constructible in an Android host unit test, where
 * `Bitmap.createBitmap` throws `not mocked`. If this test cannot run on a target, the
 * fake pipeline cannot either.
 */
class PlaceholderRasterTest {

    @Test
    fun `it carries the size it was given`() {
        val raster = placeholderRaster(width = 1654, height = 2339)

        assertEquals(1654, raster.width)
        assertEquals(2339, raster.height)
        raster.release()
    }

    @Test
    fun `it claims no source resolution`() {
        // Nothing rendered these pixels -- there are none -- so asserting a dpi would be a lie
        // the engine then passes to Tesseract.
        assertNull(placeholderRaster(width = 10, height = 10).sourceDpi)
    }

    @Test
    fun `releasing twice is harmless`() {
        val raster = placeholderRaster(width = 10, height = 10)

        raster.release()
        raster.release()
    }
}
