package io.github.andrewmalitchuk.uncial.core.source.raster

import android.graphics.Bitmap

/**
 * Android raster, backed by a [Bitmap].
 *
 * Construct one directly to recognize an image you already have — a camera frame, a
 * gallery pick, a screenshot — without going through a PDF.
 */
public actual class Raster internal constructor(
    private val backing: Bitmap?,
    public actual val width: Int,
    public actual val height: Int,
    public actual val sourceDpi: Int? = null,
) {
    /**
     * Wraps a bitmap. Not copied: this does not take ownership until [release] is called.
     *
     * @param sourceDpi the resolution [bitmap] was rendered at, when it is known.
     */
    public constructor(bitmap: Bitmap, sourceDpi: Int? = null) :
        this(bitmap, bitmap.width, bitmap.height, sourceDpi)

    /**
     * The backing bitmap.
     *
     * @throws IllegalStateException for a [placeholderRaster], which has no pixels.
     */
    public val bitmap: Bitmap
        get() = checkNotNull(backing) {
            "this Raster is a placeholder and has no Bitmap; it can only be used with a " +
                "fake recognizer that ignores pixels"
        }

    /**
     * Recycles the backing bitmap.
     *
     * Android is the one platform where this matters: a 200 dpi A4 page is ~5 MB of heap,
     * and the GC is not fast enough to keep up with a 300-page document on its own.
     */
    public actual fun release() {
        backing?.takeIf { !it.isRecycled }?.recycle()
    }
}
