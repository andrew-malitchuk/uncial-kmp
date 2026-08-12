package io.github.andrewmalitchuk.uncial.model.source.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OcrOptionsTest {

    @Test
    fun `defaults collapse the POC's three render resolutions into one`() {
        val options = OcrOptions()
        assertEquals(200, options.renderDpi)
        assertEquals(2600, options.maxPageSide)
        // 200 dpi against PDF's 72 dpi user space.
        assertEquals(200f / 72f, options.renderScale)
    }

    @Test
    fun `impossible options are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> { OcrOptions(languages = emptyList()) }
        assertFailsWith<IllegalArgumentException> { OcrOptions(renderDpi = 10) }
        assertFailsWith<IllegalArgumentException> { OcrOptions(renderDpi = 5_000) }
        assertFailsWith<IllegalArgumentException> { OcrOptions(maxPageSide = 8) }
        assertFailsWith<IllegalArgumentException> { OcrOptions(pageRange = -3..2) }
        // A reversed range selects nothing; rejecting it beats returning an empty document.
        assertFailsWith<IllegalArgumentException> { OcrOptions(pageRange = 5..2) }
        assertFailsWith<IllegalArgumentException> { OcrOptions(pageRange = IntRange.EMPTY) }
    }

    @Test
    fun `a range running past the end is still legal because it gets clamped`() {
        assertEquals(1..99, OcrOptions(pageRange = 1..99).pageRange)
        assertEquals(4..4, OcrOptions(pageRange = 4..4).pageRange)
    }
}
