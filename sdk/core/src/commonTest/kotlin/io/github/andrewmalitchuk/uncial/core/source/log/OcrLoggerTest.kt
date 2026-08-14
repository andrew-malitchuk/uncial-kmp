package io.github.andrewmalitchuk.uncial.core.source.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class OcrLoggerTest {

    @Test
    fun `each extension logs at its own level`() {
        val recorded = mutableListOf<Pair<LogLevel, String>>()
        val logger = OcrLogger { level, message, _ -> recorded += level to message }

        logger.debug("d")
        logger.info("i")
        logger.warn("w")
        logger.error("e")

        assertEquals(
            listOf(
                LogLevel.Debug to "d",
                LogLevel.Info to "i",
                LogLevel.Warning to "w",
                LogLevel.Error to "e",
            ),
            recorded,
        )
    }

    @Test
    fun `debug and info carry no throwable`() {
        var seen: Throwable? = IllegalStateException("not cleared")
        val logger = OcrLogger { _, _, throwable -> seen = throwable }

        logger.debug("d")
        assertNull(seen)
    }

    @Test
    fun `warn and error pass the throwable through`() {
        val cause = IllegalStateException("boom")
        var seen: Throwable? = null
        val logger = OcrLogger { _, _, throwable -> seen = throwable }

        logger.warn("w", cause)
        assertSame(cause, seen)

        seen = null
        logger.error("e", cause)
        assertSame(cause, seen)
    }

    @Test
    fun `the default logger says nothing and does not throw`() {
        OcrLogger.None.error("ignored", IllegalStateException("ignored"))
    }
}
