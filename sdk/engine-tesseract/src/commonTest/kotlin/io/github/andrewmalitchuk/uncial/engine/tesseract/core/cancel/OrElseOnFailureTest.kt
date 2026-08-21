package io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The repository bans `kotlin.runCatching` on the extraction path because it swallows
 * `CancellationException`. These two tests are what keeps the replacement honest -- a
 * regression here turns "my caller cancelled me" into "this provider has nothing", and the
 * coroutine then goes on believing it finished normally.
 */
class OrElseOnFailureTest {

    @Test
    fun `it returns the block's value when the block succeeds`() {
        assertEquals("ok", orElseOnFailure(fallback = "fallback") { "ok" })
    }

    @Test
    fun `it returns the fallback on an ordinary failure`() {
        assertEquals("fallback", orElseOnFailure(fallback = "fallback") { error("boom") })
    }

    @Test
    fun `it rethrows cancellation instead of swallowing it`() {
        assertFailsWith<CancellationException> {
            orElseOnFailure(fallback = "fallback") { throw CancellationException("cancelled") }
        }
    }
}
