package io.github.andrewmalitchuk.uncial.structure.core.fixture

/**
 * Builders that lay out plausible page geometry.
 *
 * The structure heuristics read boxes and type sizes, so a fixture that returned zeroed
 * geometry would make every test pass for the wrong reason. These place lines top to
 * bottom at a given size, with a controllable gap.
 */
internal const val BODY_SIZE = 12f
