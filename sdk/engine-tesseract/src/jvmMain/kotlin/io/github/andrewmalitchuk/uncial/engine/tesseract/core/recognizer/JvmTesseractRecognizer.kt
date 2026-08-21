package io.github.andrewmalitchuk.uncial.engine.tesseract.core.recognizer

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.native.TessHandle
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Orientation
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.sourceforge.tess4j.ITessAPI

/**
 * Recognition via the system `libtesseract`, driven through its C API.
 *
 * Same engine as Android, same iteration levels, same `.traineddata` — which is what makes
 * the JVM the practical place to run a golden corpus and trust that the numbers transfer.
 * (PLAN.md §4)
 */
internal class JvmTesseractRecognizer(
    private val handle: TessHandle,
    private val logger: OcrLogger,
    override val capabilities: OcrCapabilities,
) : TextRecognizer {

    // One native handle holds one image and one result set at a time.
    private val lock = Mutex()

    override suspend fun recognize(
        raster: Raster,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage = withContext(Dispatchers.IO) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            try {
                handle.setImage(raster.image)
                // Order matters: libtesseract rejects SetSourceResolution before SetImage
                // ("Please call SetImage before SetSourceResolution") and then estimates the
                // resolution itself, which is both slower and less accurate than being told.
                // The raster's own resolution, not options.renderDpi: the rasterizer
                // clamps the requested dpi whenever the page would exceed maxPageSide, and
                // describing pixels that were never rendered is exactly the input this
                // heuristic must not be lied to about. When the raster does not know its
                // own resolution -- a bitmap the caller handed us -- say nothing at all and
                // let libtesseract estimate, which beats asserting a number we invented.
                raster.sourceDpi?.let { handle.setSourceResolution(it) }
                handle.recognize()
                currentCoroutineContext().ensureActive()

                val words = if (options.includeWords) readWords() else emptyList()
                val lines = readLines(words, options)
                logger.debug("page $pageIndex: ${lines.size} lines")

                OcrPage(
                    index = pageIndex,
                    size = Size(raster.width.toFloat(), raster.height.toFloat()),
                    lines = lines,
                    // Tesseract reports orientation per element; for a page they agree, so
                    // the first line speaks for the page. A page with no lines has no
                    // orientation to report and stays Up.
                    orientation = lines.firstOrNull()?.let { orientationOfFirstLine }
                        ?: Orientation.Up,
                    source = ExtractionSource.Ocr,
                )
            } catch (cause: Throwable) {
                if (cause is OcrError) throw cause
                currentCoroutineContext().ensureActive()
                throw OcrError.RecognitionFailed(pageIndex, cause = cause)
            }
        }
    }

    private var orientationOfFirstLine: Orientation = Orientation.Up

    private fun readLines(words: List<OcrWord>, options: OcrOptions): List<OcrLine> {
        val lines = mutableListOf<OcrLine>()
        var first = true
        handle.forEachElement(ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE) { element ->
            if (first) {
                orientationOfFirstLine = element.orientation
                first = false
            }
            lines += OcrLine(
                text = element.text,
                box = element.box,
                // A raster carries no font metrics, so line height stands in for type size.
                // Structure compares it against the document median, which a consistent
                // proxy satisfies.
                fontSize = element.box.height,
                confidence = element.confidence,
                words = words.filter { element.box.containsCenterOf(it.box) },
                language = element.languageCode?.let { code -> options.languageFor(code) },
                skewDegrees = element.skewDegrees,
            )
        }
        return lines
    }

    /**
     * Resolves Tesseract's language code back to the [OcrLanguage] the caller asked for.
     *
     * Matching against the request rather than constructing a new language keeps identity
     * meaningful: a caller can compare `line.language == OcrLanguage.Ukrainian`. A code
     * outside the request (Tesseract can fall back internally) still surfaces, as a
     * custom language, rather than being dropped.
     *
     * @return `null` for a code that is not a valid language identifier. [OcrLanguage.custom]
     *   validates its input because it is normally *caller* input that becomes a filename
     *   and a URL; here the string comes from the engine, per line, on the hot path. An
     *   unexpected code is worth losing one line's language annotation over — it is not
     *   worth failing the whole page, which is what letting the exception out would do.
     */
    private fun OcrOptions.languageFor(code: String): OcrLanguage? =
        languages.firstOrNull { it.tesseractCode == code }
            ?: try {
                OcrLanguage.custom(code)
            } catch (cause: IllegalArgumentException) {
                logger.debug("tesseract reported an unusable language code '$code': ${cause.message}")
                null
            }

    private fun readWords(): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        handle.forEachElement(ITessAPI.TessPageIteratorLevel.RIL_WORD) { element ->
            words += OcrWord(
                text = element.text,
                box = element.box,
                confidence = element.confidence,
            )
        }
        return words
    }

    override fun close() {
        handle.close()
    }
}

private fun BoundingBox.containsCenterOf(other: BoundingBox): Boolean {
    val center = other.center
    return center.x in left..right && center.y in top..bottom
}
