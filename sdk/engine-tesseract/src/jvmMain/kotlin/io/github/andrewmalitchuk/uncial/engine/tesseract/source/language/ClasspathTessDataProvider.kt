package io.github.andrewmalitchuk.uncial.engine.tesseract.source.language

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.language.fileName
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts `.traineddata` from the classpath onto disk.
 *
 * This is how the `uncial-lang-*` artifacts work on the JVM: they ship the model as a
 * resource under `tessdata/`, and Tesseract cannot read a resource — it needs a real
 * directory. So the model is unpacked once into a cache directory and reused.
 *
 * Pair it with [SystemTessDataProvider] via [plus] to get "use what the OS already has,
 * fall back to what we ship":
 *
 * ```
 * SystemTessDataProvider() + ClasspathTessDataProvider()
 * ```
 *
 * @param cacheDirectory where models are unpacked. Defaults to
 *   `~/.cache/uncial/tessdata-<user>`, so repeated runs of the same application do not
 *   re-extract. Building that default throws [OcrError.EngineInit] if the directory turns
 *   out to belong to another user.
 */
public class ClasspathTessDataProvider(
    private val cacheDirectory: File = defaultCacheDirectory(),
    private val classLoader: ClassLoader = ClasspathTessDataProvider::class.java.classLoader,
) : LanguageDataProvider {

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        withContext(Dispatchers.IO) {
            requested.filter { classLoader.getResource(it.resourcePath()) != null }
        }

    override suspend fun materialize(languages: List<OcrLanguage>): String =
        withContext(Dispatchers.IO) {
            val tessDataDir = File(cacheDirectory, TESSDATA_DIR)
            if (!tessDataDir.isDirectory && !tessDataDir.mkdirs()) {
                throw OcrError.NoLanguageData(languages, cacheDirectory.absolutePath)
            }
            val extracted = languages.filter { extractIfNeeded(it, tessDataDir) }
            if (extracted.isEmpty()) {
                throw OcrError.NoLanguageData(
                    languages = languages,
                    searchedPath = "classpath:$TESSDATA_DIR/",
                )
            }
            cacheDirectory.absolutePath
        }

    private fun extractIfNeeded(language: OcrLanguage, tessDataDir: File): Boolean {
        val target = File(tessDataDir, language.fileName())
        val resource = classLoader.getResource(language.resourcePath()) ?: return target.isFile
        // Compare sizes rather than timestamps: an upgraded artifact ships a different
        // model under the same name, and a stale cached copy would silently keep winning.
        val expectedSize = resource.openConnection().contentLengthLong
        if (target.isFile && (expectedSize <= 0L || target.length() == expectedSize)) return true

        // Extract to a sibling temp file and move it into place, so a crashed or
        // concurrent run cannot leave a half-written model that looks complete.
        val temp = Files.createTempFile(tessDataDir.toPath(), language.tesseractCode, ".part")
        return try {
            classLoader.getResourceAsStream(language.resourcePath())?.use { input ->
                Files.newOutputStream(temp).use { output -> input.copyTo(output) }
            } ?: return false
            Files.move(
                temp,
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (cause: Throwable) {
            Files.deleteIfExists(temp)
            throw OcrError.NoLanguageData(listOf(language), target.absolutePath, cause)
        }
    }

    private fun OcrLanguage.resourcePath(): String = "$TESSDATA_DIR/${fileName()}"

    public companion object {
        private const val TESSDATA_DIR = "tessdata"

        /**
         * Where models are unpacked when the caller does not say.
         *
         * Under the user's home directory rather than the shared system temp: the old
         * `/tmp/uncial-tessdata-$user` path was predictable and world-writable, so on a
         * multi-user host another account could pre-create it and plant a `.traineddata`
         * that libtesseract would then parse in native code. The name is also sanitized,
         * because `user.name` can be `DOMAIN\user` on Windows and can be non-ASCII, and
         * it eventually crosses JNA's `dataPath` boundary.
         */
        private fun defaultCacheDirectory(): File {
            val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            val user = System.getProperty("user.name")
                ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                ?.takeIf { it.isNotBlank() }
                ?: "shared"
            return File(home, ".cache/uncial/tessdata-$user").also { it.restrictToOwner() }
        }

        /**
         * Makes [this] a directory only its owner can enter, or refuses to use it.
         *
         * The permissions are handed to `createDirectories` instead of being applied
         * afterwards, for two reasons. A `mkdirs` followed by `setPosixFilePermissions`
         * leaves a window in which the directory exists with the default mode, which is
         * long enough for another account to drop a file in it. And on a directory that
         * *already* existed, `setPosixFilePermissions` throws `IOException` when someone
         * else owns it — which is exactly the case worth refusing, since what lands here
         * is then parsed by libtesseract in native code. The home directory makes that
         * unreachable in practice, but [defaultCacheDirectory] falls back to
         * `java.io.tmpdir`, which puts a pre-creatable `/tmp/.cache/uncial/...` back on
         * the table.
         *
         * Only directories this call creates get the mode; a pre-existing one is left as
         * it is and merely checked for ownership.
         *
         * @throws OcrError.EngineInit when the directory exists and belongs to another
         *   user, or cannot be created at all.
         */
        private fun File.restrictToOwner() {
            val path = toPath()
            try {
                Files.createDirectories(path, ownerOnly())
            } catch (_: UnsupportedOperationException) {
                // Windows: no POSIX permissions, and no owner worth comparing either.
                // The home directory is already per-user there.
                mkdirs()
                return
            } catch (cause: java.io.IOException) {
                throw OcrError.EngineInit("cannot create the tessdata cache $this", cause)
            }
            // Only a positive mismatch is fatal. An owner or a user name we cannot read
            // is not evidence of an attack, and refusing on it would break hosts whose
            // file system has no notion of ownership at all.
            val owner = try {
                // NOFOLLOW_LINKS: a symlink is a directory as far as createDirectories is
                // concerned, and following it would report the owner of wherever it
                // points rather than of the thing someone planted in our path.
                Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).name
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: java.io.IOException) {
                return
            }
            val us = System.getProperty("user.name")
            if (us != null && owner != us) {
                throw OcrError.EngineInit(
                    "refusing the tessdata cache $this: it belongs to $owner, not $us, " +
                        "so its contents are not ours to hand to libtesseract",
                )
            }
        }

        private fun ownerOnly(): FileAttribute<Set<PosixFilePermission>> =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    }
}
