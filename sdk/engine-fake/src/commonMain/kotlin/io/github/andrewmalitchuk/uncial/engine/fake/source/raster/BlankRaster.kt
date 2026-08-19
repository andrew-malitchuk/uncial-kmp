package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import io.github.andrewmalitchuk.uncial.core.source.raster.Raster

/**
 * Creates a blank white raster of the given size, with **real pixels**.
 *
 * Use this when testing a real `TextRecognizer`, which needs something to look at. For
 * driving `UncialClient` against `FakeOcrEngine` prefer `FakePageRasterizer`, which uses
 * pixel-free placeholders and therefore also works in Android host unit tests.
 */
public expect fun blankRaster(width: Int, height: Int): Raster
