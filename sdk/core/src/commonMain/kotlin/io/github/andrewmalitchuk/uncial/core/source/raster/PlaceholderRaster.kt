package io.github.andrewmalitchuk.uncial.core.source.raster

/**
 * A raster that has a size but **no pixels**.
 *
 * Recognizing one is meaningless, and reading its platform image throws. It exists for one
 * reason: a fake pipeline needs something of the right shape to pass around, and on
 * Android the real thing cannot be created at all in a host unit test — `Bitmap` is a stub
 * in `android.jar`, so `Bitmap.createBitmap` throws `not mocked`.
 *
 * Without this, every consumer who unit-tests their own code against `FakeOcrEngine` on
 * Android would hit that wall, which would defeat the purpose of shipping a fake engine.
 * `FakePageRasterizer` therefore hands out placeholders; use
 * `blankRaster` from `uncial-engine-fake` instead when you are testing a real
 * `TextRecognizer` and the pixels actually matter.
 */
public expect fun placeholderRaster(width: Int, height: Int): Raster
