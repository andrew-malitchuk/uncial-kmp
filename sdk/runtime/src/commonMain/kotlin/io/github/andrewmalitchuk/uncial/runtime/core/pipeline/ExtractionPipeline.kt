package io.github.andrewmalitchuk.uncial.runtime.core.pipeline

import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import io.github.andrewmalitchuk.uncial.core.source.engine.DigitalTextExtractor
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.PageRasterizer
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.raster.RasterizedDocument
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Runs one extraction: digital layer first if asked for, then OCR for whatever is left.
 *
 * Emits progress per page and checks for cancellation at every page boundary, which is
 * what turns the POC's uninterruptible multi-minute call into something a UI can host.
 */
internal class ExtractionPipeline(
    private val rasterizer: PageRasterizer,
    private val digitalTextExtractor: DigitalTextExtractor?,
    private val recognizer: suspend () -> TextRecognizer,
    private val options: OcrOptions,
    private val logger: OcrLogger,
) {

    /** Extracts a PDF. */
    fun extract(bytes: ByteArray): Flow<OcrProgress> = flow {
        val digital = readDigitalLayer(bytes)
        // Coverage is judged over the pages the caller asked for, not over the whole
        // document. Judging it over all of them made `pageRange = 2..4` on a scan whose
        // first pages carry no text open the PDF for a rasterization it never used, and --
        // worse -- emit Started(Mixed) before a Done that reported DigitalTextLayer.
        val selected = digital?.pages?.selectedBy(options).orEmpty()
        // `all {}` is vacuously true on an empty list, so an extractor that reports a
        // document with no pages at all would otherwise "cover" everything and hand the
        // caller an empty result instead of running OCR. An empty selection therefore falls
        // through on purpose: `ocr` is the only place that knows the document's real page
        // count, so it decides whether the requested range was out of bounds, and both
        // paths reject it the same way instead of one erroring and the other succeeding
        // with an empty document.
        if (selected.isNotEmpty() && selected.all { it.lines.isNotEmpty() }) {
            // Every requested page had text: OCR is not needed at all, the ~1000x win.
            logger.debug("digital layer covers all ${selected.size} requested pages; skipping OCR")
            emitDocument(selected, ExtractionSource.DigitalTextLayer)
            return@flow
        }
        ocr(bytes, digital)
    }

    /**
     * Extracts a single image the caller already has — a camera frame, a scan, a
     * screenshot.
     *
     * Nearly free to support once rasterization and recognition are separate roles, which
     * is one of the reasons for the split. (PLAN.md §6.1, §11.6)
     */
    fun extract(raster: Raster): Flow<OcrProgress> = flow {
        emit(OcrProgress.Started(pageCount = 1, source = ExtractionSource.Ocr))
        currentCoroutineContext().ensureActive()
        val page = recognizer().recognize(raster, pageIndex = 0, options = options)
        emit(OcrProgress.Page(index = 0, completed = 1, of = 1, page = page))
        emit(OcrProgress.Done(OcrDocument(listOf(page), ExtractionSource.Ocr)))
    }

    private suspend fun readDigitalLayer(bytes: ByteArray): OcrDocument? {
        val extractor = digitalTextExtractor ?: return null
        if (!options.preferDigitalLayer) return null
        return extractor.extract(bytes, options)
    }

    /** Emits an already-selected set of pages as a complete extraction. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<OcrProgress>.emitDocument(
        pages: List<OcrPage>,
        source: ExtractionSource,
    ) {
        emit(OcrProgress.Started(pageCount = pages.size, source = source))
        pages.forEachIndexed { position, page ->
            currentCoroutineContext().ensureActive()
            emit(
                OcrProgress.Page(
                    index = page.index,
                    completed = position + 1,
                    of = pages.size,
                    page = page,
                ),
            )
        }
        emit(OcrProgress.Done(OcrDocument(pages, source)))
    }

    /**
     * Rasterizes and recognizes, reusing any page the digital layer already covered.
     *
     * A scan whose cover page was OCR'd by the scanner is the common case for this: page 1
     * comes from the text layer, the rest go through the engine, and the result is
     * [ExtractionSource.Mixed].
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<OcrProgress>.ocr(
        bytes: ByteArray,
        digital: OcrDocument?,
    ) {
        rasterizer.open(bytes).use { document ->
            val indices = document.pageIndices(options)
            if (indices.isEmpty()) {
                // Reached by the digital path too, once its selection turns out to be
                // empty: the page count lives here, so this is the one place that can tell
                // "the PDF has no pages" from "your pageRange missed all of them".
                throw OcrError.InvalidInput(
                    "no pages to process: the document has ${document.pageCount} page(s)" +
                        options.pageRange?.let { " and pageRange $it selects none of them" }
                            .orEmpty(),
                )
            }

            // Indexed once instead of scanned per page: a 300-page document would
            // otherwise walk the text layer 300 times to find 300 pages. `getOrPut` rather
            // than `associateBy`, which keeps the LAST entry for a duplicated index while
            // the scan this replaced kept the first -- only a malformed extractor can
            // report one index twice, but the indexing was meant to change the cost, not
            // the answer.
            val reusable = buildMap<Int, OcrPage> {
                digital?.pages?.forEach { page ->
                    if (page.lines.isNotEmpty()) getOrPut(page.index) { page }
                }
            }
            // Announced from what will actually happen rather than from "a text layer
            // exists": one that covers none of the pages in range yields a pure-OCR
            // document, and Started must not promise a source Done will contradict.
            val reused = indices.count { it in reusable }
            val source = when (reused) {
                0 -> ExtractionSource.Ocr
                indices.size -> ExtractionSource.DigitalTextLayer
                else -> ExtractionSource.Mixed
            }
            emit(OcrProgress.Started(pageCount = indices.size, source = source))

            val pages = ArrayList<OcrPage>(indices.size)
            indices.forEachIndexed { position, pageIndex ->
                // The cancellation check the POC did not have: without it, a cancelled
                // job keeps grinding through every remaining page.
                currentCoroutineContext().ensureActive()

                val page = reusable[pageIndex] ?: recognizePage(document, pageIndex)
                pages += page
                emit(
                    OcrProgress.Page(
                        index = pageIndex,
                        completed = position + 1,
                        of = indices.size,
                        page = page,
                    ),
                )
            }
            emit(OcrProgress.Done(OcrDocument(pages, ExtractionSource.of(pages.map { it.source }))))
        }
    }

    private suspend fun recognizePage(
        document: RasterizedDocument,
        pageIndex: Int,
    ): OcrPage {
        val raster = document.rasterize(pageIndex, options)
        return try {
            recognizer().recognize(raster, pageIndex, options)
        } finally {
            // Released immediately rather than at the end of the document: a 200 dpi A4
            // raster is several MB, and holding 300 of them is not survivable on a phone.
            raster.release()
        }
    }
}

/** The page indices to process, honouring `OcrOptions.pageRange` and the real page count. */
private fun RasterizedDocument.pageIndices(
    options: OcrOptions,
): List<Int> {
    val requested = options.pageRange ?: return (0 until pageCount).toList()
    // Intersected arithmetically rather than by filtering the caller's range: `0..MAX_VALUE`
    // is a legal way to say "to the end of the document", and filtering it would walk two
    // billion indices before touching a single page.
    return (maxOf(requested.first, 0)..minOf(requested.last, pageCount - 1)).toList()
}

/** The pages to keep, honouring `OcrOptions.pageRange`. */
private fun List<OcrPage>.selectedBy(options: OcrOptions): List<OcrPage> {
    val range = options.pageRange ?: return this
    return filter { it.index in range }
}
