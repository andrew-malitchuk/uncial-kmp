package io.github.andrewmalitchuk.uncial.lang.download.source.provider

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Fetches `.traineddata` on first use and caches it on disk.
 *
 * The alternative to the `uncial-lang-*` artifacts: instead of 3.8-4.1 MB per language in
 * your download size, the model arrives the first time OCR runs. The trade is obvious and
 * belongs to the consumer — a document scanner used once a month should probably download;
 * an offline-first field app should bundle.
 *
 * ```
 * val client = UncialClient {
 *     languageData = downloadingLanguageDataProvider(logger = myLogger)
 * }
 * ```
 *
 * Chain it behind a bundled or system provider to prefer what is already there:
 *
 * ```
 * languageData = SystemTessDataProvider() + downloadingLanguageDataProvider()
 * ```
 *
 * ### What it does not do
 *
 * No progress reporting, no background scheduling, no retry policy. Downloading several MB
 * silently on a metered connection is a product decision, not a library one — check
 * [isCached] and drive the download yourself if your users should be asked first.
 */
public interface DownloadingLanguageDataProvider : LanguageDataProvider {

    /** Whether [language] is already on disk, so no network call is needed. */
    public fun isCached(language: OcrLanguage): Boolean

    /**
     * Downloads [languages] now, so the first extraction does not pay for it.
     *
     * @return the languages that ended up available. Failures are logged rather than
     *   thrown: pre-warming is best-effort by definition.
     */
    public suspend fun prefetch(languages: List<OcrLanguage>): List<OcrLanguage>

    /**
     * Deletes cached models, along with any `.part` files a killed download left behind.
     *
     * Suspending because it takes the same lock the download path does: a clear that ran
     * concurrently could unlink a model between [materialize] verifying it and Tesseract
     * opening it, which surfaces as an obscure engine-init failure rather than as a cache
     * miss.
     *
     * @return how many files were removed.
     */
    public suspend fun clearCache(): Int
}
