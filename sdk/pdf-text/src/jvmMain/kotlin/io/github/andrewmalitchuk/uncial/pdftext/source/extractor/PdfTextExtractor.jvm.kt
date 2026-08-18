package io.github.andrewmalitchuk.uncial.pdftext.source.extractor

import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.pdftext.core.assembly.DigitalPageAssembly
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

public actual fun pdfTextExtractor(logger: OcrLogger): DigitalTextExtractor =
    JvmPdfTextExtractor(logger)

/** Reads the text layer with PDFBox, one page at a time so cancellation works. */
private class JvmPdfTextExtractor(private val logger: OcrLogger) : DigitalTextExtractor {

    override val id: String = PDF_TEXT_EXTRACTOR_ID

    override suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument? {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        return withContext(Dispatchers.IO) {
            val document = runCatching { Loader.loadPDF(bytes) }
                .getOrElse { throw OcrError.InvalidInput("not a readable PDF", it) }
            document.use { pdf ->
                val pages = (0 until pdf.numberOfPages).map { pageIndex ->
                    currentCoroutineContext().ensureActive()
                    // The scale is per page, not per document: maxPageSide clamps it
                    // against the page's own size, so one document can legitimately use
                    // two different scales.
                    readPage(pdf, pageIndex, options)
                }
                logger.debug(
                    "digital layer: ${pages.count(DigitalPageAssembly::hasUsableText)}" +
                        "/${pages.size} pages have usable text",
                )
                DigitalPageAssembly.assemble(pages)
            }
        }
    }

    private fun readPage(
        pdf: org.apache.pdfbox.pdmodel.PDDocument,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage {
        // The crop box, not the media box: LegacyPDFStreamEngine expresses every
        // TextPosition relative to the crop box's lower-left corner, and PDFRenderer
        // rasterizes the crop box too. Reporting a media-box size here would put the page
        // and the boxes drawn on it in two different frames.
        val page = pdf.getPage(pageIndex)
        val cropBox = page.cropBox
        // Both renderers swap the raster's sides for a quarter-turn page, and the
        // direction-adjusted TextPositions are expressed in that same turned frame -- so
        // the reported size has to turn with them, or boxes legally run off the page.
        val quarterTurned = page.rotation == 90 || page.rotation == 270
        val pageWidth = if (quarterTurned) cropBox.height else cropBox.width
        val pageHeight = if (quarterTurned) cropBox.width else cropBox.height
        // Not options.renderScale: the rasterizer clamps against maxPageSide, and a
        // document whose pages come partly from here and partly from OCR must report one
        // coordinate scale, not two.
        val scale = options.scaleForPage(pageWidth, pageHeight)
        val lines = mutableListOf<OcrLine>()
        val stripper = object : PDFTextStripper() {
            // writeString is not a per-line callback. With sortByPosition it fires once
            // per "word", and what counts as a word depends on how the PDF encodes
            // spacing: text drawn with real space glyphs arrives a line at a time, text
            // positioned with Td/TJ offsets arrives a word at a time. Buffering here and
            // flushing on the line separator is what makes OcrPage.lines actually lines.
            private val pending = mutableListOf<Pair<String, List<TextPosition>>>()

            override fun writeString(text: String, textPositions: List<TextPosition>) {
                if (text.isNotBlank() && textPositions.isNotEmpty()) {
                    pending += text to textPositions
                }
                super.writeString(text, textPositions)
            }

            override fun writeLineSeparator() {
                flushLine()
                super.writeLineSeparator()
            }

            override fun endArticle() {
                // An article's last line is followed by writeParagraphEnd/endArticle, not
                // by a line separator, so without this the buffer carries across the
                // boundary and the next column's first line is appended to it.
                flushLine()
                super.endArticle()
            }

            override fun endPage(page: org.apache.pdfbox.pdmodel.PDPage) {
                // The last line of a page is not followed by a separator.
                flushLine()
                super.endPage(page)
            }

            private fun flushLine() {
                if (pending.isEmpty()) return
                val text = pending.joinToString(wordSeparator) { it.first }
                val positions = pending.flatMap { it.second }
                pending.clear()
                lines += toLine(text, positions, scale)
            }
        }.apply {
            // Without this, writeString is called in content-stream order rather than
            // reading order, and the geometry no longer corresponds to lines.
            sortByPosition = true
            // PDFTextStripper counts pages from 1.
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        // The Writer output is discarded: the geometry is collected in writeString.
        stripper.writeText(pdf, Writer.nullWriter())

        return OcrPage(
            index = pageIndex,
            size = Size(pageWidth * scale, pageHeight * scale),
            lines = lines,
            source = ExtractionSource.DigitalTextLayer,
        )
    }

    /**
     * Converts one stripped line into an [OcrLine].
     *
     * `xDirAdj`/`yDirAdj` are PDFBox's direction-adjusted coordinates and are already
     * top-down, which is the convention Uncial uses — so no flip is needed here, unlike
     * on the iOS and Vision paths. The **anchor** still needs care: `yDirAdj` is the
     * baseline, not the top of the glyphs, so the box has to be lifted by the glyph
     * height or it sits a cap-height below its own text. PDFTextStripper does the same
     * subtraction internally when it computes a line's top.
     */
    private fun toLine(text: String, positions: List<TextPosition>, scale: Float): OcrLine {
        val left = positions.minOf { it.xDirAdj }
        val right = positions.maxOf { it.xDirAdj + it.widthDirAdj }
        val top = positions.minOf { it.yDirAdj - it.heightDir }
        val bottom = positions.maxOf { it.yDirAdj }
        val fontSize = positions.map { it.fontSizeInPt.toDouble() }.average().toFloat()
        return OcrLine(
            text = text.trim(),
            box = BoundingBox(
                left = left * scale,
                top = top * scale,
                width = (right - left) * scale,
                height = (bottom - top) * scale,
            ),
            // A real font metric, not the line-height proxy OCR has to use.
            fontSize = fontSize * scale,
            // The digital layer is not a guess.
            confidence = Confidence.Certain,
        )
    }
}
