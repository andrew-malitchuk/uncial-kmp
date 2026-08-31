package io.github.andrewmalitchuk.uncial.samples.cli.core.args

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArgumentsTest {

    @Test
    fun `the first bare argument is the path`() {
        assertEquals("/tmp/scan.pdf", listOf("ocr", "/tmp/scan.pdf", "--words").drop(1).firstPathOrNull())
    }

    @Test
    fun `a flag is never mistaken for a path`() {
        assertNull(listOf("--words", "--quiet").firstPathOrNull())
    }

    @Test
    fun `a flag's value is the argument after it`() {
        assertEquals("300", listOf("/tmp/a.pdf", "--dpi", "300").valueOf("--dpi"))
    }

    @Test
    fun `an absent flag has no value`() {
        assertNull(listOf("/tmp/a.pdf").valueOf("--dpi"))
    }

    @Test
    fun `a trailing flag has no value rather than crashing`() {
        assertNull(listOf("/tmp/a.pdf", "--dpi").valueOf("--dpi"))
    }
}
