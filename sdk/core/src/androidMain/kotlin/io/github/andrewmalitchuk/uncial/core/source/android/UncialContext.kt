package io.github.andrewmalitchuk.uncial.core.source.android

import android.annotation.SuppressLint
import android.content.Context

/**
 * Holds the application context captured by [UncialContextInitializer].
 *
 * An application context outlives every consumer of it, so holding it in a process-wide
 * holder leaks nothing — this is the one Android singleton that is not a mistake.
 *
 * [install] is public as an escape hatch: an app that strips `InitializationProvider` from
 * its merged manifest (or runs Uncial in a process where startup did not go through the
 * usual path) can supply the context itself from `Application.onCreate`.
 */
public object UncialContext {

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var context: Context? = null

    /** Supplies the application context manually. Idempotent. */
    public fun install(applicationContext: Context) {
        context = applicationContext.applicationContext
    }

    /**
     * @return the application context, or `null` if `androidx.startup` never ran — which
     *   happens when a consumer strips the provider from the merged manifest.
     */
    public fun get(): Context? = context
}
