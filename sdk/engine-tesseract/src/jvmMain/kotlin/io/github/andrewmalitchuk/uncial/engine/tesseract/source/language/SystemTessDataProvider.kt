package io.github.andrewmalitchuk.uncial.engine.tesseract.source.language

import io.github.andrewmalitchuk.uncial.core.source.language.LanguageDataProvider
import io.github.andrewmalitchuk.uncial.engine.tesseract.core.language.fileName
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds `.traineddata` where the operating system's Tesseract installation keeps it.
 *
 * The JVM artifact is deliberately **not** self-contained: it binds to a system
 * `libtesseract`, which means the language data is already on disk next to it and copying
 * it into the JAR would be waste. The trade-off is documented rather than hidden, and
 * `isAvailable()` reports it honestly. (PLAN.md §5.9, §11.4)
 *
 * On macOS with Homebrew: `brew install tesseract tesseract-lang`.
 */
public class SystemTessDataProvider(
    /** Extra directories to search before the standard ones. */
    private val extraSearchPaths: List<String> = emptyList(),
) : LanguageDataProvider {

    /** The directory containing `tessdata/`, or the `tessdata/` directory itself. */
    public val dataPath: String? by lazy { findDataPath() }

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        withContext(Dispatchers.IO) {
            val root = dataPath ?: return@withContext emptyList()
            requested.filter { File(tessDataDir(root), it.fileName()).isFile }
        }

    override suspend fun materialize(languages: List<OcrLanguage>): String =
        withContext(Dispatchers.IO) {
            val root = dataPath ?: throw OcrError.NoLanguageData(
                languages = languages,
                searchedPath = searchPaths().joinToString(File.pathSeparator),
            )
            val present = languages.filter { File(tessDataDir(root), it.fileName()).isFile }
            if (present.isEmpty()) {
                throw OcrError.NoLanguageData(languages = languages, searchedPath = root)
            }
            root
        }

    /**
     * Locates a directory whose `tessdata/` subdirectory holds at least one model.
     *
     * Note the quirk this has to absorb: `TESSDATA_PREFIX` has meant both "the parent of
     * tessdata/" and "tessdata/ itself" across Tesseract versions, and Homebrew sets it
     * to neither. Both shapes are accepted and normalized to the parent, which is what
     * Tesseract's `datapath` wants.
     */
    private fun findDataPath(): String? = searchPaths()
        .asSequence()
        .map(::File)
        .firstOrNull { candidate -> hasAnyModel(tessDataDir(candidate.path)) }
        ?.path

    private fun searchPaths(): List<String> = buildList {
        addAll(extraSearchPaths)
        System.getenv("TESSDATA_PREFIX")?.takeIf { it.isNotBlank() }?.let { prefix ->
            add(prefix)
            // Accept TESSDATA_PREFIX pointing straight at tessdata/.
            if (File(prefix).name == TESSDATA_DIR) add(File(prefix).parent ?: prefix)
        }
        add("/opt/homebrew/share")
        add("/usr/local/share")
        add("/usr/share")
        add("/usr/share/tesseract-ocr/5")
        add("/usr/share/tesseract-ocr/4.00")
    }

    private fun tessDataDir(root: String): File {
        val file = File(root)
        return if (file.name == TESSDATA_DIR) file else File(file, TESSDATA_DIR)
    }

    private fun hasAnyModel(dir: File): Boolean =
        dir.isDirectory && dir.listFiles { f -> f.name.endsWith(TRAINEDDATA) }?.isNotEmpty() == true

    private companion object {
        const val TESSDATA_DIR = "tessdata"
        const val TRAINEDDATA = ".traineddata"
    }
}
