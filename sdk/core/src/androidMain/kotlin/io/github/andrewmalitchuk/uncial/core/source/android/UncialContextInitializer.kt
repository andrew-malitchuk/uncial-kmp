package io.github.andrewmalitchuk.uncial.core.source.android

import android.content.Context
import androidx.startup.Initializer

/**
 * Captures the application [Context] at process start, so nothing in the SDK's public API
 * has to ask for one.
 *
 * Tesseract needs a real filesystem path to its language data, which on Android means
 * needing a `Context` to find `filesDir` and to open the language artifacts' assets. The
 * POC solved that by making the consumer call `AndroidOcrSupport.init(this)` from
 * `MainActivity` — an initialization step that is easy to forget, impossible to enforce,
 * and fails at recognition time rather than at startup.
 *
 * `androidx.startup` runs this from a merged `ContentProvider` before
 * `Application.onCreate`, so by the time any consumer code can call Uncial, the context is
 * already there. Cost: `startup-runtime` as an `api` dependency, about 15 KB. (PLAN.md §5.5)
 */
public class UncialContextInitializer : Initializer<Context> {

    override fun create(context: Context): Context {
        val application = context.applicationContext
        UncialContext.install(application)
        return application
    }

    /** Nothing to run first. */
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
