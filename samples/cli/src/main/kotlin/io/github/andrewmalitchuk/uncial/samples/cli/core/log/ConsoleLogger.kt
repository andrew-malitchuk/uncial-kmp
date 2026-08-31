package io.github.andrewmalitchuk.uncial.samples.cli.core.log

import io.github.andrewmalitchuk.uncial.core.source.log.LogLevel
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger

/**
 * The SDK's diagnostics, on stdout — or stderr for errors, so a shell redirect still
 * separates them.
 */
internal fun consoleLogger(verbose: Boolean): OcrLogger = OcrLogger { level, message, throwable ->
    if (!verbose && level == LogLevel.Debug) return@OcrLogger
    val stream = if (level == LogLevel.Error) System.err else System.out
    stream.println("[${level.name.lowercase()}] $message")
    throwable?.printStackTrace(stream)
}
