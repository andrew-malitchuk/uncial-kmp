package io.github.andrewmalitchuk.uncial.pdftext.source.extractor

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.github.andrewmalitchuk.uncial.core.source.android.UncialContext
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
import java.io.StringWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

public actual fun pdfTextExtractor(logger: OcrLogger): DigitalTextExtractor =
    AndroidPdfTextExtractor(logger)

/**
 * Reads the text layer with PDFBox-Android.
 *
 * The same approach as the JVM path, against a fork with different package names — which
 * is why the two implementations look alike but cannot be shared.
 *
 * This is also the module that makes the digital path expensive on Android: PDFBox-Android
 * is several MB, which is exactly why `uncial-pdf-text` is optional and stays out of the
 * Fat AAR. (PLAN.md §8.2)
 */
private class AndroidPdfTextExtractor(private val logger: OcrLogger) : DigitalTextExtractor {

    override val id: String = PDF_TEXT_EXTRACTOR_ID

    override suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument? {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        return withContext(Dispatchers.IO) {
            ensureResourceLoaderInitialized()
            val document = runCatching { PDDocument.load(bytes) }
                .getOrElse { throw OcrError.InvalidInput("not a readable PDF", it) }
            document.use { pdf ->
                val pages = (0 until pdf.numberOfPages).map { pageIndex ->
                    currentCoroutineContext().ensureActive()
                    // Per page, not per document: maxPageSide clamps the scale against the
                    // page's own size. See the JVM extractor.
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

    /**
     * PDFBox-Android needs a one-shot resource load before any document is parsed, or font
     * handling fails at parse time with an unhelpful error.
     *
     * The POC required the consumer to call this from `MainActivity`. Here it happens on
     * first use, from the context `androidx.startup` already captured.
     */
    private fun ensureResourceLoaderInitialized() {
        if (resourceLoaderReady) return
        val context = UncialContext.get() ?: throw OcrError.EngineInit(
            "no Android Context: androidx.startup did not run, so PDFBox-Android cannot " +
                "load its resources. Call UncialContext.install(applicationContext).",
        )
        PDFBoxResourceLoader.init(context)
        resourceLoaderReady = true
    }

    private fun readPage(pdf: PDDocument, pageIndex: Int, options: OcrOptions): OcrPage {
        // The crop box, not the media box: TextPositions are expressed relative to it,
        // and so is what the rasterizer draws. See the JVM extractor for the long version.
        val page = pdf.getPage(pageIndex)
        val cropBox = page.cropBox
        // Both renderers swap the raster's sides for a quarter-turn page, and the
        // direction-adjusted TextPositions are expressed in that same turned frame -- so
        // the reported size has to turn with them, or boxes legally run off the page.
        val quarterTurned = page.rotation == 90 || page.rotation == 270
        val pageWidth = if (quarterTurned) cropBox.height else cropBox.width
        val pageHeight = if (quarterTurned) cropBox.width else cropBox.height
        // Not options.renderScale -- the rasterizer clamps, and both halves of a Mixed
        // document have to land in one coordinate scale.
        val scale = options.scaleForPage(pageWidth, pageHeight)
        val lines = mutableListOf<OcrLine>()
        val stripper = object : PDFTextStripper() {
            // writeString fires per "word", not per line, whenever the PDF positions its
            // words with Td/TJ offsets rather than real space glyphs. Buffer and flush on
            // the line separator so OcrPage.lines holds lines.
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

            override fun endPage(page: PDPage) {
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
            sortByPosition = true
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        stripper.writeText(pdf, StringWriter())

        return OcrPage(
            index = pageIndex,
            size = Size(pageWidth * scale, pageHeight * scale),
            lines = lines,
            source = ExtractionSource.DigitalTextLayer,
        )
    }

    /**
     * `yDirAdj` is the **baseline**, not the top of the glyphs, so the box is lifted by
     * the glyph height — otherwise every box sits a cap-height below its own text.
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
            fontSize = fontSize * scale,
            confidence = Confidence.Certain,
        )
    }

    private companion object {
        @Volatile
        var resourceLoaderReady: Boolean = false
    }
}
