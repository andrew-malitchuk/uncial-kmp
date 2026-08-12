package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.text.TextScript
import kotlin.test.Test
import kotlin.test.assertEquals

class TextScriptTest {

    @Test
    fun `scripts are classified by letters only`() {
        assertEquals(TextScript.Cyrillic, TextScript.of("Історія"))
        assertEquals(TextScript.Latin, TextScript.of("History"))
        assertEquals(TextScript.Mixed, TextScript.of("Історія of Ukraine"))
        // Digits and punctuation must not make this Mixed.
        assertEquals(TextScript.Latin, TextScript.of("ISO 9001:2015"))
        assertEquals(TextScript.Unknown, TextScript.of("42 — 17"))
    }
}
