package uncial.convention.source.language

import org.gradle.api.provider.Property

/**
 * The `languageData { }` block a `lang-*` module declares itself with.
 *
 * ```
 * languageData {
 *     language = "ukr"
 *     sha256 = "d59e..."
 * }
 * ```
 *
 * Two values, because that is the whole configuration surface of a language module: which
 * model, and which exact bytes of it.
 */
abstract class LanguageDataExtension {

    /** Tesseract language code, i.e. the `.traineddata` basename. */
    abstract val language: Property<String>

    /** Expected SHA-256 of the model, lowercase hex. */
    abstract val sha256: Property<String>
}
