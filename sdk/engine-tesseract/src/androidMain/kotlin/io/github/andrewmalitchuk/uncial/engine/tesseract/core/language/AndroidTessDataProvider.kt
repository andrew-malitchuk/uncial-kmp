package io.github.andrewmalitchuk.uncial.engine.tesseract.core.language

import android.content.Context
import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.Point
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Supplies `.traineddata` from the app's assets.
 *
 * The `uncial-lang-*` artifacts ship their data as Android assets, and AGP merges a
 * library's assets into the consumer's APK — so a consumer who adds `uncial-lang-ukr` gets
 * `assets/tessdata/ukr.traineddata` without doing anything. This provider copies what it
 * finds there into `filesDir`, because Tesseract cannot read from an asset stream: it
 * needs a real directory path. (PLAN.md §6.4)
 *
 * The copy is done once and skipped on every later run.
 */
internal class AndroidTessDataProvider(
    private val context: Context,
) : LanguageDataProvider {

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        withContext(Dispatchers.IO) {
            requested.filter { language ->
                existsInFiles(language) || existsInAssets(language)
            }
        }

    override suspend fun materialize(languages: List<OcrLanguage>): String =
        withContext(Dispatchers.IO) {
            val targetDir = File(context.filesDir, TESSDATA_DIR)
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw OcrError.NoLanguageData(
                    languages = languages,
                    searchedPath = targetDir.absolutePath,
                    cause = null,
                )
            }

            val copied = languages.filter { language -> copyIfNeeded(language, targetDir) }
            if (copied.isEmpty()) {
                throw OcrError.NoLanguageData(
                    languages = languages,
                    // Point at the assets path, since that is where a consumer who forgot
                    // the uncial-lang-* dependency needs to look.
                    searchedPath = "assets/$TESSDATA_DIR and ${targetDir.absolutePath}",
                )
            }
            // Tesseract's datapath is the PARENT of tessdata/, not tessdata/ itself.
            context.filesDir.absolutePath
        }

    /** @return `true` when the language is present in `filesDir` afterwards. */
    private fun copyIfNeeded(language: OcrLanguage, targetDir: File): Boolean {
        val target = File(targetDir, language.fileName())
        if (target.isFile && target.length() > 0L) return true
        return runCatching {
            context.assets.open("$TESSDATA_DIR/${language.fileName()}").use { input ->
                // Write to a temp name first: a half-copied 3.8 MB model that a later run
                // treats as complete is a failure that only shows up as bad recognition.
                val temp = File(targetDir, language.fileName() + ".part")
                temp.outputStream().use { output -> input.copyTo(output) }
                temp.renameTo(target)
            }
        }.getOrElse { cause ->
            if (cause !is FileNotFoundException) throw cause
            false
        }
    }

    private fun existsInFiles(language: OcrLanguage): Boolean =
        File(File(context.filesDir, TESSDATA_DIR), language.fileName())
            .let { it.isFile && it.length() > 0L }

    private fun existsInAssets(language: OcrLanguage): Boolean = runCatching {
        context.assets.open("$TESSDATA_DIR/${language.fileName()}").close()
        true
    }.getOrDefault(false)

    private companion object {
        const val TESSDATA_DIR = "tessdata"
    }
}
