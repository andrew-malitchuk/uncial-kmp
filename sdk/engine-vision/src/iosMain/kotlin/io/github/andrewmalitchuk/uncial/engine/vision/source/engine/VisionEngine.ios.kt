package io.github.andrewmalitchuk.uncial.engine.vision.source.engine

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineFactory
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.info
import io.github.andrewmalitchuk.uncial.core.source.log.warn
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.vision.core.recognizer.VisionRecognizer
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.options.RecognitionQuality
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.Vision.VNRequestTextRecognitionLevelFast

public actual fun visionEngine(): OcrEngineFactory = VisionEngineFactory()

// BetaInteropApi is for reading the NSError out-parameter (ObjCObjectVar.value), the same
// opt-in VisionRecognizer needs for the same reason.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class VisionEngineFactory : OcrEngineFactory {

    override val id: String = VISION_ENGINE_ID

    /** Vision is part of the OS: if the app runs, the engine is there. */
    override fun isAvailable(): Boolean = true

    override fun capabilities(options: OcrOptions): OcrCapabilities =
        capabilities(options, OcrLogger.None)

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer {
        // languageData is ignored on purpose: Vision ships its models with the OS, so
        // there is nothing for a provider to materialize.
        val capabilities = capabilities(options, logger)
        if (capabilities.languages.isEmpty()) {
            // An empty recognitionLanguages makes Vision fall back to its own default,
            // which is en-US -- so a Ukrainian scan comes back as plausible English rather
            // than as an error. Say so at creation time instead of letting the caller
            // discover it in the results.
            logger.warn(
                "vision supports none of the requested languages; " +
                    "recognition will fall back to Vision's default language",
            )
        } else {
            logger.info(
                "vision initialized with " +
                    capabilities.languages.joinToString("+") { it.bcp47 ?: it.tesseractCode },
            )
        }
        return VisionRecognizer(capabilities, logger)
    }

    /**
     * The capability matrix, with [logger] for the parts that can go wrong.
     *
     * The [OcrEngineFactory] entry point has no logger, so a capability query made for a
     * UI check stays silent; [create] passes the caller's logger and the same query then
     * reports what it could not determine.
     */
    private fun capabilities(options: OcrOptions, logger: OcrLogger): OcrCapabilities {
        // A throwaway request, used for both questions below, because Vision answers both
        // of them per request rather than statically -- and the answer depends on how the
        // request is configured. Apple's header is explicit: "a language supported in one
        // recognition level might not be available in another". Ukrainian is one of those:
        // it exists at the accurate level only. So the probe has to be configured exactly
        // as VisionRecognizer will configure the real request, or a Fast extraction gets
        // told uk-UA is available and then quietly recognizes Latin nonsense.
        val probe = VNRecognizeTextRequest(null).apply {
            recognitionLevel = when (options.recognitionQuality) {
                RecognitionQuality.Fast -> VNRequestTextRecognitionLevelFast
                RecognitionQuality.Accurate -> VNRequestTextRecognitionLevelAccurate
            }
        }
        val supported = supportedLanguages(probe, logger)
        val languages = if (supported == null) {
            // The query itself failed, which is not the same thing as "this OS supports
            // nothing". Filtering against an empty set would silently drop every language
            // and hand Vision an empty recognitionLanguages, i.e. an undeclared fallback to
            // en-US. Passing the requested languages through instead keeps the caller's
            // intent: if one of them really is unsupported, Vision fails the request and
            // the error surfaces as OcrError.RecognitionFailed.
            options.languages.filter { it.bcp47 != null }
        } else {
            // Only the languages Vision confirms it can do. On iOS 15 this silently drops
            // Ukrainian, which is exactly the asymmetry capabilities exist to expose.
            options.languages.filter { it.bcp47 != null && it.bcp47 in supported }
        }
        return OcrCapabilities(
            engineId = id,
            // Read from the request rather than hardcoded. Note what this value is: the
            // revision this BUILD defaults to -- Apple defines it as the latest for the SDK
            // the app was linked against -- not necessarily the newest the running OS could
            // offer. It is still the honest answer, because VisionRecognizer leaves the
            // revision alone as well, so this is the revision recognition actually runs at.
            // Uncial deliberately does not pin one: pinning would freeze accuracy and
            // language coverage at whatever was current when this was written, and asking
            // for a revision the OS does not have throws.
            engineVersion = "vision-${probe.revision}",
            languages = languages,
            // Vision has no native word results; the engine derives them by asking
            // boundingBoxForRange for each whitespace-separated span. See
            // VisionRecognizer.words.
            wordLevel = true,
            confidence = true,
            perLineLanguage = false,
            skewDetection = false,
        )
    }

    /**
     * The BCP-47 tags this OS build can recognize, or `null` if Vision would not say.
     *
     * Asked at runtime rather than hardcoded, because the answer depends on the iOS
     * version: revision 3 (iOS 16) is what added Ukrainian. The two failure modes are kept
     * apart on purpose -- see the caller.
     */
    private fun supportedLanguages(
        request: VNRecognizeTextRequest,
        logger: OcrLogger,
    ): Set<String>? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val supported = request.supportedRecognitionLanguagesAndReturnError(error.ptr)
        if (supported == null) {
            logger.warn(
                "vision could not list its recognition languages: " +
                    (error.value?.localizedDescription ?: "unknown error"),
            )
            return@memScoped null
        }
        supported.filterIsInstance<String>().toSet()
    }
}
