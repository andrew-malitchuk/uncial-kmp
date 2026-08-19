package io.github.andrewmalitchuk.uncial.engine.fake.source.raster

import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetGrayFillColor
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease

@OptIn(ExperimentalForeignApi::class)
public actual fun blankRaster(width: Int, height: Int): Raster {
    val colorSpace = CGColorSpaceCreateDeviceGray()
    val context = CGBitmapContextCreate(
        data = null,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8u,
        bytesPerRow = 0u,
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value,
    )
    try {
        checkNotNull(context) { "could not create a ${width}x$height bitmap context" }
        CGContextSetGrayFillColor(context, 1.0, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        val image = checkNotNull(CGBitmapContextCreateImage(context)) {
            "could not create an image from the bitmap context"
        }
        // CGBitmapContextCreateImage returns a +1 reference and Raster takes its own,
        // so this one is handed back straight away; the raster's release() owns it now.
        return try {
            Raster(image)
        } finally {
            CGImageRelease(image)
        }
    } finally {
        context?.let { CGContextRelease(it) }
        CGColorSpaceRelease(colorSpace)
    }
}
