package io.github.andrewmalitchuk.uncial.samples.android.source.model

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress

/**
 * Everything the five screens render, and nothing else.
 *
 * The state is derived from `OcrProgress` and `OcrDocument` rather than holding them: a
 * screen wants "31 lines" and "94 %", the SDK returns pages of lines of words, and doing
 * that arithmetic in a composable would redo it on every recomposition of a list that is
 * already scrolling.
 */
data class OcrUiState(
    val capabilities: OcrCapabilities? = null,
    val hasDigitalTextLayer: Boolean = false,
    val run: RunState = RunState.Idle,
    val document: DocumentSummary? = null,
    val error: String? = null,
) {
    val isRunning: Boolean get() = run is RunState.Running
}
