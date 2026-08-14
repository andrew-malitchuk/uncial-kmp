package io.github.andrewmalitchuk.uncial.core.source.engine

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The registry is a process-wide object, so every test cleans up after itself — a leftover
 * engine would leak into whatever runs next.
 */
class OcrEngineRegistryTest {

    @AfterTest
    fun cleanUp() {
        OcrEngineRegistry.engines().forEach { OcrEngineRegistry.unregister(it.id) }
    }

    @Test
    fun `registering twice replaces rather than duplicates`() {
        OcrEngineRegistry.register(StubEngineFactory("stub"))
        OcrEngineRegistry.register(StubEngineFactory("stub"))

        assertEquals(1, OcrEngineRegistry.engines().size)
    }

    @Test
    fun `a factory that throws while probing is skipped and does not hide the next one`() {
        OcrEngineRegistry.register(StubEngineFactory("broken", probeFailure = ProbeBlewUp()))
        OcrEngineRegistry.register(StubEngineFactory("working"))

        assertEquals("working", assertNotNull(OcrEngineRegistry.firstAvailable()).id)
    }

    @Test
    fun `the probe failure is kept so the caller can say why the engine vanished`() {
        val failure = ProbeBlewUp()
        OcrEngineRegistry.register(StubEngineFactory("broken", probeFailure = failure))
        assertNull(OcrEngineRegistry.firstAvailable())

        // Without this the caller only ever sees "no OCR engine available". Asserted by
        // identity rather than by type: cleanUp() can unregister engines but cannot reach
        // the registry's failure, so a sibling test's ProbeBlewUp would satisfy a type
        // check even if probe() had stopped recording anything at all.
        assertSame(failure, OcrEngineRegistry.lastProbeFailure())
    }

    @Test
    fun `a lookup clears the previous failure so the diagnostic describes that lookup`() {
        OcrEngineRegistry.register(StubEngineFactory("broken", probeFailure = ProbeBlewUp()))
        assertNull(OcrEngineRegistry.firstAvailable())
        assertNotNull(OcrEngineRegistry.lastProbeFailure())

        OcrEngineRegistry.unregister("broken")
        OcrEngineRegistry.register(StubEngineFactory("working"))
        assertNotNull(OcrEngineRegistry.firstAvailable())

        // The failure belongs to a lookup that is over. Left in place it becomes the cause
        // of the next "no OCR engine available", which then blames a broken engine for a
        // registry that is merely empty.
        assertNull(OcrEngineRegistry.lastProbeFailure())
    }
}

private class ProbeBlewUp : IllegalStateException("no native library here")

/**
 * The smallest thing that satisfies the contract: `core` has no engine to test with, and
 * depending on `engine-fake` would invert the module graph.
 */
private class StubEngineFactory(
    override val id: String,
    private val available: Boolean = true,
    private val probeFailure: Throwable? = null,
) : OcrEngineFactory {

    override fun isAvailable(): Boolean = probeFailure?.let { throw it } ?: available

    override fun capabilities(options: OcrOptions): OcrCapabilities = OcrCapabilities(
        engineId = id,
        engineVersion = "stub",
        languages = OcrLanguage.Default,
    )

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer = error("not needed: these tests never get as far as recognition")
}
