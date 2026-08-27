package io.github.andrewmalitchuk.uncial.lang.download.core.verification

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.info
import io.github.andrewmalitchuk.uncial.core.source.log.warn
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage

/**
 * Shared logic: what to do once bytes have arrived.
 *
 * Kept in common code so the Android and JVM implementations cannot drift on the part that
 * matters — verification.
 */
internal object DownloadVerification {

    /**
     * Checks downloaded [bytes] against the pinned checksum, if there is one.
     *
     * @throws OcrError.NoLanguageData when a pinned checksum does not match. This is
     *   deliberately fatal: a model that is not what we expected must never reach the
     *   native layer.
     */
    fun verify(
        language: OcrLanguage,
        bytes: ByteArray,
        expected: String?,
        actual: () -> String,
        logger: OcrLogger,
    ) {
        if (bytes.isEmpty()) {
            throw OcrError.NoLanguageData(listOf(language), cause = null)
        }
        if (expected == null) {
            logger.warn(
                "no checksum pinned for ${language.tesseractCode}: the downloaded model is " +
                    "trusted on the strength of HTTPS alone",
            )
            return
        }
        // Normalized on both sides: a pin is copied out of `sha256sum`, a CI log or a
        // vendor's page, and half of those print uppercase hex. Comparing raw would turn
        // a correct-but-uppercase pin into a permanent "checksum mismatch" -- which the
        // KDoc above tells the consumer to read as an attack.
        val digest = actual().trim().lowercase()
        val pinned = expected.trim().lowercase()
        if (digest != pinned) {
            throw OcrError.NoLanguageData(
                languages = listOf(language),
                cause = IllegalStateException(
                    "checksum mismatch for ${language.tesseractCode}: " +
                        "expected $pinned, got $digest",
                ),
            )
        }
        logger.info("${language.tesseractCode}.traineddata verified (${bytes.size / 1024} KiB)")
    }
}
