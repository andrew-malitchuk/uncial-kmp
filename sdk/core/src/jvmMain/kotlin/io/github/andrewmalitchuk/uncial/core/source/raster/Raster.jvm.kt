package io.github.andrewmalitchuk.uncial.core.source.raster

import java.awt.image.BufferedImage

/**
 * JVM raster, backed by a [BufferedImage].
 *
 * Construct one directly to recognize an image loaded with `ImageIO` instead of a PDF.
 */
public actual class Raster internal constructor(
    private val backing: BufferedImage?,
    public actual val width: Int,
    public actual val height: Int,
    public actual val sourceDpi: Int? = null,
) {
    /**
     * Wraps an image.
     *
     * @param sourceDpi the resolution [image] was rendered at, when it is known. Tesseract
     *   uses it as a layout hint; leaving it `null` makes the engine estimate instead.
     */
    public constructor(image: BufferedImage, sourceDpi: Int? = null) :
        this(image, image.width, image.height, sourceDpi)

    /**
     * The backing image.
     *
     * @throws IllegalStateException for a [placeholderRaster], which has no pixels.
     */
    public val image: BufferedImage
        get() = checkNotNull(backing) {
            "this Raster is a placeholder and has no BufferedImage; it can only be used " +
                "with a fake recognizer that ignores pixels"
        }

    /** No-op: the JVM's GC reclaims a [BufferedImage] like any other object. */
    public actual fun release() {
        // Nothing to do.
    }
}
