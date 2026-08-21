package io.github.andrewmalitchuk.uncial.engine.tesseract.source.engine

import io.github.andrewmalitchuk.uncial.core.source.android.UncialContext
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel.orElseOnFailure
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.language.AndroidTessDataProvider
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

public actual fun defaultTessDataProvider(): LanguageDataProvider = LazyAndroidTessDataProvider()

/**
 * Defers looking up the application context until the data is actually needed.
 *
 * `defaultTessDataProvider()` is not a suspend function and must not throw just because it
 * was called early, so a missing context surfaces at [materialize] time as a proper
 * [OcrError] instead of at construction as an exception nobody expects.
 */
private class LazyAndroidTessDataProvider : LanguageDataProvider {

    private fun delegate(languages: List<OcrLanguage>): LanguageDataProvider {
        val context = UncialContext.get() ?: throw OcrError.EngineInit(
            "no Android Context: androidx.startup did not run. If the consumer app removes " +
                "InitializationProvider from its merged manifest, supply a LanguageDataProvider " +
                "explicitly instead. (requested: " +
                languages.joinToString("+") { it.tesseractCode } + ")",
        )
        return AndroidTessDataProvider(context)
    }

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        orElseOnFailure(emptyList()) { delegate(requested).available(requested) }

    override suspend fun materialize(languages: List<OcrLanguage>): String =
        delegate(languages).materialize(languages)
}
