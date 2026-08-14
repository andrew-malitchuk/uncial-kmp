package io.github.andrewmalitchuk.uncial.core.source.log

/** Logs at [LogLevel.Info]. */
public fun OcrLogger.info(message: String): Unit = log(LogLevel.Info, message, null)
