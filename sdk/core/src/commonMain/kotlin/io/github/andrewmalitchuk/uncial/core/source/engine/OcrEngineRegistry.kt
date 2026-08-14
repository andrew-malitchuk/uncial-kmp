package io.github.andrewmalitchuk.uncial.core.source.engine

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Where engines announce themselves, so the runtime never has to name one.
 *
 * `uncial-runtime` must not depend on `uncial-engine-tesseract` or
 * `uncial-engine-vision` — that is the rule the whole module graph is arranged around
 * (PLAN.md §6.6). But a consumer should not have to write platform-specific code to pick
 * an engine either. The registry resolves both: engine modules put themselves in, the
 * runtime asks for whatever is there.
 *
 * On Android the Tesseract engine registers itself at process start through
 * `androidx.startup`, so adding the dependency is genuinely all that is required. On other
 * platforms, call the engine module's `install...` function once:
 *
 * ```
 * installVisionEngine()          // iOS
 * installTesseractEngine()       // JVM
 * ```
 *
 * Passing an engine explicitly to `UncialClient`'s builder bypasses the registry
 * entirely — which is what tests do with `FakeOcrEngine`.
 *
 * ### Thread safety
 *
 * Registration is lock-free: the list is immutable and swapped atomically. It has to be,
 * because on Android an initializer registers from the startup thread while application
 * code may already be reading.
 */
@OptIn(ExperimentalAtomicApi::class)
public object OcrEngineRegistry {

    private val registered = AtomicReference<List<OcrEngineFactory>>(emptyList())

    private val lastFailure = AtomicReference<Throwable?>(null)

    /**
     * Adds [factory], replacing any previously registered engine with the same
     * [OcrEngineFactory.id].
     *
     * Idempotent, so an engine module can call it from both an initializer and an explicit
     * `install...()` without ending up registered twice.
     */
    public fun register(factory: OcrEngineFactory) {
        update { current -> current.filterNot { it.id == factory.id } + factory }
    }

    /** Removes the engine with this id, if present. Intended for tests. */
    public fun unregister(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    /** Every registered engine, in registration order. */
    public fun engines(): List<OcrEngineFactory> = registered.load()

    /**
     * The first registered engine that reports itself usable here.
     *
     * A factory that throws while probing is treated as unavailable rather than being
     * allowed to take the whole lookup down — one broken third-party engine must not hide
     * a working one registered after it — but the failure is kept in
     * [lastProbeFailure] so that "no OCR engine available" can be explained.
     *
     * @return `null` when no engine module is on the classpath, or when the only ones
     *   present cannot run — a JVM without `libtesseract`, for instance.
     */
    public fun firstAvailable(): OcrEngineFactory? {
        // Cleared before probing, not after: this is a process-global object, so a failure
        // left over from an unrelated earlier lookup would otherwise be attached as the
        // cause of a later "no OCR engine available" whose real reason is that nothing is
        // registered at all -- blaming an engine the caller may not even have installed.
        lastFailure.store(null)
        return engines().firstOrNull { it.probe() }
    }

    /**
     * The exception thrown by an [OcrEngineFactory.isAvailable] probe during the most
     * recent [firstAvailable] lookup, or `null` if that lookup had none.
     *
     * A diagnostic, not control flow: an engine that blows up while deciding whether it
     * can run disappears from [firstAvailable] and the caller is left with a generic
     * "no OCR engine available". Report this alongside that message — it is the only
     * record of why the engine vanished. Scoped to one lookup so it always answers "why
     * did the lookup I just performed come back empty", which a value kept indefinitely
     * cannot: the next lookup resets it, whether or not anything fails that time.
     */
    public fun lastProbeFailure(): Throwable? = lastFailure.load()

    private fun OcrEngineFactory.probe(): Boolean =
        try {
            isAvailable()
        } catch (cause: Throwable) {
            // Caught explicitly rather than with runCatching, which would read as "the
            // result does not matter". It does: this is deliberately non-fatal, and
            // isAvailable does not suspend so there is no CancellationException at risk
            // here -- the only thing lost is the diagnostic, which is why it is kept.
            lastFailure.store(cause)
            false
        }

    private inline fun update(transform: (List<OcrEngineFactory>) -> List<OcrEngineFactory>) {
        while (true) {
            val current = registered.load()
            if (registered.compareAndSet(current, transform(current))) return
        }
    }
}
