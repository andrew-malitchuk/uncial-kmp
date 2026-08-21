package io.github.andrewmalitchuk.uncial.engine.tesseract.source.language

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel.orElseOnFailure
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel.runReportingFailure
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Tries `this` first, then [fallback].
 *
 * Reported availability is the union of both, and [LanguageDataProvider.materialize] uses
 * whichever provider can supply the most of the requested languages — so a machine with a
 * system Tesseract keeps using it, while a machine without one falls back to the models
 * the `uncial-lang-*` artifacts ship.
 */
public operator fun LanguageDataProvider.plus(
    fallback: LanguageDataProvider,
): LanguageDataProvider = ChainedLanguageDataProvider(listOf(this, fallback))

private class ChainedLanguageDataProvider(
    private val providers: List<LanguageDataProvider>,
) : LanguageDataProvider {

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        providers.flatMap { provider -> orElseOnFailure(emptyList()) { provider.available(requested) } }
            .distinct()

    override suspend fun materialize(languages: List<OcrLanguage>): String {
        // Tesseract takes ONE datapath, so the languages cannot be split across providers.
        // Pick the provider that covers the most of them, preferring earlier ones on a tie.
        val ranked = providers
            .map { provider ->
                provider to orElseOnFailure(emptyList()) { provider.available(languages) }
            }
            .filter { (_, covered) -> covered.isNotEmpty() }
            .sortedByDescending { (_, covered) -> covered.size }

        if (ranked.isEmpty()) throw OcrError.NoLanguageData(languages)
        val failures = mutableListOf<Throwable>()
        for ((provider, covered) in ranked) {
            var datapath: String? = null
            runReportingFailure({ datapath = provider.materialize(covered) }) { failures += it }
            datapath?.let { return it }
        }
        throw OcrError.NoLanguageData(languages, cause = failures.firstOrNull())
    }
}
