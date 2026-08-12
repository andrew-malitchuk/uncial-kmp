package io.github.andrewmalitchuk.uncial.model.source.language

/**
 * A language to recognize.
 *
 * This is deliberately **not** an enum. Tesseract can load any `.traineddata` a caller
 * drops in, and closing the set would break the "bring your own language data" promise
 * the moment somebody needs Polish, Old Church Slavonic, or a model they trained
 * themselves. The two languages Uncial ships support for are constants; everything else
 * goes through [custom].
 *
 * Each language carries both identifiers Uncial needs, because the engines disagree:
 * Tesseract keys off a `.traineddata` filename ([tesseractCode]), Vision off a BCP-47 tag
 * ([bcp47]).
 *
 * @property tesseractCode the ISO 639-2/T code Tesseract uses as its `.traineddata`
 *   basename, e.g. `ukr`. Used by the Tesseract engines on Android and JVM.
 * @property bcp47 the BCP-47 tag Apple Vision expects, e.g. `uk-UA`, or `null` for a
 *   language Vision cannot recognize. A `null` here means the iOS engine will skip this
 *   language rather than fail — check `UncialClient.capabilities` to see what actually
 *   got used.
 */
public class OcrLanguage private constructor(
    public val tesseractCode: String,
    public val bcp47: String?,
) {
    override fun toString(): String = tesseractCode

    override fun equals(other: Any?): Boolean =
        this === other || (other is OcrLanguage && other.tesseractCode == tesseractCode)

    override fun hashCode(): Int = tesseractCode.hashCode()

    public companion object {
        /** Ukrainian — the language Uncial was built for, and the reason ML Kit is unusable. */
        public val Ukrainian: OcrLanguage = OcrLanguage("ukr", "uk-UA")

        public val English: OcrLanguage = OcrLanguage("eng", "en-US")

        /** Both shipped languages, in the order engines should prefer them. */
        public val Default: List<OcrLanguage> = listOf(Ukrainian, English)

        /**
         * Declares a language Uncial does not ship support for.
         *
         * Supplying the `.traineddata` is the caller's job — see `LanguageDataProvider`.
         * Nothing here validates that the model exists; a missing one surfaces as
         * [OcrError.NoLanguageData] when recognition starts.
         *
         * The code becomes a filename and a URL path segment, so it is restricted to
         * `[A-Za-z0-9_-]`. Without that, `custom("../../etc/x")` would make a downloading
         * provider write network-fetched bytes outside its cache directory.
         *
         * @param tesseractCode the `.traineddata` basename, e.g. `pol`.
         * @param bcp47 the Vision tag, if Vision supports this language at all.
         */
        public fun custom(tesseractCode: String, bcp47: String? = null): OcrLanguage {
            require(tesseractCode.matches(TESSERACT_CODE)) {
                "tesseractCode must match ${TESSERACT_CODE.pattern}, was '$tesseractCode'"
            }
            return OcrLanguage(tesseractCode, bcp47)
        }

        /**
         * What a [tesseractCode] may contain.
         *
         * It is interpolated into both a file path and a download URL, so anything that
         * could escape either — separators, dots, whitespace — is rejected outright.
         */
        private val TESSERACT_CODE: Regex = Regex("[A-Za-z0-9_-]{1,32}")
    }
}
