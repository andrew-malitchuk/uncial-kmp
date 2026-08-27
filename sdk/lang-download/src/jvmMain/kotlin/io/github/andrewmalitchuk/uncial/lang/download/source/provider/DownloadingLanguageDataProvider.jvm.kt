package io.github.andrewmalitchuk.uncial.lang.download.source.provider

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.lang.download.core.provider.JvmDownloadingLanguageDataProvider
import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource
import java.io.File

public actual fun downloadingLanguageDataProvider(
    source: TessDataSource,
    cacheDirectoryPath: String?,
    logger: OcrLogger,
): DownloadingLanguageDataProvider = JvmDownloadingLanguageDataProvider(
    source = source,
    cacheDirectory = cacheDirectoryPath?.let(::File) ?: defaultCacheDirectory(),
    logger = logger,
)

/**
 * A per-user directory under the system temp dir.
 *
 * Per-user rather than shared, because a model downloaded by one account should not be
 * writable by another; and under temp rather than the working directory so that running
 * from a read-only checkout still works.
 */
private fun defaultCacheDirectory(): File {
    val base = System.getProperty("java.io.tmpdir") ?: "."
    val user = System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "shared"
    return File(base, "uncial-tessdata-$user")
}
