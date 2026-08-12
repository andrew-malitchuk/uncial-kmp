package io.github.andrewmalitchuk.uncial.model.source.error

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Everything that can go wrong, as a closed set.
 *
 * The POC returned `null` for every failure and printed the reason to stdout, which meant
 * a caller could not tell "this PDF has no text layer" from "you forgot to install
 * libtesseract" from "the file is corrupt". Each case here is one the caller can act on
 * differently. (PLAN.md §5.3)
 *
 * `OcrError` extends `Exception` so it can travel two ways without being redefined: as the
 * failure of a [Result] on the Kotlin side, and as a thrown, typed error on the Swift side
 * via `@Throws`. It is never thrown at a Kotlin caller who used a `Result`-returning API.
 *
 * ### Cancellation is not in here, on purpose
 *
 * Structured concurrency requires a cancelled coroutine's [kotlin.coroutines.cancellation.CancellationException]
 * to propagate untouched — swallowing it into a `Result` would leave the caller's scope
 * believing the work completed. Uncial therefore never wraps cancellation, and there is no
 * `OcrError.Cancelled`: if you cancel an extraction, you get `CancellationException`, which
 * is what every other suspend function in your codebase already does. This is a deliberate
 * deviation from the SDK's original error list.
 *
 * New subclasses may be added in minor releases; treat a `when` over this hierarchy as
 * needing an `else`.
 */
public sealed class OcrError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /**
     * The engine has no language model for the requested languages.
     *
     * The single most common setup failure: `.traineddata` was never supplied on Android,
     * or `brew install tesseract` was never run on a JVM host.
     *
     * @property languages the languages that could not be loaded.
     * @property searchedPath where Uncial looked, when it knows.
     */
    public class NoLanguageData(
        public val languages: List<OcrLanguage>,
        public val searchedPath: String? = null,
        cause: Throwable? = null,
    ) : OcrError(
        message = "no language data for ${languages.joinToString("+") { it.tesseractCode }}" +
            (searchedPath?.let { " (searched: $it)" } ?: ""),
        cause = cause,
    )

    /**
     * A page could not be turned into a raster.
     *
     * Usually a damaged or password-protected PDF, or a page so large that even
     * `OcrOptions.maxPageSide` could not save the allocation.
     *
     * @property pageIndex the page that failed, or `null` if the document could not be
     *   opened at all.
     */
    public class RenderFailed(
        public val pageIndex: Int? = null,
        message: String = "failed to rasterize" + (pageIndex?.let { " page $it" } ?: " document"),
        cause: Throwable? = null,
    ) : OcrError(message, cause)

    /**
     * The recognition engine could not be started.
     *
     * On Android this is typically a missing native library or a `TessBaseAPI.init`
     * refusal; on the JVM, JNA failing to find `libtesseract`.
     */
    public class EngineInit(
        message: String,
        cause: Throwable? = null,
    ) : OcrError(message, cause)

    /**
     * The requested operation cannot be performed on this platform or with the modules on
     * the classpath.
     *
     * Examples: asking for the digital text layer without `uncial-pdf-text`, or passing a
     * raster to a platform whose engine only accepts PDFs.
     */
    public class Unsupported(
        message: String,
        cause: Throwable? = null,
    ) : OcrError(message, cause)

    /**
     * The input is not something Uncial can read at all — empty bytes, not a PDF, or a
     * PDF whose structure is broken beyond recovery.
     */
    public class InvalidInput(
        message: String,
        cause: Throwable? = null,
    ) : OcrError(message, cause)

    /**
     * Recognition ran but the engine failed on a specific page.
     *
     * Distinguished from [RenderFailed] because the page rasterized fine — the engine
     * itself gave up, which usually means the page survives a retry at a different dpi.
     */
    public class RecognitionFailed(
        public val pageIndex: Int,
        message: String = "recognition failed on page $pageIndex",
        cause: Throwable? = null,
    ) : OcrError(message, cause)
}
