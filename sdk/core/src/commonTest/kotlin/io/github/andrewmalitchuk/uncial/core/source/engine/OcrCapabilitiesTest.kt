package io.github.andrewmalitchuk.uncial.core.source.engine

import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `droppedAnyOf` is how a caller finds out that the engine quietly recognized something
 * other than what they asked for -- Vision without Ukrainian before iOS 16 being the case
 * this exists for.
 */
class OcrCapabilitiesTest {

    @Test
    fun `nothing dropped when every requested language survived`() {
        val capabilities = capabilities(OcrLanguage.Ukrainian, OcrLanguage.English)

        assertFalse(capabilities.droppedAnyOf(listOf(OcrLanguage.Ukrainian, OcrLanguage.English)))
    }

    @Test
    fun `a missing language is reported as dropped`() {
        val capabilities = capabilities(OcrLanguage.English)

        assertTrue(capabilities.droppedAnyOf(listOf(OcrLanguage.Ukrainian, OcrLanguage.English)))
    }

    @Test
    fun `an engine with no languages drops everything requested`() {
        assertTrue(capabilities().droppedAnyOf(listOf(OcrLanguage.English)))
    }

    @Test
    fun `requesting nothing drops nothing`() {
        assertFalse(capabilities().droppedAnyOf(emptyList()))
    }

    private fun capabilities(vararg languages: OcrLanguage) =
        OcrCapabilities(engineId = "test", languages = languages.toList())
}
