package io.github.andrewmalitchuk.uncial.engine.tesseract.core.cancel

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunReportingFailureTest {

    @Test
    fun `a completed block reports true and nothing to the handler`() {
        var reported: Throwable? = null

        val completed = runReportingFailure({ }, onFailure = { reported = it })

        assertTrue(completed)
        assertNull(reported)
    }

    @Test
    fun `a failed block reports false and hands the cause over`() {
        var reported: Throwable? = null

        val completed = runReportingFailure({ error("boom") }, onFailure = { reported = it })

        assertFalse(completed)
        assertEquals("boom", reported?.message)
    }

    @Test
    fun `cancellation is rethrown and never reported as a failure`() {
        var reported: Throwable? = null

        assertFailsWith<CancellationException> {
            runReportingFailure(
                { throw CancellationException("cancelled") },
                onFailure = { reported = it },
            )
        }
        assertNull(reported, "a cancelled run is not a failed one")
    }
}
