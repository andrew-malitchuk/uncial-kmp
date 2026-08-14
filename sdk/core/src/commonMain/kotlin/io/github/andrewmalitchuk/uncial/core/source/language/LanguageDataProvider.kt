package io.github.andrewmalitchuk.uncial.core.source.language

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Supplies Tesseract's `.traineddata` files.
 *
 * Tesseract cannot be handed a stream: it needs a real directory containing a `tessdata/`
 * subdirectory. Where that directory comes from differs per platform and per consumer —
 * copied out of an AAR's assets, installed by Homebrew, downloaded at runtime, or shipped
 * inside the app — so it is an interface rather than a policy.
 *
 * This is also the "bring your own traineddata" mechanism from PLAN.md §2: pair a
 * `OcrLanguage.custom("pol")` with a provider that can materialize `pol.traineddata` and
 * Uncial will use it, with no change to the SDK.
 *
 * Engines that need no data — Vision, whose languages ship with iOS — ignore the provider
 * entirely.
 */
public interface LanguageDataProvider {

    /**
     * Which of [requested] this provider can actually supply.
     *
     * Called before [materialize] so the runtime can report an honest
     * `OcrCapabilities.languages` and fail fast with
     * `OcrError.NoLanguageData` rather than letting Tesseract fail obscurely.
     */
    public suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage>

    /**
     * Puts the data for [languages] on the local filesystem and returns the **parent** of
     * the `tessdata/` directory.
     *
     * That is: if this returns `/data/user/0/app/files`, then
     * `/data/user/0/app/files/tessdata/ukr.traineddata` must exist. This mirrors
     * Tesseract's own `datapath` convention, quirk included, rather than inventing a
     * nicer one that would need translating back at every call site.
     *
     * Implementations should be idempotent and cheap on repeat calls — the runtime may
     * call this once per extraction.
     *
     * @throws io.github.andrewmalitchuk.uncial.model.OcrError.NoLanguageData if none of
     *   [languages] can be materialized.
     */
    public suspend fun materialize(languages: List<OcrLanguage>): String
}
