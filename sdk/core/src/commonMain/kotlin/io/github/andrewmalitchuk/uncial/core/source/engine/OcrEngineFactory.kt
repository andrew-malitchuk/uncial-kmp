package io.github.andrewmalitchuk.uncial.core.source.engine

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions

/**
 * Creates [TextRecognizer]s.
 *
 * This interface is the reason `uncial-runtime` has no compile-time dependency on any
 * engine: the runtime is handed a factory and never names Tesseract or Vision. Swapping
 * engines, or supplying your own, means supplying a different factory. (PLAN.md §6.3,
 * §6.6)
 */
public interface OcrEngineFactory {

    /** Stable identifier, mirrored into [OcrCapabilities.engineId]. */
    public val id: String

    /**
     * Whether this engine can run here **at all**, checked without creating anything.
     *
     * Deliberately public and honest: the JVM engine binds to a system `libtesseract`
     * that may simply not be installed, and a caller is entitled to find that out before
     * handing over a 300-page document. (PLAN.md §5.9)
     */
    public fun isAvailable(): Boolean

    /**
     * What this engine would be able to do with [options], without creating a recognizer.
     *
     * Cheap enough to call for a UI capability check.
     */
    public fun capabilities(options: OcrOptions): OcrCapabilities

    /**
     * Creates a recognizer configured for [options].
     *
     * The caller owns the result and must close it.
     *
     * @param languageData where to obtain `.traineddata`, for engines that need it.
     *   Vision ignores it — the OS ships its languages.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.EngineInit if the engine
     *   cannot be started.
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.NoLanguageData if no
     *   requested language can be loaded.
     */
    public suspend fun create(
        options: OcrOptions,
        logger: OcrLogger = OcrLogger.None,
        languageData: LanguageDataProvider? = null,
    ): TextRecognizer
}
