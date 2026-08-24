package io.github.andrewmalitchuk.uncial.runtime.core.error

import io.github.andrewmalitchuk.uncial.model.source.error.OcrError

/**
 * Turns whatever a platform threw into a typed [OcrError].
 *
 * Engines throw whatever their native layer throws: `UnsatisfiedLinkError` from JNA,
 * `IllegalStateException` from `PdfRenderer`, `NSError`-derived failures from Vision. A
 * consumer cannot branch on those, and should not have to know them.
 */
internal object ErrorMapper {

    fun map(cause: Throwable): OcrError = when (cause) {
        is OcrError -> cause
        // OutOfMemoryError is JVM-only and cannot be named in common code; the Android and
        // JVM rasterizers map it to OcrError.RenderFailed where it actually happens.
        else -> OcrError.EngineInit(
            message = cause.message ?: cause::class.simpleName ?: "extraction failed",
            cause = cause,
        )
    }
}
