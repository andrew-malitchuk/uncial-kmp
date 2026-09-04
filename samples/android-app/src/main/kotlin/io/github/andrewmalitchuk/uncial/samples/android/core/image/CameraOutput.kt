package io.github.andrewmalitchuk.uncial.samples.android.core.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Where `ActivityResultContracts.TakePicture` is told to write. */
private const val CAMERA_DIR = "camera"

/**
 * Allocates a file for the camera app to write a full-resolution photo into.
 *
 * `TakePicture` needs a destination: the thumbnail-returning `TakePicturePreview` is
 * useless for OCR. The destination has to be a content URI another app may write to, which
 * is what the `FileProvider` in the manifest is for — a `file://` URI would be a
 * `FileUriExposedException` on anything since Nougat.
 *
 * Each call leaves a JPEG in the cache. A real app would clean up after itself; a sample
 * leaves it to the system, which reclaims the cache directory under pressure.
 */
internal fun newCameraOutput(context: Context): Uri {
    val directory = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
    val file = File.createTempFile("photo-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
