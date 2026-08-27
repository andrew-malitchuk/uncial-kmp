package io.github.andrewmalitchuk.uncial.lang.download.source.provider

import io.github.andrewmalitchuk.uncial.core.source.android.UncialContext
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.lang.download.core.provider.JvmDownloadingLanguageDataProvider
import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
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
 * `filesDir/uncial-tessdata`.
 *
 * Deliberately `filesDir` and not `cacheDir`: Android deletes cache directories under
 * storage pressure, and re-downloading several MB because the OS reclaimed 4 MB is worse
 * than keeping it. Consumers who prefer eviction can pass `cacheDir.path` explicitly.
 */
private fun defaultCacheDirectory(): File {
    val context = UncialContext.get() ?: throw OcrError.EngineInit(
        "no Android Context: androidx.startup did not run, so the download cache location " +
            "is unknown. Pass cacheDirectoryPath explicitly, or call " +
            "UncialContext.install(applicationContext).",
    )
    return File(context.filesDir, "uncial-tessdata")
}
