package io.github.andrewmalitchuk.uncial.engine.tesseract.core.recognizer

import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.log.warn
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as withNativeLock

/**
 * Recognition via Tesseract4Android.
 *
 * Holds a native `TessBaseAPI` with a loaded language model, which is why it is a class
 * with a lifecycle rather than the POC's `object`: loading `ukr+eng` costs real time and
 * memory, and doing it per page would dominate the runtime of a multi-page document.
 * (PLAN.md §5.1)
 */
internal class AndroidTesseractRecognizer(
    private val api: TessBaseAPI,
    private val logger: OcrLogger,
    override val capabilities: OcrCapabilities,
) : TextRecognizer {

    // TessBaseAPI holds one image and one result set at a time.
    private val lock = Mutex()

    /**
     * Guards the native handle against `close()`, which the suspending [lock] cannot.
     *
     * `close()` is a plain `AutoCloseable.close()` and the runtime calls it from
     * `UncialClient.close()` on whatever thread happens to be there. `recycle()` zeroes
     * the wrapper's native pointer, so racing it against a recognition in flight turns a
     * would-be `IllegalStateException` into a SIGSEGV.
     */
    private val nativeLock = ReentrantLock()

    // Volatile because the cancellation handler below reads it from an arbitrary thread
    // without the lock -- it cannot take the lock, since the whole point is to interrupt
    // the work that is holding it.
    @Volatile
    private var recycled = false

    override suspend fun recognize(
        raster: Raster,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage = withContext(Dispatchers.Default) {
        lock.withLock {
            currentCoroutineContext().ensureActive()

            // Tesseract can be interrupted mid-page: stop() makes the in-flight
            // getUTF8Text() return early, so cancelling a 300-page job does not have to
            // wait out the current page.
            val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
                // `recycled` is set before recycle() runs, so this cannot call stop() on a
                // handle whose native pointer has already been zeroed -- which would be a
                // SIGSEGV, not something runCatching could catch. dispose() does not wait
                // for a handler that is already executing, so the flag is the only guard.
                if (cause != null && !recycled) runCatching { api.stop() }
            }
            try {
                nativeLock.withNativeLock {
                    check(!recycled) { "this recognizer has been closed" }
                    api.setImage(raster.bitmap)
                    // Triggers recognition; the iterator below reads its cached results.
                    api.getUTF8Text()

                    val lines = readLines(options)
                    logger.debug("page $pageIndex: ${lines.size} lines")
                    OcrPage(
                        index = pageIndex,
                        size = Size(raster.width.toFloat(), raster.height.toFloat()),
                        lines = lines,
                        source = ExtractionSource.Ocr,
                    )
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (cause: Throwable) {
                if (cause is OcrError) throw cause
                currentCoroutineContext().ensureActive()
                throw OcrError.RecognitionFailed(pageIndex, cause = cause)
            } finally {
                cancellation.dispose()
                nativeLock.withNativeLock {
                    if (!recycled) {
                        try {
                            api.clear()
                        } catch (cause: Throwable) {
                            logger.warn("TessBaseAPI.clear() failed", cause)
                        }
                    }
                }
            }
        }
    }

    private fun readLines(options: OcrOptions): List<OcrLine> {
        val lineLevel = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
        val wordLevel = TessBaseAPI.PageIteratorLevel.RIL_WORD
        val lines = mutableListOf<OcrLine>()
        // Read every word on the page once, up front. Doing it per line meant a fresh
        // full-page iterator and a full walk for each of them -- O(lines x words) JNI
        // round trips, which on a dense page is thousands of calls to do hundreds.
        val words = if (options.includeWords) readWords(wordLevel) else emptyList()
        val iterator = api.resultIterator ?: return emptyList()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(lineLevel)?.trim()
                if (text.isNullOrEmpty()) continue
                val box = iterator.getBoundingRect(lineLevel).toBoundingBox()
                lines += OcrLine(
                    text = text,
                    box = box,
                    // Tesseract reports no font metrics for a raster, so line height is
                    // the only available proxy for type size. Structure compares it
                    // against the document median, which a consistent proxy satisfies.
                    fontSize = box.height,
                    // The POC never read this, even though Tesseract has always
                    // provided it. (PLAN.md §5.4)
                    confidence = Confidence.ofPercent(iterator.confidence(lineLevel)),
                    words = words.filter { box.containsCenterOf(it.box) },
                )
            } while (iterator.next(lineLevel))
        } finally {
            iterator.delete()
        }
        return lines
    }

    /** Every word on the recognized page, in iteration order. */
    private fun readWords(wordLevel: Int): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        val iterator = api.resultIterator ?: return emptyList()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(wordLevel)?.trim()
                if (text.isNullOrEmpty()) continue
                words += OcrWord(
                    text = text,
                    box = iterator.getBoundingRect(wordLevel).toBoundingBox(),
                    confidence = Confidence.ofPercent(iterator.confidence(wordLevel)),
                )
            } while (iterator.next(wordLevel))
        } finally {
            iterator.delete()
        }
        return words
    }

    override fun close() {
        // Interrupt before queueing for the lock. Recognition holds nativeLock for the
        // whole page, and close() is reachable from ViewModel.onCleared() on the main
        // thread -- waiting out a multi-second page there is an ANR. stop() makes the
        // in-flight getUTF8Text() return early so the handover takes milliseconds.
        runCatching { api.stop() }
        nativeLock.withNativeLock {
            if (recycled) return
            recycled = true
            try {
                api.recycle()
            } catch (cause: Throwable) {
                logger.warn("TessBaseAPI.recycle() failed", cause)
            }
        }
    }
}

/** Whether [other]'s centre falls inside this box — how a word is assigned to its line. */
private fun BoundingBox.containsCenterOf(other: BoundingBox): Boolean {
    val center = other.center
    return center.x in left..right && center.y in top..bottom
}

private fun Rect.toBoundingBox(): BoundingBox = BoundingBox(
    left = left.toFloat(),
    top = top.toFloat(),
    width = width().toFloat(),
    height = height().toFloat(),
)
