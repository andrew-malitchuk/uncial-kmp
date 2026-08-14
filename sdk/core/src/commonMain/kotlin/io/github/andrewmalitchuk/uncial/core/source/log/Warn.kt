package io.github.andrewmalitchuk.uncial.core.source.log

/** Logs at [LogLevel.Warning]. */
public fun OcrLogger.warn(message: String, throwable: Throwable? = null): Unit =
    log(LogLevel.Warning, message, throwable)
