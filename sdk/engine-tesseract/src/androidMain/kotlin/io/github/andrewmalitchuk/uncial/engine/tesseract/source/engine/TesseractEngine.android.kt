package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import com.googlecode.tesseract.android.TessBaseAPI
import io.github.andrewmalitchuk.uncial.core.source.android.UncialContext
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.info
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.recognizer.AndroidTesseractRecognizer
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual fun tesseractEngine(
    pageSegmentation: TesseractPageSegmentation,
): OcrEngineFactory = AndroidTesseractEngineFactory(pageSegmentation)

/**
 * Tesseract4Android bundles `libtesseract.so` for all four ABIs, so unlike the JVM engine
 * this one is genuinely self-contained: if the AAR is on the classpath, the native side is
 * there too.
 */
private class AndroidTesseractEngineFactory(
    private val pageSegmentation: TesseractPageSegmentation,
) : OcrEngineFactory {

    override val id: String = TESSERACT_ENGINE_ID

    override fun isAvailable(): Boolean = nativeLibraryLoads && UncialContext.get() != null

    override fun capabilities(options: OcrOptions): OcrCapabilities = OcrCapabilities(
        engineId = id,
        engineVersion = tesseractVersion,
        // Optimistic: what the engine WOULD do with these options. Which languages are
        // really available depends on which uncial-lang-* artifacts are present, and that
        // needs I/O — read TextRecognizer.capabilities after create() for the truth.
        languages = options.languages,
        wordLevel = true,
        confidence = true,
        // The Tesseract4Android wrapper exposes neither the per-element recognition
        // language nor the orientation/deskew angle: its ResultIterator has only text,
        // bounding box and confidence. libtesseract has all of it, which is why the JVM
        // engine reports true here -- a real asymmetry between two bindings of the SAME
        // engine, and precisely why capabilities exist. Use OcrLine.script instead, which
        // is derived from the text and therefore always available.
        perLineLanguage = false,
        orientationDetection = false,
        skewDetection = false,
    )

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer = withContext(Dispatchers.IO) {
        if (!nativeLibraryLoads) {
            throw OcrError.EngineInit("libtesseract failed to load", nativeLibraryFailure)
        }
        val provider = languageData ?: defaultTessDataProvider()
        val usable = provider.available(options.languages)
        if (usable.isEmpty()) {
            throw OcrError.NoLanguageData(languages = options.languages)
        }
        val dataPath = provider.materialize(usable)

        val api = TessBaseAPI()
        val languages = usable.joinToString("+") { it.tesseractCode }
        // OEM_LSTM_ONLY on purpose: the combined mode needs legacy data that neither
        // tessdata_fast nor tessdata_best carries, and asking for it fails init outright.
        val initialized = runCatching {
            api.init(dataPath, languages, TessBaseAPI.OEM_LSTM_ONLY)
        }.getOrElse { cause ->
            api.recycle()
            throw OcrError.EngineInit("TessBaseAPI.init threw for '$languages'", cause)
        }
        if (!initialized) {
            api.recycle()
            throw OcrError.NoLanguageData(languages = usable, searchedPath = dataPath)
        }
        api.pageSegMode = pageSegmentation.toTesseractMode()
        logger.info("tesseract initialized with '$languages' from $dataPath")

        AndroidTesseractRecognizer(
            api = api,
            logger = logger,
            capabilities = capabilities(options).copy(languages = usable),
        )
    }

    private companion object {
        var nativeLibraryFailure: Throwable? = null

        /**
         * Whether the native side is present, probed once and remembered.
         *
         * Touching [TessBaseAPI] triggers its `System.loadLibrary`, so this doubles as the
         * load attempt. A consumer whose R8 rules stripped the JNI entry points, or whose
         * ABI is not in the AAR, finds out here rather than mid-document.
         *
         * Deliberately `by lazy` rather than an eager initializer: as an eager one it ran
         * during class initialization, which `isAvailable()` triggers, which in turn is
         * reached from the non-suspending `UncialClient.capabilities`. An app that merely
         * renders "is OCR available?" would load a multi-megabyte native library on the
         * main thread. It is still the caller's job to touch `capabilities` off the main
         * thread the first time; this at least stops the cost from being paid twice.
         */
        val nativeLibraryLoads: Boolean by lazy {
            try {
                TessBaseAPI().recycle()
                true
            } catch (cause: Throwable) {
                nativeLibraryFailure = cause
                false
            }
        }

        val tesseractVersion: String? by lazy {
            if (!nativeLibraryLoads) return@lazy null
            try {
                val api = TessBaseAPI()
                try {
                    api.version
                } finally {
                    api.recycle()
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
