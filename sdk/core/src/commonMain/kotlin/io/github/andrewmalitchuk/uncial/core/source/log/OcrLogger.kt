package io.github.andrewmalitchuk.uncial.core.source.log

/**
 * Where Uncial's diagnostics go.
 *
 * The POC printed to stdout, which is not something a library gets to do: it pollutes the
 * consumer's logs, cannot be turned off, and is invisible on iOS. Uncial logs nothing
 * unless a logger is supplied. (PLAN.md §5.3)
 */
public fun interface OcrLogger {

    /**
     * Records one message.
     *
     * Called from whatever coroutine the extraction is running on, including during
     * per-page work, so implementations should be quick and must not throw.
     */
    public fun log(level: LogLevel, message: String, throwable: Throwable?)

    public companion object {
        /** Discards everything. The default. */
        public val None: OcrLogger = OcrLogger { _, _, _ -> }
    }
}
