package io.github.andrewmalitchuk.uncial.samples.cli.core.command

import io.github.andrewmalitchuk.uncial.runtime.source.progress.OcrProgress
import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.pdftext.source.extractor.pdfTextExtractor
import io.github.andrewmalitchuk.uncial.samples.cli.core.args.firstPathOrNull
import io.github.andrewmalitchuk.uncial.samples.cli.core.args.valueOf
import io.github.andrewmalitchuk.uncial.samples.cli.core.log.consoleLogger
import io.github.andrewmalitchuk.uncial.samples.cli.core.report.printDocumentReport
import io.github.andrewmalitchuk.uncial.samples.cli.core.report.printStructure
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/** Extracts a document and prints its structure. */
internal fun runOcr(args: List<String>) = runBlocking {
    val path = args.firstPathOrNull() ?: run {
        System.err.println("ocr needs a PDF path")
        exitProcess(1)
    }
    val file = File(path)
    if (!file.isFile) {
        System.err.println("no such file: $path")
        exitProcess(1)
    }
    val quiet = "--quiet" in args
    val includeWords = "--words" in args
    val dpi = args.valueOf("--dpi")?.toIntOrNull() ?: 200

    val client = UncialClient {
        languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
        renderDpi = dpi
        this.includeWords = includeWords
        preferDigitalLayer = "--no-digital" !in args
        digitalTextExtractor = pdfTextExtractor()
        logger = consoleLogger(verbose = !quiet)
    }

    client.use {
        val capabilities = it.capabilities
        if (capabilities == null) {
            System.err.println("no engine available; try: brew install tesseract tesseract-lang")
            exitProcess(2)
        }
        println(
            "engine ${capabilities.engineId} " +
                "(${capabilities.languages.joinToString("+") { l -> l.tesseractCode }}) @ $dpi dpi",
        )

        val started = System.currentTimeMillis()
        val document = it.collectDocument(file, quiet)
        val elapsed = System.currentTimeMillis() - started

        printDocumentReport(document, includeWords = includeWords, elapsedMs = elapsed)
        printStructure(document)
    }
}

/**
 * Runs the extraction through the flow rather than `extract()`.
 *
 * Per-page progress is the whole reason the flow exists, and a 300-page scan is exactly
 * where a caller needs it.
 *
 * The `else` is redundant today and kept anyway: `OcrProgress` is documented as growing in
 * minor releases.
 */
@Suppress("REDUNDANT_ELSE_IN_WHEN")
private suspend fun UncialClient.collectDocument(file: File, quiet: Boolean): OcrDocument = try {
    var result: OcrDocument? = null
    extractAsFlow(file.readBytes()).collect { progress ->
        when (progress) {
            is OcrProgress.Started ->
                println("processing ${progress.pageCount} pages (${progress.source})")
            is OcrProgress.Page -> if (!quiet) {
                println(
                    "  page ${progress.index + 1}/${progress.of} " +
                        "— ${progress.page.lines.size} lines " +
                        "(${(progress.fraction * 100).toInt()}%)",
                )
            }
            is OcrProgress.Done -> result = progress.document
            else -> Unit
        }
    }
    requireNotNull(result) { "the flow completed without emitting a document" }
} catch (error: Throwable) {
    System.err.println("extraction failed: $error")
    // The cause chain is where the real reason lives -- a typed OcrError wraps whatever
    // the native layer threw.
    var cause = error.cause
    while (cause != null) {
        System.err.println("  caused by: $cause")
        cause = cause.cause
    }
    exitProcess(3)
}
