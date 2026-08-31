package io.github.andrewmalitchuk.uncial.samples.cli.source.entrypoint

import io.github.andrewmalitchuk.uncial.engine.tesseract.source.install.installTesseractEngine
import io.github.andrewmalitchuk.uncial.samples.cli.core.command.makeFixture
import io.github.andrewmalitchuk.uncial.samples.cli.core.command.reportCapabilities
import io.github.andrewmalitchuk.uncial.samples.cli.core.command.runOcr
import io.github.andrewmalitchuk.uncial.samples.cli.core.command.runWithDownloadedData
import kotlin.system.exitProcess

private const val USAGE = """
Uncial JVM harness

  ocr <file.pdf> [--dpi N] [--words] [--no-digital] [--quiet]
      Extract a document and print its structure.

  fixture <out.pdf> [--pages N]
      Generate a SCANNED (image-only, no text layer) Ukrainian PDF, so the OCR path is
      what gets exercised rather than the digital one.

  capabilities
      Report what this machine's engine can do.

  download <file.pdf> [--cache DIR]
      Same as ocr, but fetch .traineddata over the network into an empty cache instead of
      using the system Tesseract data. Proves the runtime-download path.
"""

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println(USAGE)
        exitProcess(1)
    }
    // The JVM has no androidx.startup, so the engine is registered explicitly. Once.
    installTesseractEngine()

    when (val command = args.first()) {
        "ocr" -> runOcr(args.drop(1))
        "fixture" -> makeFixture(args.drop(1))
        "capabilities" -> reportCapabilities()
        "download" -> runWithDownloadedData(args.drop(1))
        else -> {
            System.err.println("unknown command: $command")
            println(USAGE)
            exitProcess(1)
        }
    }
}
