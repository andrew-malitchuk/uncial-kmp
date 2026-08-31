package io.github.andrewmalitchuk.uncial.samples.cli.core.fixture

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.File
import java.io.IOException

/**
 * Generates a **digital** PDF: real text, real fonts, a real text layer.
 *
 * The counterpart to the scanned fixture. Uncial should take the digital fast path here
 * and never start an OCR engine — which is worth being able to demonstrate, because it is
 * the difference between milliseconds and seconds per page.
 */
internal fun writeDigitalPdf(file: File, pageCount: Int, ascii: Boolean = false) {
    PDDocument().use { document ->
        // --ascii forces Helvetica and English text: a small, byte-stable PDF suitable for
        // embedding in a test, with no dependency on which fonts the machine has.
        val font = if (ascii) PDType1Font(Standard14Fonts.FontName.HELVETICA) else cyrillicFont(document)
        val cyrillicAvailable = font is PDType0Font
        repeat(pageCount) { pageIndex ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                var y = PDRectangle.A4.height - 80f
                content.write(font, 24f, 60f, y, heading(pageIndex, cyrillicAvailable))
                y -= 50f
                for (line in body(pageIndex, cyrillicAvailable)) {
                    content.write(font, 12f, 60f, y, line)
                    y -= 20f
                }
            }
        }
        document.save(file)
    }
    println("wrote ${file.absolutePath} — $pageCount page(s), WITH a text layer")
    println("Uncial should report source=DigitalTextLayer and never start an engine.")
}

private fun PDPageContentStream.write(font: PDFont, size: Float, x: Float, y: Float, text: String) {
    if (text.isEmpty()) return
    beginText()
    setFont(font, size)
    newLineAtOffset(x, y)
    showText(text)
    endText()
}

/**
 * A font that can encode Cyrillic, or Helvetica if none is installed.
 *
 * PDFBox's standard 14 fonts are WinAnsi-encoded and cannot represent Ukrainian at all —
 * `showText` throws rather than degrading. So a real TrueType font has to be embedded, and
 * if the machine has none of the usual ones the fixture falls back to English text and
 * says so, instead of failing.
 */
private fun cyrillicFont(document: PDDocument): PDFont {
    val candidates = listOf(
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    )
    for (path in candidates) {
        val candidate = File(path)
        if (!candidate.isFile) continue
        try {
            return PDType0Font.load(document, candidate)
        } catch (cause: IOException) {
            // A font that is present but unloadable is a different situation from a font
            // that is absent, and it is the one that would otherwise be a mystery: the
            // fixture would come out in English with no hint as to why.
            println("warning: $path is present but could not be embedded: ${cause.message}")
        }
    }
    println("warning: no Cyrillic-capable TrueType font found; writing English text instead")
    return PDType1Font(Standard14Fonts.FontName.HELVETICA)
}

private fun heading(pageIndex: Int, cyrillic: Boolean): String = if (cyrillic) {
    "Розділ ${pageIndex + 1}. Цифровий текстовий шар"
} else {
    "Chapter ${pageIndex + 1}. Digital text layer"
}

private fun body(pageIndex: Int, cyrillic: Boolean): List<String> = if (cyrillic) {
    listOf(
        "Цей PDF містить справжній текстовий шар, тому OCR не потрібен.",
        "Uncial має прочитати текст напряму і повернути source=DigitalTextLayer.",
        "",
        "Сторінка ${pageIndex + 1}: цифровий шлях приблизно в тисячу разів швидший",
        "за розпізнавання і при цьому точний, а не приблизний.",
    )
} else {
    listOf(
        "This PDF carries a real text layer, so OCR is unnecessary.",
        "Uncial should read it directly and report source=DigitalTextLayer.",
        "",
        "Page ${pageIndex + 1}: the digital path is roughly a thousand times faster",
        "than recognition, and exact rather than approximate.",
    )
}
