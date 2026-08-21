package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.info
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.native.JnaLibraryPath
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.native.TessHandle
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.recognizer.JvmTesseractRecognizer
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.language.SystemTessDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.source.options.TesseractPageSegmentation
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public actual fun tesseractEngine(
    pageSegmentation: TesseractPageSegmentation,
): OcrEngineFactory = JvmTesseractEngineFactory(pageSegmentation)

/**
 * The one engine that is not self-contained.
 *
 * It needs a system `libtesseract` plus `.traineddata` on disk — on macOS,
 * `brew install tesseract tesseract-lang`. That is an acceptable trade for a JVM artifact,
 * but it must be documented and [isAvailable] must be honest about it, because the failure
 * otherwise surfaces as an `UnsatisfiedLinkError` from inside JNA. (PLAN.md §5.9)
 */
private class JvmTesseractEngineFactory(
    private val pageSegmentation: TesseractPageSegmentation,
) : OcrEngineFactory {

    override val id: String = TESSERACT_ENGINE_ID

    override fun isAvailable(): Boolean {
        // The native library is the hard requirement; models can come from the system or
        // from an uncial-lang-* artifact on the classpath.
        if (!JnaLibraryPath.libraryPresent()) return false
        if (SystemTessDataProvider().dataPath != null) return true
        return javaClass.classLoader?.getResource("tessdata") != null ||
            javaClass.classLoader?.getResource("tessdata/eng.traineddata") != null ||
            javaClass.classLoader?.getResource("tessdata/ukr.traineddata") != null
    }

    override fun capabilities(options: OcrOptions): OcrCapabilities = OcrCapabilities(
        engineId = id,
        engineVersion = nativeVersion,
        languages = options.languages,
        wordLevel = true,
        confidence = true,
        // libtesseract's C API reports the recognition language per element and the page
        // orientation. Tess4J's high-level wrapper hides both, and the Android binding has
        // neither -- so this is one of the few places the JVM engine is the richer one.
        perLineLanguage = true,
        orientationDetection = true,
        // The C API exposes the per-line deskew angle; Tess4J's high-level
        // wrapper does not.
        skewDetection = true,
    )

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer = withContext(Dispatchers.IO) {
        JnaLibraryPath.ensureConfigured()
        if (!JnaLibraryPath.libraryPresent()) {
            throw OcrError.EngineInit(
                "no libtesseract found in ${JnaLibraryPath.locatedDirectories()} or the " +
                    "system loader path. Install it (macOS: brew install tesseract) or set " +
                    "-Djna.library.path.",
            )
        }

        val provider = languageData ?: defaultTessDataProvider()
        val usable = provider.available(options.languages)
        if (usable.isEmpty()) throw OcrError.NoLanguageData(languages = options.languages)
        // LanguageDataProvider's contract is Tesseract's own datapath convention: the
        // PARENT of tessdata/. That is what the Tesseract4Android wrapper wants, because
        // it appends `tessdata/` in Java before calling into the native layer. Tess4J
        // does not: it hands the path straight to libtesseract 5, which treats it as the
        // tessdata directory itself. So the JVM engine has to descend one level.
        val dataPath = tesseractDataPath(provider.materialize(usable))

        val languages = usable.joinToString("+") { it.tesseractCode }
        val handle = try {
            TessHandle.open(
                dataPath = dataPath,
                languages = languages,
                pageSegMode = pageSegmentation.toTesseractMode(),
                logger = logger,
            )
        } catch (cause: OcrError.NoLanguageData) {
            // Re-thrown with the languages filled in: TessHandle does not know them.
            throw OcrError.NoLanguageData(usable, dataPath, cause.cause)
        } catch (cause: Throwable) {
            throw OcrError.EngineInit("failed to initialize Tesseract for '$languages'", cause)
        }
        logger.info("tesseract initialized with '$languages' from $dataPath")

        JvmTesseractRecognizer(
            handle = handle,
            logger = logger,
            capabilities = capabilities(options).copy(languages = usable),
        )
    }

    private companion object {
        /**
         * The native library's version, read through JNA.
         *
         * Wrapped because this is the first call that actually loads `libtesseract`: on a
         * machine without it, this is where the `UnsatisfiedLinkError` lands, and it must
         * not escape a capability query.
         */
        val nativeVersion: String? by lazy {
            runCatching {
                JnaLibraryPath.ensureConfigured()
                net.sourceforge.tess4j.TessAPI.INSTANCE.TessVersion()
            }.getOrNull()
        }
    }
}
