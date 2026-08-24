package io.github.andrewmalitchuk.uncial.runtime.core.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and captures failure as a [Result], **without swallowing cancellation**.
 *
 * `kotlin.runCatching` catches `Throwable`, which includes `CancellationException` — so
 * using it inside a coroutine converts "my caller cancelled me" into "the operation failed
 * normally", and the caller's scope goes on believing the work completed. That bug is
 * subtle enough, and common enough, that Uncial does not use `runCatching` anywhere in the
 * extraction path.
 */
internal inline fun <T> runCatchingOcr(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (cause: Throwable) {
        Result.failure(ErrorMapper.map(cause))
    }
