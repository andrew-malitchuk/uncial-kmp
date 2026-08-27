package io.github.andrewmalitchuk.uncial.lang.download.core.verification

import io.github.andrewmalitchuk.uncial.core.source.log.LogLevel
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadVerificationTest {

    @Test
    fun `a matching checksum passes`() {
        val logger = RecordingLogger()
        DownloadVerification.verify(
            language = OcrLanguage.Ukrainian,
            bytes = ByteArray(1024),
            expected = PINNED,
            actual = { PINNED },
            logger = logger,
        )
        assertTrue(logger.messages.any { it.first == LogLevel.Info })
    }

    @Test
    fun `a mismatched checksum is fatal rather than a warning`() {
        // The bytes go straight into a native library, so a model that is not what we
        // expected must never be written to the cache.
        val error = assertFailsWith<OcrError.NoLanguageData> {
            DownloadVerification.verify(
                language = OcrLanguage.Ukrainian,
                bytes = ByteArray(1024),
                expected = PINNED,
                actual = { "0".repeat(64) },
                logger = RecordingLogger(),
            )
        }
        assertEquals(listOf(OcrLanguage.Ukrainian), error.languages)
        assertTrue("checksum mismatch" in (error.cause?.message ?: ""))
    }

    @Test
    fun `a pin is compared case-insensitively and without surrounding whitespace`() {
        // Pins get copied out of `sha256sum`, a CI log or a vendor's page, and half of
        // those print uppercase and/or with a trailing newline. Comparing raw would turn
        // a correct pin into a permanent "checksum mismatch" -- which this SDK tells the
        // consumer to read as an attack.
        val logger = RecordingLogger()
        DownloadVerification.verify(
            language = OcrLanguage.Ukrainian,
            bytes = ByteArray(1024),
            expected = PINNED.uppercase(),
            actual = { PINNED },
            logger = logger,
        )
        DownloadVerification.verify(
            language = OcrLanguage.Ukrainian,
            bytes = ByteArray(1024),
            expected = "  $PINNED\n",
            actual = { "\t${PINNED.uppercase()} " },
            logger = logger,
        )
        assertEquals(2, logger.messages.count { it.first == LogLevel.Info })
    }

    @Test
    fun `normalization does not make a different digest match`() {
        // The other half of the case above: normalizing must only paper over formatting.
        assertFailsWith<OcrError.NoLanguageData> {
            DownloadVerification.verify(
                language = OcrLanguage.Ukrainian,
                bytes = ByteArray(1024),
                expected = PINNED.uppercase(),
                actual = { PINNED.dropLast(1) + "C" },
                logger = RecordingLogger(),
            )
        }
    }

    @Test
    fun `an unpinned language is allowed but warned about`() {
        val logger = RecordingLogger()
        DownloadVerification.verify(
            language = OcrLanguage.custom("pol"),
            bytes = ByteArray(1024),
            expected = null,
            actual = { error("must not be hashed when nothing is pinned") },
            logger = logger,
        )
        val warning = logger.messages.single { it.first == LogLevel.Warning }
        assertTrue("no checksum pinned" in warning.second, warning.second)
    }

    @Test
    fun `an empty download fails`() {
        assertFailsWith<OcrError.NoLanguageData> {
            DownloadVerification.verify(
                language = OcrLanguage.English,
                bytes = ByteArray(0),
                expected = null,
                actual = { "" },
                logger = RecordingLogger(),
            )
        }
    }
}

private class RecordingLogger : OcrLogger {
    val messages = mutableListOf<Pair<LogLevel, String>>()
    override fun log(level: LogLevel, message: String, throwable: Throwable?) {
        messages += level to message
    }
}
