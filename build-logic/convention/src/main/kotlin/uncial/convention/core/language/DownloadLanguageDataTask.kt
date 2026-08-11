package uncial.convention.core.language

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.MessageDigest

/**
 * Fetches a Tesseract language model into the generated asset and resource directories.
 *
 * The models are several MB of binary and are deliberately not committed: they are build
 * inputs, downloaded reproducibly and verified by SHA-256 so a corrupted or substituted
 * model fails the build rather than degrading recognition silently.
 *
 * Output goes under `build/`, not into `src/`, so the directories can be wired into the
 * Android variant as *generated* sources — which is what makes Gradle order the download
 * before packaging instead of silently shipping an AAR with no assets in it.
 *
 * Note on sizes: `tessdata_fast` is what yields the ~3.8 MB (ukr) and ~4.1 MB (eng)
 * figures. The full `tessdata` models for these languages are 12 MB and 23 MB, and there
 * is no smaller variant below `tessdata_fast` to fall back to.
 *
 * Cacheable: the task is a pure function of the language code, the source URL and the
 * expected digest -- all `@Input`s, no file inputs -- so a clean CI checkout can restore
 * the models from the build cache instead of pulling several MB per language off GitHub
 * on every run.
 */
@CacheableTask
internal abstract class DownloadLanguageDataTask : DefaultTask() {

    /** Tesseract language code, i.e. the `.traineddata` basename. */
    @get:Input
    abstract val language: Property<String>

    /** Expected SHA-256 of the downloaded model, lowercase hex. */
    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val sourceUrl: Property<String>

    /** Wired into the Android variant's assets; becomes `assets/tessdata/<lang>.traineddata`. */
    @get:OutputDirectory
    abstract val assetsDirectory: DirectoryProperty

    /** Added to the JVM source set's resources; becomes `tessdata/<lang>.traineddata`. */
    @get:OutputDirectory
    abstract val resourcesDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val code = language.get()
        val targets = listOf(assetsDirectory, resourcesDirectory).map { directory ->
            directory.get().dir(TESSDATA).asFile.apply { mkdirs() }
                .resolve("$code.traineddata")
        }
        val expected = sha256.get()
        // Both outputs are identical, so download once even though it lands twice.
        // The check is on the digest, not on mere existence: Gradle does not clear an
        // @OutputDirectory before re-running, so bumping the expected checksum would
        // otherwise re-run the task, find the stale file, and package it unverified --
        // defeating the guarantee this task exists to provide.
        if (targets.all { it.isFile && it.readBytes().sha256() == expected }) {
            logger.info("$code.traineddata already present and verified")
            return
        }

        val url = sourceUrl.get()
        logger.lifecycle("downloading $code.traineddata from $url")
        val connection = URI(url).toURL().openConnection().apply {
            // A stalled mirror should fail the build, not hang it forever.
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
        }
        val bytes = connection.getInputStream().use { it.readBytes() }

        val digest = bytes.sha256()
        check(digest == expected) {
            "$code.traineddata checksum mismatch.\n" +
                "  expected: $expected\n" +
                "  actual:   $digest\n" +
                "Either upstream republished the model or the download was tampered with; " +
                "verify before updating the expected value."
        }

        targets.forEach { target ->
            target.writeBytes(bytes)
            logger.lifecycle("  -> ${target.path} (${bytes.size / 1024} KiB)")
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val TESSDATA = "tessdata"
        const val TIMEOUT_MILLIS = 30_000
    }
}
