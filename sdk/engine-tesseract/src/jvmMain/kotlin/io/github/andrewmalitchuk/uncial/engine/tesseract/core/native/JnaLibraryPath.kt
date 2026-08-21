package io.github.andrewmalitchuk.uncial.engine.tesseract.core.native

import java.io.File

/**
 * Makes JNA able to find `libtesseract`.
 *
 * Tess4J reaches the native library through JNA, which searches the system loader path
 * plus `jna.library.path`. Homebrew installs into `/opt/homebrew/lib`, which is in
 * neither — so on a stock Apple Silicon Mac, Tess4J fails to load a library that is
 * plainly installed. This adds the usual locations to `jna.library.path` if, and only if,
 * a `libtesseract` is actually there.
 *
 * Mutating a system property from a library is unpleasant, but the alternative is telling
 * every consumer to set `-Djna.library.path` by hand. Existing values are preserved.
 */
internal object JnaLibraryPath {

    private const val PROPERTY = "jna.library.path"

    private val candidateDirectories = listOf(
        "/opt/homebrew/lib",
        "/usr/local/lib",
        "/usr/lib",
        "/usr/lib/x86_64-linux-gnu",
        "/usr/lib/aarch64-linux-gnu",
    )

    private val libraryNames = listOf(
        "libtesseract.dylib",
        "libtesseract.5.dylib",
        "libtesseract.so",
        "libtesseract.so.5",
        "tesseract.dll",
    )

    /** Directories that actually contain a `libtesseract`. */
    fun locatedDirectories(): List<String> = candidateDirectories.filter { dir ->
        libraryNames.any { File(dir, it).exists() }
    }

    /** `true` if a `libtesseract` exists somewhere we know to look. */
    fun libraryPresent(): Boolean = locatedDirectories().isNotEmpty()

    /** Adds the located directories to `jna.library.path`, keeping anything already set. */
    @Synchronized
    fun ensureConfigured() {
        val located = locatedDirectories()
        if (located.isEmpty()) return
        val existing = System.getProperty(PROPERTY)
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val merged = (existing + located).distinct()
        if (merged.size != existing.size) {
            System.setProperty(PROPERTY, merged.joinToString(File.pathSeparator))
        }
    }
}
