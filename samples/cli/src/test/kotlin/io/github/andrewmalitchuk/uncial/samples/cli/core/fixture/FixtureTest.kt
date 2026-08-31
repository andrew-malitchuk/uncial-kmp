package io.github.andrewmalitchuk.uncial.samples.cli.core.fixture

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The harness's central claim: `fixture` produces a **scanned** PDF, not a generated one.
 *
 * If the scanned fixture ever grew a text layer, every `ocr` run against it would take the
 * digital fast path and prove nothing about recognition -- while still printing a perfect
 * result. This test is the only thing standing between that and a silent loss of coverage.
 */
class FixtureTest {

    @Test
    fun `the scanned fixture carries no text layer at all`() {
        val file = temporaryPdf()

        writeScannedPdf(file, pageCount = 2)

        assertEquals(2, pageCount(file))
        assertTrue(textOf(file).isBlank(), "a scanned fixture with text would silently skip OCR")
    }

    @Test
    fun `the digital fixture does carry one`() {
        val file = temporaryPdf()

        writeDigitalPdf(file, pageCount = 2, ascii = true)

        assertEquals(2, pageCount(file))
        assertTrue(textOf(file).isNotBlank(), "the fast-path fixture is the one WITH text")
    }

    @Test
    fun `the digital fixture is structurally interesting`() {
        val file = temporaryPdf()

        writeDigitalPdf(file, pageCount = 3, ascii = true)

        val text = textOf(file)
        // A heading, body text and a page number per page -- DocumentStructure needs
        // something to reconstruct, or the structure step is exercised by nothing.
        assertTrue(text.lines().count { it.isNotBlank() } >= 6, text)
    }

    private fun temporaryPdf(): File =
        File.createTempFile("uncial-fixture", ".pdf").also { it.deleteOnExit() }

    private fun pageCount(file: File): Int =
        Loader.loadPDF(file).use { it.numberOfPages }

    private fun textOf(file: File): String =
        Loader.loadPDF(file).use { PDFTextStripper().getText(it) }
}
