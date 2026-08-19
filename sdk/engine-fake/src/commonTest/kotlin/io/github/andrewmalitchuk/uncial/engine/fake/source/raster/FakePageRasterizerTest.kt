package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FakePageRasterizerTest {

    @Test
    fun `it ignores the bytes entirely`() = runTest {
        // The contract a consumer relies on: pass ByteArray(0) and still get pages.
        val document = FakePageRasterizer(pageCount = 3).open(ByteArray(0))

        assertEquals(3, document.pageCount)
    }

    @Test
    fun `rasters carry the configured page size`() = runTest {
        val document = FakePageRasterizer(pageCount = 1, pageWidth = 640, pageHeight = 480)
            .open(ByteArray(0))

        val raster = document.rasterize(pageIndex = 0, options = OcrOptions())
        assertEquals(640, raster.width)
        assertEquals(480, raster.height)
        raster.release()
    }

    @Test
    fun `failOnPage fails that page and no other`() = runTest {
        val document = FakePageRasterizer(pageCount = 2, failOnPage = 1).open(ByteArray(0))

        document.rasterize(pageIndex = 0, options = OcrOptions()).release()
        assertFailsWith<OcrError.RenderFailed> {
            document.rasterize(pageIndex = 1, options = OcrOptions())
        }
    }
}
