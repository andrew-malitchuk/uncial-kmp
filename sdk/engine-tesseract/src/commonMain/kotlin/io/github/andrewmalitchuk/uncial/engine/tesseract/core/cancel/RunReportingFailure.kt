package io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel

import kotlinx.coroutines.CancellationException

/**
 * Runs [block], handing any failure to [onFailure] — rethrowing cancellation untouched.
 *
 * @return `true` when [block] completed, `false` when it failed.
 */
internal inline fun runReportingFailure(block: () -> Unit, onFailure: (Throwable) -> Unit): Boolean =
    try {
        block()
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (cause: Throwable) {
        onFailure(cause)
        false
    }
