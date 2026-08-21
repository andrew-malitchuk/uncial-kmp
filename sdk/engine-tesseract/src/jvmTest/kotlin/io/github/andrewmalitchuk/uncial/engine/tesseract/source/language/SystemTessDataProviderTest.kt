package io.github.andrewmalitchuk.uncial.engine.tesseract.source.language

import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `TESSDATA_PREFIX` has meant both "the parent of tessdata/" and "tessdata/ itself" across
 * Tesseract versions, and Homebrew sets it to neither. Both shapes have to normalize to the
 * parent, which is what Tesseract's `datapath` wants.
 */
class SystemTessDataProviderTest {

    @Test
    fun `a directory holding tessdata is found`() = runTest {
        val root = tessdataRoot("ukr")

        val provider = SystemTessDataProvider(extraSearchPaths = listOf(root.path))

        assertEquals(root.path, provider.dataPath)
        assertContentEquals(
            listOf(OcrLanguage.Ukrainian),
            provider.available(listOf(OcrLanguage.Ukrainian)),
        )
    }

    @Test
    fun `a path pointing straight at tessdata is accepted too`() = runTest {
        val root = tessdataRoot("ukr")
        val tessdata = File(root, "tessdata")

        val provider = SystemTessDataProvider(extraSearchPaths = listOf(tessdata.path))

        assertEquals(tessdata.path, provider.dataPath)
        assertContentEquals(
            listOf(OcrLanguage.Ukrainian),
            provider.available(listOf(OcrLanguage.Ukrainian)),
        )
    }

    @Test
    fun `a language with no model is not reported as available`() = runTest {
        val root = tessdataRoot("ukr")

        val available = SystemTessDataProvider(extraSearchPaths = listOf(root.path))
            .available(listOf(OcrLanguage.Ukrainian, OcrLanguage.English))

        assertContentEquals(listOf(OcrLanguage.Ukrainian), available)
    }

    @Test
    fun `materialize returns the parent of tessdata`() = runTest {
        val root = tessdataRoot("ukr")

        val datapath = SystemTessDataProvider(extraSearchPaths = listOf(root.path))
            .materialize(listOf(OcrLanguage.Ukrainian))

        // The contract, quirk included: datapath + "/tessdata/ukr.traineddata" must exist.
        assertTrue(File(File(datapath, "tessdata"), "ukr.traineddata").isFile)
    }

    @Test
    fun `materialize refuses when none of the requested models are present`() = runTest {
        val root = tessdataRoot("ukr")

        val failure = assertFailsWith<OcrError.NoLanguageData> {
            SystemTessDataProvider(extraSearchPaths = listOf(root.path))
                .materialize(listOf(OcrLanguage.English))
        }
        assertEquals(root.path, failure.searchedPath)
    }

    @Test
    fun `an empty directory is not a data path`() = runTest {
        val empty = Files.createTempDirectory("uncial-empty").toFile().also { it.deleteOnExit() }

        val provider = SystemTessDataProvider(extraSearchPaths = listOf(empty.path))

        // Falls through to the standard search paths, which on a bare CI machine hold nothing.
        if (provider.dataPath == null) {
            assertTrue(provider.available(listOf(OcrLanguage.Ukrainian)).isEmpty())
        } else {
            assertTrue(provider.dataPath != empty.path, "an empty directory must never win")
        }
    }

    @Test
    fun `a missing search path is simply skipped`() = runTest {
        val provider = SystemTessDataProvider(extraSearchPaths = listOf("/does/not/exist"))

        // Either a system Tesseract answered, or nothing did -- neither may throw.
        val available = provider.available(listOf(OcrLanguage.Ukrainian))
        if (provider.dataPath == null) {
            assertTrue(available.isEmpty())
            assertNull(provider.dataPath)
        }
    }

    private fun tessdataRoot(vararg languages: String): File {
        val root = Files.createTempDirectory("uncial-tessdata").toFile()
        root.deleteOnExit()
        val tessdata = File(root, "tessdata").apply { mkdirs() }
        languages.forEach { code -> File(tessdata, "$code.traineddata").writeText("not a real model") }
        return root
    }
}
