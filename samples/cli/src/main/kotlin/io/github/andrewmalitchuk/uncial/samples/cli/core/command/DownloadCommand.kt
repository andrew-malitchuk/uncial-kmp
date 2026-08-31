package io.github.andrewmalitchuk.uncial.samples.cli.core.command

import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.lang.download.source.provider.downloadingLanguageDataProvider
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.samples.cli.core.args.firstPathOrNull
import io.github.andrewmalitchuk.uncial.samples.cli.core.args.valueOf
import io.github.andrewmalitchuk.uncial.samples.cli.core.log.consoleLogger
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs OCR using language data fetched over the network rather than found on the system.
 *
 * The point is to exercise the path a consumer takes when they do not want 3.8-4.1 MB per
 * language inside their artifact: an empty cache directory, a download on first use, and a
 * checksum check before the bytes reach libtesseract.
 */
internal fun runWithDownloadedData(args: List<String>) = runBlocking {
    val path = args.firstPathOrNull() ?: run {
        System.err.println("download needs a PDF path")
        exitProcess(1)
    }
    val file = File(path)
    if (!file.isFile) {
        System.err.println("no such file: $path")
        exitProcess(1)
    }
    val cacheDirectory = args.valueOf("--cache")
        ?: File(System.getProperty("java.io.tmpdir"), "uncial-download-demo").path

    val languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    val provider = downloadingLanguageDataProvider(
        cacheDirectoryPath = cacheDirectory,
        logger = consoleLogger(verbose = true),
    )
    println("cache       $cacheDirectory")
    languages.forEach { language ->
        println("cached      ${language.tesseractCode}=${provider.isCached(language)}")
    }

    val started = System.currentTimeMillis()
    val prefetched = provider.prefetch(languages)
    println(
        "prefetched  ${prefetched.joinToString("+") { it.tesseractCode }} " +
            "in ${System.currentTimeMillis() - started}ms",
    )

    val client = UncialClient {
        this.languages = languages
        // The whole point: data comes from the downloader, not from the system install.
        languageData = provider
        logger = consoleLogger(verbose = false)
    }
    client.use {
        val document = it.extract(file.readBytes()).getOrElse { error ->
            System.err.println("extraction failed: $error")
            System.err.println("  caused by: ${error.cause}")
            exitProcess(3)
        }
        println("source      ${document.source}")
        println("pages       ${document.pageCount}")
        println("lines       ${document.lines.size}")
        val blocks = DocumentStructure.reconstruct(document)
        println("blocks      ${blocks.size}")
        blocks.filterIsInstance<DocBlock.Heading>().take(3).forEach { heading ->
            println("heading     ${heading.text}")
        }
    }
}
