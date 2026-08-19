package io.github.andrewmalitchuk.uncial.engine.fake.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrWord
import kotlinx.coroutines.delay

/**
 * A deterministic engine for testing code that calls Uncial.
 *
 * This is published rather than kept internal because it is part of the SDK's
 * developer experience: without it, a consumer's unit test of "what does my screen do
 * when OCR returns two pages" has to run Tesseract, install language data, and tolerate
 * recognition drift between machines. (PLAN.md §6.2)
 *
 * ```
 * val client = UncialClient {
 *     engine = FakeOcrEngine(text = mapOf(0 to "Розділ 1", 1 to "Текст сторінки"))
 *     rasterizer = FakePageRasterizer(pageCount = 2)
 * }
 * ```
 *
 * @param text page index to the text that page should recognize as, one line per `\n`.
 *   Pages with no entry recognize as [defaultText].
 * @param defaultText what a page with no [text] entry produces. Blank means an empty page,
 *   which is how you simulate an image-only scan that OCR could not read.
 * @param capabilities what this engine claims it can do. Override to test how your code
 *   handles an engine without confidence or word support.
 * @param failOnPage make [TextRecognizer.recognize] throw
 *   [OcrError.RecognitionFailed] for this page index, to test error paths.
 * @param available what [isAvailable] returns, to test the "engine missing" path.
 * @param delayPerPageMillis artificial per-page delay, for testing progress and
 *   cancellation. Uses `delay`, so it costs no real time under `runTest`.
 */
public class FakeOcrEngine(
    private val text: Map<Int, String> = emptyMap(),
    private val defaultText: String = DEFAULT_PAGE_TEXT,
    private val capabilities: OcrCapabilities = DefaultCapabilities,
    private val failOnPage: Int? = null,
    private val available: Boolean = true,
    private val delayPerPageMillis: Long = 0L,
) : OcrEngineFactory {

    override val id: String = FAKE_ENGINE_ID

    override fun isAvailable(): Boolean = available

    override fun capabilities(options: OcrOptions): OcrCapabilities =
        capabilities.copy(languages = options.languages)

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer {
        if (!available) throw OcrError.EngineInit("FakeOcrEngine configured as unavailable")
        return FakeRecognizer(capabilities(options))
    }

    private inner class FakeRecognizer(
        override val capabilities: OcrCapabilities,
    ) : TextRecognizer {

        override suspend fun recognize(
            raster: Raster,
            pageIndex: Int,
            options: OcrOptions,
        ): OcrPage {
            if (delayPerPageMillis > 0) delay(delayPerPageMillis)
            if (pageIndex == failOnPage) throw OcrError.RecognitionFailed(pageIndex)

            val pageText = text[pageIndex] ?: defaultText
            return OcrPage(
                index = pageIndex,
                size = Size(raster.width.toFloat(), raster.height.toFloat()),
                lines = pageText.toLines(
                    // DefaultCapabilities advertises wordLevel, so the fake has to
                    // actually produce words -- otherwise a consumer testing "my UI draws
                    // word boxes" gets an empty list from an engine that claims to support
                    // them, which is precisely the bug the fake exists to let them catch.
                    includeWords = capabilities.wordLevel && options.includeWords,
                ),
                source = ExtractionSource.Ocr,
            )
        }

        override fun close() {
            // Nothing to release.
        }
    }

    /**
     * Lays out text as evenly spaced lines with plausible geometry.
     *
     * The boxes matter: `DocumentStructure` reads them, so a fake that returned
     * [BoundingBox.Zero] everywhere would make every structure test pass for the wrong
     * reason.
     */
    private fun String.toLines(includeWords: Boolean): List<OcrLine> {
        if (isBlank()) return emptyList()
        return split('\n')
            .filter { it.isNotBlank() }
            .mapIndexed { index, line ->
                val trimmed = line.trim()
                val box = BoundingBox(
                    left = LINE_LEFT,
                    top = LINE_TOP + index * (LINE_HEIGHT + LINE_GAP),
                    width = trimmed.length * CHARACTER_WIDTH,
                    height = LINE_HEIGHT,
                )
                OcrLine(
                    text = trimmed,
                    box = box,
                    fontSize = LINE_HEIGHT,
                    confidence = Confidence.of(FAKE_CONFIDENCE),
                    words = if (includeWords) trimmed.toWords(box) else emptyList(),
                )
            }
    }

    /**
     * Splits a line into words laid out along its own box.
     *
     * The geometry is derived from [CHARACTER_WIDTH] the same way the line's is, so word
     * boxes tile the line without gaps or overlap and a caller can assert on them.
     */
    private fun String.toWords(lineBox: BoundingBox): List<OcrWord> {
        var cursor = lineBox.left
        return split(' ')
            .filter { it.isNotBlank() }
            .map { word ->
                val width = word.length * CHARACTER_WIDTH
                val left = cursor
                // Advance past the word and the space that followed it.
                cursor += width + CHARACTER_WIDTH
                OcrWord(
                    text = word,
                    box = BoundingBox(
                        left = left,
                        top = lineBox.top,
                        width = width,
                        height = lineBox.height,
                    ),
                    confidence = Confidence.of(FAKE_CONFIDENCE),
                )
            }
    }

    public companion object {
        /** Identifier this engine reports in `OcrCapabilities.engineId`. */
        public const val FAKE_ENGINE_ID: String = "fake"

        /** What a page with no configured text recognizes as. */
        public const val DEFAULT_PAGE_TEXT: String = "Uncial fake page"

        /** Claims everything, so tests exercise the fully featured paths by default. */
        public val DefaultCapabilities: OcrCapabilities = OcrCapabilities(
            engineId = FAKE_ENGINE_ID,
            engineVersion = "fake-1",
            languages = OcrLanguage.Default,
            wordLevel = true,
            confidence = true,
            perLineLanguage = false,
            skewDetection = false,
        )

        private const val FAKE_CONFIDENCE = 0.99f
        private const val LINE_LEFT = 60f
        private const val LINE_TOP = 80f
        private const val LINE_HEIGHT = 24f
        private const val LINE_GAP = 8f
        private const val CHARACTER_WIDTH = 11f
    }
}
