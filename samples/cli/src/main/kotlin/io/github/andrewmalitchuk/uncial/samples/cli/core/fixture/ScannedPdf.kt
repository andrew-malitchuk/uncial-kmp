package io.github.andrewmalitchuk.uncial.samples.cli.core.fixture

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File

/**
 * Writes an image-only PDF: every page is a bitmap, so there is no text layer to read.
 *
 * The pages deliberately contain a large heading, a smaller subheading, ordinary body
 * text, a repeated running header and a page number, so that `DocumentStructure` has
 * something real to reconstruct.
 */
internal fun writeScannedPdf(file: File, pageCount: Int) {
    PDDocument().use { document ->
        repeat(pageCount) { pageIndex ->
            val image = renderPage(pageIndex, pageCount)
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val pdImage = LosslessFactory.createFromImage(document, image)
            PDPageContentStream(document, page).use { content ->
                content.drawImage(
                    pdImage,
                    0f,
                    0f,
                    PDRectangle.A4.width,
                    PDRectangle.A4.height,
                )
            }
        }
        document.save(file)
    }
}
/** Renders one page of Ukrainian text into a bitmap at ~150 dpi. */
private fun renderPage(pageIndex: Int, pageCount: Int): BufferedImage {
    // A4 at 150 dpi. Rendering the fixture higher than the SDK's default 200 dpi read
    // would make recognition unrealistically easy.
    val width = 1240
    val height = 1754
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
        )
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color.BLACK

        val margin = 110
        var y = 120

        // Running header: repeats on every page, so chrome detection has something to find.
        g.font = font(22)
        g.drawString("Історія української мови — розділ ${pageIndex + 1}", margin, y)
        y += 90

        if (pageIndex == 0) {
            g.font = font(58)
            g.drawString("Уncial", margin, y)
            y += 90
            g.font = font(40)
            g.drawString("Розпізнавання тексту", margin, y)
            y += 80
        }

        g.font = font(34)
        g.drawString("Розділ ${pageIndex + 1}. Письмо та шрифт", margin, y)
        y += 70

        g.font = font(24)
        for (line in bodyLines(pageIndex, pageCount)) {
            g.drawString(line, margin, y)
            y += 40
        }

        // A lone page number at the bottom: the other kind of chrome.
        g.font = font(22)
        val number = "${pageIndex + 1}"
        g.drawString(number, width / 2, height - 90)
    } finally {
        g.dispose()
    }
    return image
}

/**
 * A font that actually has Cyrillic glyphs.
 *
 * The logical `SansSerif` family maps to something with Cyrillic coverage on macOS and
 * most Linux distributions; a font without it would render Ukrainian as boxes and the
 * fixture would silently test nothing.
 */
private fun font(size: Int): Font = Font("SansSerif", Font.PLAIN, size)

private fun bodyLines(pageIndex: Int, pageCount: Int): List<String> = when (pageIndex) {
    0 -> listOf(
        "Унціал — це маюскульне книжкове письмо, яким переписували",
        "рукописи з четвертого по восьме століття. Його літери округлі",
        "й окремі, а не злиті, тому їх легко читати навіть через півтори",
        "тисячі років після написання.",
        "",
        "Саме тому назва підходить бібліотеці розпізнавання: усе тут",
        "про те, щоб текст можна було прочитати.",
    )
    1 -> listOf(
        "Скановані документи не мають текстового шару, тому єдиний",
        "спосіб дістати з них текст — оптичне розпізнавання. Рушій",
        "повертає рядки з координатами та впевненістю.",
        "",
        "Українська мова записується кирилицею, і це відразу відсікає",
        "частину рушіїв: ML Kit моделі для кирилиці не має зовсім.",
    )
    else -> listOf(
        "Реконструкція структури перетворює геометрію назад у сенс:",
        "рядок, більший за медіану, стає заголовком, а вертикальний",
        "проміжок означає новий абзац.",
        "",
        "Колонтитули та номери сторінок відкидаються, бо вони",
        "повторюються і не є частиною тексту. Page ${pageIndex + 1} of $pageCount.",
    )
}
