package io.github.andrewmalitchuk.uncial.engine.tesseract.source.language

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * "Use what the OS already has; fall back to what we ship."
 *
 * Tesseract takes ONE datapath, so the chain cannot split languages across providers -- it
 * has to pick the one covering the most of them.
 */
class ChainedLanguageDataProviderTest {

    @Test
    fun `available is the union of both providers`() = runTest {
        val chained = StubProvider("ukr" to "/system") + StubProvider("eng" to "/bundled")

        val available = chained.available(listOf(OcrLanguage.Ukrainian, OcrLanguage.English))

        assertContentEquals(listOf(OcrLanguage.Ukrainian, OcrLanguage.English), available)
    }

    @Test
    fun `available reports a language only once when both providers have it`() = runTest {
        val chained = StubProvider("ukr" to "/system") + StubProvider("ukr" to "/bundled")

        assertContentEquals(
            listOf(OcrLanguage.Ukrainian),
            chained.available(listOf(OcrLanguage.Ukrainian)),
        )
    }

    @Test
    fun `materialize picks the provider covering the most languages`() = runTest {
        val poor = StubProvider("ukr" to "/system")
        val rich = StubProvider("ukr" to "/bundled", "eng" to "/bundled")

        val datapath = (poor + rich).materialize(listOf(OcrLanguage.Ukrainian, OcrLanguage.English))

        assertEquals("/bundled", datapath)
    }

    @Test
    fun `an earlier provider wins a tie`() = runTest {
        val first = StubProvider("ukr" to "/system")
        val second = StubProvider("ukr" to "/bundled")

        assertEquals("/system", (first + second).materialize(listOf(OcrLanguage.Ukrainian)))
    }

    @Test
    fun `a provider that fails to materialize falls through to the next`() = runTest {
        val broken = StubProvider("ukr" to "/system", failMaterialize = true)
        val working = StubProvider("ukr" to "/bundled")

        assertEquals("/bundled", (broken + working).materialize(listOf(OcrLanguage.Ukrainian)))
    }

    @Test
    fun `a provider that throws while probing does not take the chain down`() = runTest {
        val broken = StubProvider(failAvailable = true)
        val working = StubProvider("ukr" to "/bundled")

        assertContentEquals(
            listOf(OcrLanguage.Ukrainian),
            (broken + working).available(listOf(OcrLanguage.Ukrainian)),
        )
    }

    @Test
    fun `no provider covering anything is NoLanguageData`() = runTest {
        val chained = StubProvider() + StubProvider()

        assertFailsWith<OcrError.NoLanguageData> {
            chained.materialize(listOf(OcrLanguage.Ukrainian))
        }
    }

    @Test
    fun `cancellation while probing is not turned into an empty result`() = runTest {
        val cancelling = object : LanguageDataProvider {
            override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
                throw CancellationException("cancelled")

            override suspend fun materialize(languages: List<OcrLanguage>): String = "/unused"
        }

        assertFailsWith<CancellationException> {
            (cancelling + StubProvider("ukr" to "/bundled")).available(listOf(OcrLanguage.Ukrainian))
        }
    }

    private class StubProvider(
        private vararg val models: Pair<String, String>,
        private val failAvailable: Boolean = false,
        private val failMaterialize: Boolean = false,
    ) : LanguageDataProvider {

        override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> {
            if (failAvailable) error("probe failed")
            return requested.filter { language -> models.any { it.first == language.tesseractCode } }
        }

        override suspend fun materialize(languages: List<OcrLanguage>): String {
            if (failMaterialize) error("materialize failed")
            return models.first { pair -> languages.any { it.tesseractCode == pair.first } }.second
        }
    }
}
