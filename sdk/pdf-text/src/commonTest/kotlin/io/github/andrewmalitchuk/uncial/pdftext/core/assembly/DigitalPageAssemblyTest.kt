package io.github.andrewmalitchuk.uncial.pdftext.core.assembly

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gate that decides whether OCR runs at all.
 *
 * Both directions are failures a user sees: too strict and a real text layer is re-OCR'd
 * for seconds per page; too lax and a stamped page number suppresses OCR of a whole scan.
 */
class DigitalPageAssemblyTest {

    @Test
    fun `a page with only a stamped page number has no usable text`() {
        assertFalse(DigitalPageAssembly.hasUsableText(page(0, "41")))
    }

    @Test
    fun `a page of prose has usable text`() {
        assertTrue(DigitalPageAssembly.hasUsableText(page(0, "Розділ перший про довгі рядки")))
    }

    @Test
    fun `the threshold counts trimmed characters`() {
        // 23 characters plus padding: whitespace must not buy its way over the line.
        val justUnder = "a".repeat(23)
        assertFalse(DigitalPageAssembly.hasUsableText(page(0, "   $justUnder   ")))
        assertTrue(DigitalPageAssembly.hasUsableText(page(0, "   ${justUnder}b   ")))
    }

    @Test
    fun `the threshold counts every line on the page`() {
        val page = page(0, "twelve chars", "and twelve more")
        assertTrue(DigitalPageAssembly.hasUsableText(page))
    }

    @Test
    fun `no usable page at all assembles to null`() {
        assertNull(DigitalPageAssembly.assemble(listOf(page(0, "1"), page(1, ""))))
    }

    @Test
    fun `an unusable page is kept but emptied`() {
        val document = DigitalPageAssembly.assemble(
            listOf(page(0, PROSE), page(1, "42")),
        )

        assertEquals(2, document?.pageCount, "every page must survive so the runtime can OCR exactly one")
        assertEquals(1, document?.pages?.get(0)?.lines?.size)
        assertEquals(emptyList(), document?.pages?.get(1)?.lines)
    }

    @Test
    fun `an assembled document reports the digital text layer`() {
        val document = DigitalPageAssembly.assemble(listOf(page(0, PROSE)))

        assertEquals(ExtractionSource.DigitalTextLayer, document?.source)
    }

    private fun page(index: Int, vararg text: String) = OcrPage(
        index = index,
        size = Size(WIDTH, HEIGHT),
        lines = text.filter { it.isNotEmpty() }.mapIndexed { line, content ->
            OcrLine(
                text = content,
                box = BoundingBox(left = 0f, top = line * 20f, width = WIDTH, height = 18f),
                fontSize = 12f,
            )
        },
        source = ExtractionSource.DigitalTextLayer,
    )

    private companion object {
        const val PROSE = "Рядок тексту достатньої довжини"
        const val WIDTH = 600f
        const val HEIGHT = 800f
    }
}
