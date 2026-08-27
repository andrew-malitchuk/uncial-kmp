package io.github.andrewmalitchuk.uncial.lang.download.source.tessdata

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Where downloaded `.traineddata` comes from, and how it is verified.
 *
 * ### Why the checksums are not optional in spirit
 *
 * A `.traineddata` file is fed straight into a native library. Downloading one over the
 * network and handing it to `libtesseract` without checking what arrived means trusting
 * the transport, the CDN and whoever can influence either. [checksums] is therefore part
 * of the source rather than an afterthought: supply the SHA-256 of every language you
 * fetch, pin them in your own build, and treat a mismatch as an attack rather than a
 * glitch.
 *
 * Leaving [checksums] empty is allowed — some consumers genuinely cannot pin hashes for a
 * model they let users choose at runtime — but then the only protection is HTTPS, and
 * [DownloadingLanguageDataProvider] will say so through its logger.
 *
 * @property baseUrl directory URL the language files sit under, with a trailing slash.
 *   The default is Tesseract's own `tessdata_fast` repository, which is what the
 *   `uncial-lang-*` artifacts are built from — so a downloaded model and a bundled one are
 *   the same bytes.
 * @property checksums language to expected hex SHA-256. Case and surrounding
 *   whitespace are ignored, so a hash pasted out of `sha256sum` or a CI log works
 *   as-is.
 */
public class TessDataSource(
    public val baseUrl: String = TESSDATA_FAST_BASE_URL,
    public val checksums: Map<OcrLanguage, String> = DEFAULT_CHECKSUMS,
) {
    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with '/', was '$baseUrl'" }
        require(baseUrl.startsWith("https://")) {
            "baseUrl must be https: a .traineddata goes straight into a native library"
        }
    }

    /** The URL for one language's model. */
    public fun urlFor(language: OcrLanguage): String =
        "$baseUrl${language.tesseractCode}.traineddata"

    /** The expected SHA-256 for [language], or `null` if none was pinned. */
    public fun checksumFor(language: OcrLanguage): String? = checksums[language]

    public companion object {
        /** `tessdata_fast` — the same models the `uncial-lang-*` artifacts embed. */
        public const val TESSDATA_FAST_BASE_URL: String =
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"

        /** `tessdata` — full models: 12 MB for ukr, 23 MB for eng. Slower, marginally better. */
        public const val TESSDATA_BASE_URL: String =
            "https://github.com/tesseract-ocr/tessdata/raw/main/"

        /**
         * Checksums for the two languages Uncial ships support for, against
         * [TESSDATA_FAST_BASE_URL]. Verified at the same time as the build-time download,
         * so a bundled and a downloaded model are byte-identical.
         */
        public val DEFAULT_CHECKSUMS: Map<OcrLanguage, String> = mapOf(
            OcrLanguage.Ukrainian to
                "d59e53e2bded32f4445f124b4b00240fcac7e8044c003ab822ccb94f0b3db59b",
            OcrLanguage.English to
                "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        )
    }
}
