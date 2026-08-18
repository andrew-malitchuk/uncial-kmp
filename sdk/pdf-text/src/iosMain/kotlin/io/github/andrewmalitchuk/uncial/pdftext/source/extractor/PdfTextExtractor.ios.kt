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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.attribute
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.PDFSelection
import platform.PDFKit.kPDFDisplayBoxCropBox
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont

@OptIn(ExperimentalForeignApi::class)
public actual fun pdfTextExtractor(logger: OcrLogger): DigitalTextExtractor =
    IosPdfTextExtractor(logger)

/**
 * Reads the text layer with PDFKit — a system framework, so the digital path costs iOS
 * nothing in binary size, unlike Android's PDFBox-Android.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosPdfTextExtractor(private val logger: OcrLogger) : DigitalTextExtractor {

    override val id: String = PDF_TEXT_EXTRACTOR_ID

    override suspend fun extract(bytes: ByteArray, options: OcrOptions): OcrDocument? {
        if (bytes.isEmpty()) throw OcrError.InvalidInput("empty input")
        // PDFKit runs on whichever thread calls it, and parsing a document plus walking
        // every page's selection is not something a SwiftUI consumer's main thread can
        // afford. The Android and JVM extractors dispatch for the same reason; iOS was the
        // odd one out.
        return withContext(Dispatchers.IO) {
            val data = bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
            // See the note in PageRasterizers.ios.kt: PDFKit's initialiser is unannotated,
            // so the null check only survives if the local is typed nullable.
            val opened: PDFDocument? = PDFDocument(data = data)
            val document = opened ?: throw OcrError.InvalidInput("not a readable PDF")
            if (document.pageCount.toInt() <= 0) {
                throw OcrError.InvalidInput("PDF contains no pages")
            }

            val pages = (0 until document.pageCount.toInt()).map { pageIndex ->
                currentCoroutineContext().ensureActive()
                // Every page produces autoreleased PDFKit temporaries -- the page
                // selection, its per-line selections, the attributed string behind
                // pointSize(). Nothing drains a pool between iterations, so without one
                // here a 300-page document keeps all of them alive until extract()
                // returns.
                autoreleasepool {
                    val page = document.pageAtIndex(pageIndex.toULong())
                    if (page == null) {
                        OcrPage(
                            index = pageIndex,
                            size = Size(0f, 0f),
                            lines = emptyList(),
                            source = ExtractionSource.DigitalTextLayer,
                        )
                    } else {
                        readPage(page, pageIndex, options)
                    }
                }
            }
            logger.debug(
                "digital layer: ${pages.count(DigitalPageAssembly::hasUsableText)}" +
                    "/${pages.size} pages have usable text",
            )
            DigitalPageAssembly.assemble(pages)
        }
    }

    private fun readPage(page: PDFPage, pageIndex: Int, options: OcrOptions): OcrPage {
        val bounds = page.boundsForBox(kPDFDisplayBoxCropBox)
        val pageWidth = bounds.useContents { size.width }.toFloat()
        val pageHeight = bounds.useContents { size.height }.toFloat()
        // Per page and clamped, matching the rasterizer: options.renderScale alone would
        // put a clamped page's digital text in a different scale from its OCR'd siblings.
        val scale = options.scaleForPage(pageWidth, pageHeight)

        // A selection over the whole page, split into lines: PDFKit's way of exposing the
        // text layer with geometry.
        val lines = page.selectionForRect(bounds)
            ?.selectionsByLine()
            ?.mapNotNull { it as? PDFSelection }
            ?.mapNotNull { selection -> selection.toOcrLine(page, pageHeight, scale) }
            .orEmpty()

        return OcrPage(
            index = pageIndex,
            size = Size(pageWidth * scale, pageHeight * scale),
            lines = lines,
            source = ExtractionSource.DigitalTextLayer,
        )
    }

    private fun PDFSelection.toOcrLine(
        page: PDFPage,
        pageHeight: Float,
        scale: Float,
    ): OcrLine? {
        val text = string?.trim().orEmpty()
        if (text.isEmpty()) return null
        val rect = boundsForPage(page)
        val box = rect.useContents {
            val left = origin.x.toFloat()
            val bottom = origin.y.toFloat()
            val boxWidth = size.width.toFloat()
            val boxHeight = size.height.toFloat()
            BoundingBox(
                left = left * scale,
                // PDF user space has its origin at the bottom-left; Uncial is top-down.
                top = (pageHeight - (bottom + boxHeight)) * scale,
                width = boxWidth * scale,
                height = boxHeight * scale,
            )
        }
        return OcrLine(
            text = text,
            box = box,
            // A real point size, scaled the same way the geometry is -- not the line-height
            // proxy. This matters because DocumentStructure compares fontSize against the
            // document median: a proxy on iOS and a true metric on Android would make the
            // same document reconstruct differently on the two platforms.
            fontSize = (pointSize()?.times(scale)) ?: box.height,
            confidence = Confidence.Certain,
        )
    }

    /**
     * The point size of the font this selection is set in, if PDFKit knows it.
     *
     * `PDFSelection.attributedString` carries the real font attributes, which is the only
     * route to a type size on iOS -- the selection itself exposes none. Returns `null` for
     * a selection with no font attribute, and the caller falls back to the line height.
     */
    private fun PDFSelection.pointSize(): Float? {
        val attributed = attributedString ?: return null
        if (attributed.string.isEmpty()) return null
        val font = attributed.attribute(
            attrName = NSFontAttributeName,
            atIndex = 0u,
            effectiveRange = null,
        ) as? UIFont ?: return null
        return font.pointSize.toFloat().takeIf { it > 0f }
    }
}
