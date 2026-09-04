package io.github.andrewmalitchuk.uncial.samples.android.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.InputStream

// Turning a photo into something Uncial can recognize.
//
// This is the half of image input the SDK deliberately does NOT do. UncialClient takes a
// Raster, which on Android is a Bitmap; getting from "the user pointed a camera at a page"
// to a bitmap worth recognizing is the app's job, and it is not nothing -- orientation and
// size both matter.

/**
 * The longest side a decoded photo is allowed to have.
 *
 * `OcrOptions.maxPageSide` does not apply here: it guards the *rasterizers*, and a raster
 * the caller built never goes through one. So the cap has to be applied at decode time, or
 * a 50 MP phone photo becomes ~200 MB of ARGB_8888 and Tesseract spends a minute on pixels
 * that carry no extra letters.
 *
 * 2400 px on the long side is roughly 200 dpi across an A4 page — the same figure this
 * sample asks for when it rasterizes a PDF.
 */
private const val MAX_IMAGE_SIDE = 2400
/**
 * Decodes a photo into a bitmap sized and oriented for recognition.
 *
 * Two things a naive `BitmapFactory.decodeStream` gets wrong:
 *
 * - **Size.** See [MAX_IMAGE_SIDE].
 * - **Orientation.** A camera writes the sensor's pixels and records how the phone was
 *   held in an EXIF tag. Gallery apps honour that tag; `BitmapFactory` does not, and
 *   neither does Tesseract — so a portrait photo recognizes as a sideways page, which with
 *   `PSM_AUTO` mostly means nothing at all.
 *
 * Runs blocking I/O and allocates tens of megabytes: call it off the main thread.
 */
internal fun decodeForOcr(context: Context, uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.read(uri) { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a decodable image: $uri" }

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        // Tesseract4Android's setImage(Bitmap) wants 8888; RGB_565 would halve the memory
        // and then fail at the native boundary.
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.read(uri) { BitmapFactory.decodeStream(it, null, options) }
        ?: error("could not decode $uri")

    // A third pass over the stream rather than reading EXIF from the decoded bitmap, which
    // no longer carries it. Streams from a content provider are not reliably rewindable,
    // so each pass reopens.
    val rotation = context.read(uri) { rotationDegrees(it) }
    return decoded.rotate(rotation)
}

/**
 * Reads [uri] through the content resolver, failing loudly if it cannot be opened.
 *
 * The null check is on the *stream*, deliberately. Writing this as
 * `openInputStream(uri)?.use(block) ?: error(...)` reads the same and is wrong: `block`
 * here legitimately returns null -- `BitmapFactory.decodeStream` with `inJustDecodeBounds`
 * always does, since it only fills in the bounds -- so the elvis would fire on a stream
 * that opened perfectly well and blame the file for it.
 */
private fun <T> Context.read(uri: Uri, block: (InputStream) -> T): T {
    val stream = contentResolver.openInputStream(uri) ?: error("could not open $uri")
    return stream.use(block)
}

/**
 * The power-of-two subsampling factor that brings the long side under [MAX_IMAGE_SIDE].
 *
 * `inSampleSize` is the cheap way to downscale: the decoder never materializes the full
 * image, so peak memory stays at the *result's* size rather than the original's. It only
 * honours powers of two, which is why this doubles rather than dividing.
 */
private fun sampleSizeFor(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > MAX_IMAGE_SIDE) sample *= 2
    return sample
}

/**
 * The rotation EXIF says to apply, in degrees clockwise.
 *
 * `android.media.ExifInterface` rather than the AndroidX one: its stream constructor is API
 * 24, which is this project's `minSdk`, so the sample keeps one fewer dependency. The four
 * mirrored orientations (`TRANSPOSE`, `FLIP_*`) are treated as their unmirrored
 * counterparts — a mirrored photo of a page is a photo of a mirror, and no OCR engine will
 * read it anyway.
 */
private fun rotationDegrees(stream: InputStream): Int =
    when (
        ExifInterface(stream)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

/** Rotates, recycling the original: the two together are twice the peak memory. */
private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}
