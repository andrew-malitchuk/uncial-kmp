package io.github.andrewmalitchuk.uncial.model.source.geometry

import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals

class BoundingBoxTest {

    @Test
    fun `edges follow the top-down convention`() {
        val box = BoundingBox(left = 10f, top = 20f, width = 100f, height = 30f)
        assertEquals(110f, box.right)
        // Bottom is GREATER than top: y grows downwards.
        assertEquals(50f, box.bottom)
        assertEquals(60f, box.center.x)
        assertEquals(3000f, box.area)
    }

    @Test
    fun `union covers both boxes`() {
        val union = BoundingBox(0f, 0f, 10f, 10f).union(BoundingBox(20f, 30f, 10f, 10f))
        assertEquals(BoundingBox(0f, 0f, 30f, 40f), union)
    }
}
