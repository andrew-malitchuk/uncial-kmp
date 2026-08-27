package io.github.andrewmalitchuk.uncial.lang.download.core.verification

import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TessDataSourceTest {

    @Test
    fun `the default source points at the same models the lang artifacts embed`() {
        val source = TessDataSource()
        assertEquals(
            "https://github.com/tesseract-ocr/tessdata_fast/raw/main/ukr.traineddata",
            source.urlFor(OcrLanguage.Ukrainian),
        )
        assertEquals(PINNED, source.checksumFor(OcrLanguage.Ukrainian))
    }

    @Test
    fun `plain http is refused`() {
        // A .traineddata is executable input to a native library; downgrading the transport
        // is not a configuration choice.
        assertFailsWith<IllegalArgumentException> {
            TessDataSource(baseUrl = "http://example.com/tessdata/")
        }
    }

    @Test
    fun `a base url without a trailing slash is refused`() {
        assertFailsWith<IllegalArgumentException> {
            TessDataSource(baseUrl = "https://example.com/tessdata")
        }
    }

    @Test
    fun `an unknown language simply has no pinned checksum`() {
        assertEquals(null, TessDataSource().checksumFor(OcrLanguage.custom("pol")))
    }
}
