package io.github.andrewmalitchuk.uncial.model.source.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OcrLanguageTest {

    @Test
    fun `shipped languages carry both identifiers the engines need`() {
        assertEquals("ukr", OcrLanguage.Ukrainian.tesseractCode)
        assertEquals("uk-UA", OcrLanguage.Ukrainian.bcp47)
        assertEquals("eng", OcrLanguage.English.tesseractCode)
    }

    @Test
    fun `a custom language keeps the set open for bring-your-own traineddata`() {
        val polish = OcrLanguage.custom("pol")
        assertEquals("pol", polish.tesseractCode)
        // No Vision tag: the iOS engine will drop it rather than fail.
        assertEquals(null, polish.bcp47)
        assertEquals(polish, OcrLanguage.custom("pol", bcp47 = "pl-PL"))
        assertFailsWith<IllegalArgumentException> { OcrLanguage.custom("  ") }
    }

    @Test
    fun `a code that could escape a cache directory or a download URL is rejected`() {
        // The code becomes both a filename and a URL path segment, so a downloading
        // provider handed one of these would write network-fetched bytes wherever the
        // traversal points.
        assertFailsWith<IllegalArgumentException> { OcrLanguage.custom("../../etc/passwd") }
        assertFailsWith<IllegalArgumentException> { OcrLanguage.custom("ukr/../eng") }
        // The extension is the provider's to add; accepting it here would look for
        // `eng.traineddata.traineddata`.
        assertFailsWith<IllegalArgumentException> { OcrLanguage.custom("eng.traineddata") }
    }

    @Test
    fun `real Tesseract basenames survive the path check`() {
        // The other half of the rule above, and the half a tightening breaks silently:
        // every one of these ships in tessdata, so a caller passing one is doing exactly
        // what custom() is for. Underscores and capitals both have to stay legal.
        val shipped = listOf(
            "chi_sim",
            "chi_tra_vert",
            "aze_cyrl",
            "srp_latn",
            "Cyrillic",
            "Canadian_Aboriginal",
        )
        shipped.forEach { code ->
            assertEquals(code, OcrLanguage.custom(code).tesseractCode, "rejected '$code'")
        }
    }
}
