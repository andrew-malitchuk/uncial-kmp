package io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel

import kotlinx.coroutines.CancellationException

/**
 * Runs [block], returning [fallback] if it fails — but **never** swallowing cancellation.
 *
 * The same reasoning as `runCatchingOcr` in the runtime: `kotlin.runCatching` catches
 * `Throwable`, which includes `CancellationException`, so using it around a suspending
 * call turns "my caller cancelled me" into "this provider has nothing", and the failure
 * is then reported as `NoLanguageData` while the coroutine goes on believing it finished
 * normally. Language-data lookup sits on the extraction path, so it plays by the same
 * rule as the rest of it.
 */
internal inline fun <T> orElseOnFailure(fallback: T, block: () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        fallback
    }
