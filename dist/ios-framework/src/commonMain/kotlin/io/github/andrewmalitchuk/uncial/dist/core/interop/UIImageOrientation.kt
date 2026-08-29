package io.github.andrewmalitchuk.uncial.dist.core.interop

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation

/**
 * Redraws the image so its pixels are the right way up.
 *
 * A camera does not rotate what it captured: it writes the sensor's pixels and records how
 * the phone was held, which `UIImage` carries as `imageOrientation` and `CGImage` does not
 * carry at all. `VNImageRequestHandler` is handed the `CGImage`, so a portrait photo of a
 * page reaches Vision on its side and comes back as nothing.
 *
 * Drawing into a context is what bakes the orientation into the pixels. It costs a full
 * copy of the image, which is why the upright case returns early.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UIImage.upright(): UIImage {
    if (imageOrientation == UIImageOrientation.UIImageOrientationUp) return this
    // scale = 0.0 would adopt the screen's scale and silently resample; the image's own
    // scale keeps the pixels one-to-one.
    UIGraphicsBeginImageContextWithOptions(size, false, scale)
    try {
        size.useContents { drawInRect(CGRectMake(0.0, 0.0, width, height)) }
        return UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
        UIGraphicsEndImageContext()
    }
}
