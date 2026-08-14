package io.github.andrewmalitchuk.uncial.core.source.raster

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGImageRetain

/**
 * iOS raster, backed by a `CGImage`.
 *
 * Construct one from `uiImage.CGImage` to recognize an image you already have.
 */
@OptIn(ExperimentalForeignApi::class)
public actual class Raster internal constructor(
    private var backing: CGImageRef?,
    public actual val width: Int,
    public actual val height: Int,
    public actual val sourceDpi: Int? = null,
) {
    init {
        // `CGImageRef` is a plain `CPointer` in Kotlin/Native, not an Obj-C object, so
        // nothing here is under ARC: holding one in a Kotlin field neither retains it nor
        // keeps its owner alive. Rasterizers build one from a `UIImage` that goes out of
        // scope immediately afterwards, so without this retain the image can be freed
        // while a recognizer is still reading it.
        backing?.let { CGImageRetain(it) }
    }

    /**
     * Wraps a Core Graphics image, retaining it for the lifetime of this raster.
     *
     * @param sourceDpi the resolution [image] was rendered at, when it is known.
     */
    public constructor(image: CGImageRef, sourceDpi: Int? = null) : this(
        backing = image,
        width = CGImageGetWidth(image).toInt(),
        height = CGImageGetHeight(image).toInt(),
        sourceDpi = sourceDpi,
    )

    /**
     * The backing Core Graphics image.
     *
     * @throws IllegalStateException for a [placeholderRaster], which has no pixels.
     */
    public val image: CGImageRef
        get() = checkNotNull(backing) {
            "this Raster has no CGImage: it is either a placeholder, which can only be " +
                "used with a fake recognizer that ignores pixels, or it has already been " +
                "released"
        }

    /**
     * Releases the retained `CGImage`.
     *
     * Idempotent, and a no-op for a [placeholderRaster], which never had one. After this
     * the raster has no pixels and [image] throws.
     */
    public actual fun release() {
        backing?.let {
            backing = null
            CGImageRelease(it)
        }
    }
}
