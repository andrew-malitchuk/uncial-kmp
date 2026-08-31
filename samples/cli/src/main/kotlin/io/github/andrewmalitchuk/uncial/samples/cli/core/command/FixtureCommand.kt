package io.github.andrewmalitchuk.uncial.samples.cli.core.command

import io.github.andrewmalitchuk.uncial.samples.cli.core.args.firstPathOrNull
import io.github.andrewmalitchuk.uncial.samples.cli.core.args.valueOf
import io.github.andrewmalitchuk.uncial.samples.cli.core.fixture.writeDigitalPdf
import io.github.andrewmalitchuk.uncial.samples.cli.core.fixture.writeScannedPdf
import java.io.File
import kotlin.system.exitProcess

/**
 * Generates a **scanned** Ukrainian PDF: the text is rasterized into an image and the
 * image is what goes into the PDF, so the document has no text layer at all.
 *
 * That distinction is the point. A PDF generated the normal way carries its text, and
 * Uncial would take the digital fast path and never touch an OCR engine — so it would
 * prove nothing about recognition. This produces the input the SDK actually exists for.
 *
 * `--text` asks for the counterpart instead: a real text layer, so the fast path can be
 * demonstrated too.
 */
internal fun makeFixture(args: List<String>) {
    val path = args.firstPathOrNull() ?: run {
        System.err.println("fixture needs an output path")
        exitProcess(1)
    }
    val pageCount = args.valueOf("--pages")?.toIntOrNull() ?: 3
    val file = File(path)
    file.parentFile?.mkdirs()

    if ("--text" in args) {
        writeDigitalPdf(file, pageCount, ascii = "--ascii" in args)
        return
    }

    writeScannedPdf(file, pageCount)
    println("wrote ${file.absolutePath} — $pageCount page(s), image-only (no text layer)")
    println("now run:  ./gradlew :samples:cli:run --args=\"ocr ${file.absolutePath}\"")
}
