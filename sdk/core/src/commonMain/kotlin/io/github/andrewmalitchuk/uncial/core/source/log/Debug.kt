package io.github.andrewmalitchuk.uncial.core.source.log

/** Logs at [LogLevel.Debug]. */
public fun OcrLogger.debug(message: String): Unit = log(LogLevel.Debug, message, null)
