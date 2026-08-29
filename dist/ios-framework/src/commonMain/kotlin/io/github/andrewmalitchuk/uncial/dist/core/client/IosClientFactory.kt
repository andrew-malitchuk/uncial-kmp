package io.github.andrewmalitchuk.uncial.dist.core.client

import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.core.source.log.LogLevel
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor

/**
 * Builds the client the façade hands to Swift.
 *
 * Every parameter is required here and defaulted on the façade: Obj-C export drops Kotlin
 * default arguments, so the defaults only exist for Kotlin callers and belong at the one
 * place Kotlin calls this.
 *
 * @param onLog receives diagnostics; pass `null` to discard them.
 */
internal fun buildIosClient(
    languages: List<OcrLanguage>,
    renderDpi: Int,
    includeWords: Boolean,
    preferDigitalLayer: Boolean,
    onLog: ((String) -> Unit)?,
): UncialClient = UncialClient {
    this.languages = languages
    this.renderDpi = renderDpi
    this.includeWords = includeWords
    this.preferDigitalLayer = preferDigitalLayer
    // PDFKit is a system framework, so on iOS the digital path costs nothing in binary
    // size and there is no reason not to wire it up by default.
    this.digitalTextExtractor = pdfTextExtractor()
    if (onLog != null) {
        this.logger = OcrLogger { level: LogLevel, message: String, _ ->
            onLog("[${level.name.lowercase()}] $message")
        }
    }
}
