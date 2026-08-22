package io.github.andrewmalitchuk.uncial.engine.vision.source.engine

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.engine.vision.core.fixture.SAMPLE_PDF_BYTES
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.raster.source.rasterizer.createPageRasterizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Exercises the real iOS pipeline: PDFKit rasterizes, Apple Vision recognizes.
 *
 * ### What the simulator can and cannot prove
 *
 * Vision's text recognition returns **zero observations on the iOS Simulator**. Verified
 * directly against the framework rather than assumed: `performRequests` returns `true`,
 * `error` is `null`, revision 3 reports as supported, and `results` is empty for accurate,
 * fast, with languages and without — while the rasterized page demonstrably contains ink
 * (see [RasterDiagnosticTest]). Recognition needs the Neural Engine, which the simulator
 * does not provide.
 *
 * So these tests assert everything that *is* observable here — engine availability,
 * capability reporting, language filtering, and that the whole pipeline runs without
 * error — and assert the recognized text only when Vision actually returned some, which
 * happens on a real device. A test that demanded text here would be red for a reason that
 * has nothing to do with this code, and a test that dropped the assertion entirely would
 * be green for no reason at all.
 */
class VisionRecognizerIntegrationTest {

    @Test
    fun `vision is always available and reports what it can do`() {
        val engine = visionEngine()
        assertTrue(engine.isAvailable(), "Vision ships with the OS and must always be available")
        assertEquals(VISION_ENGINE_ID, engine.id)

        val capabilities = engine.capabilities(
            OcrOptions(languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)),
        )
        assertTrue(
            OcrLanguage.English in capabilities.languages,
            "every Vision revision supports English; got ${capabilities.languages}",
        )
        assertTrue(capabilities.confidence, "Vision always reports per-observation confidence")
        // Derived from boundingBoxForRange rather than native, but real.
        assertTrue(capabilities.wordLevel)
        // Vision has no equivalent of libtesseract's per-element recognition language or
        // orientation, so unlike the JVM engine it must not claim them.
        assertTrue(!capabilities.perLineLanguage)
        assertTrue(!capabilities.orientationDetection)
    }

    @Test
    fun `requested languages Vision cannot do are dropped rather than silently misused`() {
        // A language with no Vision tag at all: the engine must not pass it through, or
        // Vision would be asked for something it cannot do and fail obscurely.
        val polish = OcrLanguage.custom("pol")
        val capabilities = visionEngine().capabilities(
            OcrOptions(languages = listOf(OcrLanguage.English, polish)),
        )
        assertTrue(polish !in capabilities.languages, "got ${capabilities.languages}")
        assertTrue(OcrLanguage.English in capabilities.languages)
    }

    @Test
    fun `ukrainian support tracks the OS version`() {
        val capabilities = visionEngine().capabilities(
            OcrOptions(languages = listOf(OcrLanguage.Ukrainian)),
        )
        // Vision gained Ukrainian in revision 3 (iOS 16). Either answer is correct; what
        // matters is that the SDK reports the truth instead of assuming.
        println(
            "vision Ukrainian supported: " +
                (OcrLanguage.Ukrainian in capabilities.languages),
        )
    }

    @Test
    fun `the full PDFKit to Vision pipeline runs and its output is well-formed`() = runTest {
        val options = OcrOptions(languages = listOf(OcrLanguage.English))
        val document = createPageRasterizer().open(SAMPLE_PDF_BYTES)
        assertEquals(1, document.pageCount)

        val page = document.use { pdf ->
            val raster = pdf.rasterize(pageIndex = 0, options = options)
            try {
                assertTrue(raster.width > 0 && raster.height > 0, "empty raster")
                visionEngine().create(options, OcrLogger.None, null).use { recognizer ->
                    recognizer.recognize(raster, pageIndex = 0, options = options)
                }
            } finally {
                raster.release()
            }
        }

        assertEquals(0, page.index)
        assertTrue(page.size.width > 0f && page.size.height > 0f, "page has no size")

        if (page.lines.isEmpty()) {
            println(
                "Vision returned no observations — expected on the iOS Simulator, which " +
                    "has no Neural Engine. Run on a device to exercise recognition.",
            )
            return@runTest
        }

        // On a real device: check that what came back is coherent.
        val text = page.text.lowercase()
        assertTrue(
            "text layer" in text || "uncial" in text || "digital" in text,
            "expected words from the fixture, recognized: ${page.text}",
        )
        page.lines.forEach { line ->
            assertTrue(line.text.isNotBlank(), "a blank line should never be emitted")
            assertTrue(line.box.width > 0f && line.box.height > 0f, "empty box: ${line.box}")
            // Top-down coordinates: Vision reports bottom-up normalized, and the engine is
            // the single place that conversion happens. If it were wrong, boxes would sit
            // outside the page.
            assertTrue(
                line.box.top >= -1f && line.box.bottom <= page.size.height + 1f,
                "box ${line.box} outside page ${page.size}",
            )
            assertTrue(line.confidence.isKnown, "Vision always reports confidence")
        }
    }
}
