package io.github.andrewmalitchuk.uncial.model.source.text

import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfidenceTest {

    @Test
    fun `an unknown confidence is distinguishable and does not pretend to be zero`() {
        assertFalse(Confidence.Unknown.isKnown)
        assertTrue(Confidence.Unknown.value.isNaN())
        assertEquals(0.5f, Confidence.Unknown.orElse(0.5f))
    }

    @Test
    fun `normalized values are clamped`() {
        assertEquals(1f, Confidence.of(1.4f).value)
        assertEquals(0f, Confidence.of(-0.2f).value)
        assertEquals(0.75f, Confidence.of(0.75f).value)
    }

    @Test
    fun `negative zero is folded onto zero so equal confidences compare equal`() {
        // coerceIn lets -0.0 through, and equality on a value class over Float is total
        // order -- the same rule that makes Unknown == Unknown -- so an engine reporting
        // -0.0 used to produce a Confidence that did not equal Confidence.of(0f).
        assertEquals(Confidence.of(0f), Confidence.of(-0f))
        assertEquals(Confidence.of(0f).hashCode(), Confidence.of(-0f).hashCode())
        assertEquals(Confidence.of(0f), Confidence.ofPercent(-0f))
        // Positive zero, not the negative one it was handed.
        assertTrue(1f / Confidence.of(-0f).value > 0f)
    }

    @Test
    fun `a Tesseract percentage converts to the normalized scale`() {
        assertEquals(0.87f, Confidence.ofPercent(87f).value)
        assertEquals(1f, Confidence.Certain.value)
    }

    @Test
    fun `a missing engine value cannot masquerade as a real one`() {
        assertFalse(Confidence.of(Float.NaN).isKnown)
        assertFalse(Confidence.ofPercent(Float.NaN).isKnown)
        // Tesseract reports -1 for "no estimate".
        assertFalse(Confidence.ofPercent(-1f).isKnown)
    }

    @Test
    fun `unknown sorts below every known value so aggregates stay meaningful`() {
        val values = listOf(Confidence.of(0.4f), Confidence.Unknown, Confidence.of(0.9f))
        assertEquals(Confidence.Unknown, values.min())
        assertEquals(0.9f, values.max().value)
    }
}
