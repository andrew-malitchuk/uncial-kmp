package io.github.andrewmalitchuk.uncial.lang.download.source.provider

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource

/**
 * Creates the platform's [DownloadingLanguageDataProvider].
 *
 * @param source where to fetch from and what to verify against.
 * @param cacheDirectoryPath where models are cached. `null` uses a per-application default:
 *   `filesDir/uncial-tessdata` on Android, a directory under the system temp dir on the JVM.
 * @param logger receives download and verification diagnostics. Strongly recommended: this
 *   is the one part of the SDK that touches the network.
 */
public expect fun downloadingLanguageDataProvider(
    source: TessDataSource = TessDataSource(),
    cacheDirectoryPath: String? = null,
    logger: OcrLogger = OcrLogger.None,
): DownloadingLanguageDataProvider
