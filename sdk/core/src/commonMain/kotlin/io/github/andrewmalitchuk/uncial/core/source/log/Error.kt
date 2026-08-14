package io.github.andrewmalitchuk.uncial.core.source.log

/** Logs at [LogLevel.Error]. */
public fun OcrLogger.error(message: String, throwable: Throwable? = null): Unit =
    log(LogLevel.Error, message, throwable)
