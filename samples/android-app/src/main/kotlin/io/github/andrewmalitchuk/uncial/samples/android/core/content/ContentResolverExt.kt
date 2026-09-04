package io.github.andrewmalitchuk.uncial.samples.android.core.content

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UncialSample"

/**
 * A human name for a picked URI, for the progress and document screens.
 *
 * The last path segment is not it. Android's photo picker hands back
 * `content://media/picker/0/…/media/19`, whose last segment is the media id — so a
 * recognized photo would be titled "19". `OpenableColumns.DISPLAY_NAME` is the name the
 * provider actually has, and the segment stays as the fallback for providers that expose
 * no columns at all.
 *
 * Blocking: a cursor query is I/O, which is why this moves itself off the main thread.
 */
internal suspend fun Context.displayName(uri: Uri): String = withContext(Dispatchers.IO) {
    // A plain try/catch, not `runCatching`: this runs inside the extraction coroutine, and
    // runCatching swallows CancellationException -- the trap the SDK has its own
    // runCatchingOcr for. Nothing inside the block suspends, so catching Exception here
    // cannot hide a cancellation; the narrow type is what makes that true.
    val queried = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
    } catch (cause: Exception) {
        Log.w(TAG, "no display name for $uri", cause)
        null
    }
    queried ?: uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "document"
}
