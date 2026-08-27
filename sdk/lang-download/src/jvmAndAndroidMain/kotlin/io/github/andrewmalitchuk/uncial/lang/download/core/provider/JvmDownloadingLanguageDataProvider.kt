package io.github.andrewmalitchuk.uncial.lang.download.core.provider

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.log.info
import io.github.andrewmalitchuk.uncial.core.source.log.warn
import io.github.andrewmalitchuk.uncial.lang.download.core.verification.DownloadVerification
import io.github.andrewmalitchuk.uncial.lang.download.source.provider.DownloadingLanguageDataProvider
import io.github.andrewmalitchuk.uncial.lang.download.source.tessdata.TessDataSource
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The download provider for both JVM targets.
 *
 * Uses `HttpURLConnection`, which exists on Android and the JVM alike, so the module needs
 * no HTTP dependency — and therefore cannot conflict with whatever client the consumer
 * already has.
 */
internal class JvmDownloadingLanguageDataProvider(
    private val source: TessDataSource,
    private val cacheDirectory: File,
    private val logger: OcrLogger,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    // The only place this class touches the network, isolated behind a lambda so tests can
    // drive the awkward paths -- an HTTP error, an oversized body, a lying Content-Length --
    // without a server and without loosening TessDataSource's https requirement.
    private val openConnection: (String) -> HttpURLConnection = ::openHttpConnection,
) : DownloadingLanguageDataProvider {

    // Two extractions starting at once must not download the same model twice, nor race on
    // the same output file.
    private val lock = Mutex()

    override fun isCached(language: OcrLanguage): Boolean = language.cachedFile().isUsable()

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        // Everything is "available": whether it is on disk yet only changes how long
        // materialize takes. Reporting only cached languages would make the runtime skip
        // languages it could perfectly well fetch.
        requested

    override suspend fun materialize(languages: List<OcrLanguage>): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val tessDataDirectory = File(cacheDirectory, TESSDATA_DIRECTORY)
                if (!tessDataDirectory.isDirectory && !tessDataDirectory.mkdirs()) {
                    throw OcrError.NoLanguageData(languages, cacheDirectory.absolutePath)
                }
                // One language failing must not abort the rest: the contract is to throw
                // only when NONE of them can be materialized, and a document asked for in
                // ukr+eng is still worth recognizing in ukr alone. The last failure is kept
                // so the error that does get thrown still says why.
                var lastFailure: Throwable? = null
                val obtained = languages.filter { language ->
                    currentCoroutineContext().ensureActive()
                    try {
                        ensureDownloaded(language, tessDataDirectory)
                    } catch (cancellation: CancellationException) {
                        // Not a language failure. Recording it would report NoLanguageData
                        // while the caller sits believing its cancellation took effect.
                        throw cancellation
                    } catch (cause: Throwable) {
                        logger.warn("download of ${language.tesseractCode} failed", cause)
                        lastFailure = cause
                        false
                    }
                }
                if (obtained.isEmpty()) {
                    throw OcrError.NoLanguageData(languages, source.baseUrl, lastFailure)
                }
                // Tesseract's datapath convention: the PARENT of tessdata/.
                cacheDirectory.absolutePath
            }
        }

    override suspend fun prefetch(languages: List<OcrLanguage>): List<OcrLanguage> =
        withContext(Dispatchers.IO) {
            // The same lock the other two entry points take. Without it a prefetch and a
            // materialize of one language fetch the same 4 MB twice, and a concurrent
            // clearCache can unlink the .part file this call is mid-write on -- after
            // which the rename fails, the copy throws, and the install's cleanup deletes
            // a cached model this call never meant to touch.
            lock.withLock {
                val tessDataDirectory = File(cacheDirectory, TESSDATA_DIRECTORY)
                tessDataDirectory.mkdirs()
                languages.filter { language ->
                    currentCoroutineContext().ensureActive()
                    // Best-effort by definition: a failed pre-warm must not break the
                    // caller, it just means the first extraction pays for the download.
                    try {
                        ensureDownloaded(language, tessDataDirectory)
                    } catch (cancellation: CancellationException) {
                        // Not a language failure. Swallowing it would leave the caller
                        // believing its cancellation took effect.
                        throw cancellation
                    } catch (cause: Throwable) {
                        // Throwable rather than OcrError: ensureDownloaded's
                        // path-traversal check throws IllegalStateException, and this
                        // method promises that failures are logged rather than thrown.
                        logger.warn("prefetch of ${language.tesseractCode} failed", cause)
                        false
                    }
                }
            }
        }

    override suspend fun clearCache(): Int = withContext(Dispatchers.IO) {
        // Under the same lock as the download path: without it this can unlink a model
        // between materialize verifying it and Tesseract opening it.
        lock.withLock {
            val tessDataDirectory = File(cacheDirectory, TESSDATA_DIRECTORY)
            val files = tessDataDirectory.listFiles { file ->
                // .part files too -- a killed download leaves them behind, and a cache
                // clear that keeps several MB of rubbish is not one.
                file.name.endsWith(TRAINEDDATA) || file.name.endsWith(TRAINEDDATA + PARTIAL)
            }
            files?.count { it.delete() } ?: 0
        }
    }

    /** @return `true` when the model is on disk afterwards. */
    private fun ensureDownloaded(language: OcrLanguage, tessDataDirectory: File): Boolean {
        val target = File(tessDataDirectory, language.fileName())
        // OcrLanguage.custom restricts the code to [A-Za-z0-9_-], so this cannot trigger
        // today -- it is here so that a future loosening of that rule cannot silently turn
        // into an arbitrary file write.
        val root = tessDataDirectory.canonicalPath + File.separator
        check(target.canonicalPath.startsWith(root)) {
            "refusing to write ${language.tesseractCode}.traineddata outside $root"
        }
        if (target.isUsable()) {
            logger.debug("${language.tesseractCode}.traineddata already cached")
            return true
        }

        val url = source.urlFor(language)
        logger.info("downloading ${language.tesseractCode}.traineddata from $url")
        val bytes = fetch(url, language)

        DownloadVerification.verify(
            language = language,
            bytes = bytes,
            expected = source.checksumFor(language),
            actual = { bytes.sha256() },
            logger = logger,
        )

        // Write to a temp file and rename, so a killed process cannot leave a truncated
        // model that a later run treats as complete. A half-written model does not fail
        // loudly -- it just recognizes badly.
        val partial = File(tessDataDirectory, language.fileName() + PARTIAL)
        return try {
            partial.writeBytes(bytes)
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            true
        } catch (cause: Throwable) {
            partial.delete()
            // The destination goes too. The copyTo fallback is not atomic, so a failure
            // part-way through it leaves a truncated target -- and isUsable(), which only
            // asks "a file, not empty?", would hand that to Tesseract on the next run.
            target.delete()
            throw OcrError.NoLanguageData(listOf(language), target.absolutePath, cause)
        }
    }

    private fun fetch(url: String, language: OcrLanguage): ByteArray {
        val connection = openConnection(url)
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            // GitHub serves these through a redirect to its CDN.
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw OcrError.NoLanguageData(
                    languages = listOf(language),
                    searchedPath = url,
                    cause = IllegalStateException("HTTP $status"),
                )
            }
            // Redirects are followed blindly (GitHub sends us on to its CDN) and the
            // checksum can only be computed once the whole body is in memory, so an
            // oversized response would exhaust the heap before anything could reject it.
            // Content-Length is a claim rather than a fact, so the cap is enforced again
            // while reading.
            val advertised = connection.contentLengthLong
            if (advertised > maxBytes) throw tooLarge(language, url, advertised)
            connection.inputStream.use { stream -> stream.readAtMost(language, url) }
        } catch (cause: OcrError) {
            throw cause
        } catch (cause: Throwable) {
            throw OcrError.NoLanguageData(listOf(language), url, cause)
        } finally {
            connection.disconnect()
        }
    }

    /** Reads the whole body, refusing to grow past [maxBytes]. */
    private fun InputStream.readAtMost(language: OcrLanguage, url: String): ByteArray {
        val body = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            if (body.size().toLong() + read > maxBytes) throw tooLarge(language, url, null)
            body.write(chunk, 0, read)
        }
        return body.toByteArray()
    }

    private fun tooLarge(language: OcrLanguage, url: String, advertised: Long?) =
        OcrError.NoLanguageData(
            languages = listOf(language),
            searchedPath = url,
            cause = IllegalStateException(
                "response exceeds the $maxBytes byte limit" +
                    (advertised?.let { " (Content-Length $it)" } ?: "") +
                    ": a .traineddata is around 4 MB, so this is not one",
            ),
        )

    private fun OcrLanguage.cachedFile(): File =
        File(File(cacheDirectory, TESSDATA_DIRECTORY), fileName())

    private fun OcrLanguage.fileName(): String = "$tesseractCode$TRAINEDDATA"

    private fun File.isUsable(): Boolean = isFile && length() > 0L

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    private companion object {
        const val TESSDATA_DIRECTORY = "tessdata"
        const val TRAINEDDATA = ".traineddata"
        const val PARTIAL = ".part"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val READ_CHUNK_BYTES = 64 * 1024
    }
}

/**
 * The ceiling on a single download.
 *
 * The models are 3.8-4.1 MB and the largest thing in `tessdata` proper is 23 MB, so 64 MB
 * is generous while still being far below "enough to kill the app". It exists because the
 * bytes are buffered in memory before they can be verified: there is no point at which a
 * runaway response could be noticed otherwise.
 */
private const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024

private fun openHttpConnection(url: String): HttpURLConnection =
    URI(url).toURL().openConnection() as HttpURLConnection
