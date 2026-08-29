package io.github.andrewmalitchuk.uncial.dist.core.extraction

import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import io.github.andrewmalitchuk.uncial.dist.source.model.IosRunProgress
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import kotlinx.coroutines.flow.Flow

/**
 * Collects a progress flow into its final document, reporting the rest through [onProgress].
 *
 * This is how `extractAsFlow` reaches Swift at all: a Kotlin `Flow` does not export, so the
 * flow is consumed here and each emission becomes a callback.
 *
 * The `Done` emission is where the document lives; everything before it is progress. A flow
 * that completes without one means the pipeline emitted nothing, which is a bug rather than
 * an empty result — an empty result is a `Done` carrying a document with no lines.
 */
internal suspend fun collectDocument(
    flow: Flow<OcrProgress>,
    onProgress: (IosRunProgress) -> Unit,
): OcrDocument {
    var document: OcrDocument? = null
    var source: String? = null
    flow.collect { progress ->
        when (progress) {
            is OcrProgress.Started -> {
                source = progress.source.name
                onProgress(IosRunProgress(0, progress.pageCount, source))
            }
            is OcrProgress.Page -> onProgress(
                IosRunProgress(progress.completed, progress.of, source),
            )
            is OcrProgress.Done -> document = progress.document
            // OcrProgress is documented as growing in minor releases.
            else -> Unit
        }
    }
    return document ?: throw OcrError.RecognitionFailed(
        pageIndex = 0,
        message = "the extraction completed without producing a document",
    )
}
