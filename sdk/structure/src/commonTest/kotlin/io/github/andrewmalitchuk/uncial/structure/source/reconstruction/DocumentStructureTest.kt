package io.github.andrewmalitchuk.uncial.structure.source.reconstruction

import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.structure.core.fixture.BODY_SIZE
import io.github.andrewmalitchuk.uncial.structure.core.fixture.document
import io.github.andrewmalitchuk.uncial.structure.core.fixture.line
import io.github.andrewmalitchuk.uncial.structure.core.fixture.page
import io.github.andrewmalitchuk.uncial.structure.core.fixture.singlePage
import io.github.andrewmalitchuk.uncial.structure.core.fixture.stack
import io.github.andrewmalitchuk.uncial.structure.source.options.StructureOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentStructureTest {

    @Test
    fun `empty document produces no blocks`() {
        assertEquals(emptyList(), DocumentStructure.reconstruct(OcrDocument(emptyList())))
    }

    @Test
    fun `document of only blank lines produces no blocks`() {
        val blocks = DocumentStructure.reconstruct(singlePage("   " to BODY_SIZE))
        assertEquals(emptyList(), blocks)
    }

    @Test
    fun `consecutive body lines join into one paragraph`() {
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "Перший рядок абзацу" to BODY_SIZE,
                "продовження того ж абзацу" to BODY_SIZE,
            ),
        )
        assertEquals(1, blocks.size)
        assertEquals(
            "Перший рядок абзацу продовження того ж абзацу",
            assertIs<DocBlock.Paragraph>(blocks[0]).text,
        )
    }

    @Test
    fun `a wide vertical gap starts a new paragraph`() {
        // gap 2.0 exceeds the default paragraphGapRatio of 1.2.
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "Кінець першого абзацу" to BODY_SIZE,
                "Початок другого абзацу" to BODY_SIZE,
                gap = 2.0f,
            ),
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is DocBlock.Paragraph })
    }

    @Test
    fun `a larger line becomes a heading and its level follows how much larger`() {
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "Розділ перший" to BODY_SIZE * 2f,
                "Підрозділ" to BODY_SIZE * 1.3f,
                "Тіло тексту тут" to BODY_SIZE,
                "і ще тіло тексту" to BODY_SIZE,
                "і ще трохи тіла" to BODY_SIZE,
            ),
        )
        assertEquals(1, assertIs<DocBlock.Heading>(blocks[0]).level)
        assertEquals("Розділ перший", blocks[0].text)
        assertEquals(2, assertIs<DocBlock.Heading>(blocks[1]).level)
        assertIs<DocBlock.Paragraph>(blocks[2])
    }

    @Test
    fun `a long line in large type stays a paragraph`() {
        // Size says heading, length says prose. maxHeadingWords settles it -- otherwise a
        // large-print book would be one heading per line.
        val longText = List(30) { "слово" }.joinToString(" ")
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                longText to BODY_SIZE * 2f,
                "звичайний рядок" to BODY_SIZE,
                "ще звичайний рядок" to BODY_SIZE,
                "і третій" to BODY_SIZE,
            ),
        )
        assertTrue(blocks.none { it is DocBlock.Heading }, "expected no headings, got $blocks")
    }

    @Test
    fun `a heading closes the paragraph before it`() {
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "тіло тексту" to BODY_SIZE,
                "ще тіло" to BODY_SIZE,
                "Новий Розділ" to BODY_SIZE * 2f,
                "тіло після заголовка" to BODY_SIZE,
                "і ще після" to BODY_SIZE,
            ),
        )
        assertIs<DocBlock.Paragraph>(blocks[0])
        assertIs<DocBlock.Heading>(blocks[1])
        assertIs<DocBlock.Paragraph>(blocks[2])
    }

    @Test
    fun `end-of-line hyphenation is resolved`() {
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "сло-" to BODY_SIZE,
                "во продовжується" to BODY_SIZE,
            ),
        )
        assertEquals("слово продовжується", blocks.single().text)
    }

    @Test
    fun `a paragraph continues across a page break`() {
        // Across pages the previous line's position says nothing, so the gap heuristic must
        // not fire -- which is right for books, where paragraphs straddle the page turn.
        val doc = document(
            page(0, stack("абзац починається на першій сторінці" to BODY_SIZE)),
            page(1, stack("і завершується на другій" to BODY_SIZE)),
        )
        val blocks = DocumentStructure.reconstruct(doc)
        assertEquals(
            "абзац починається на першій сторінці і завершується на другій",
            blocks.single().text,
        )
    }

    @Test
    fun `a lone page number is dropped wherever it appears`() {
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "42" to BODY_SIZE,
                "справжній текст" to BODY_SIZE,
                "ще справжній текст" to BODY_SIZE,
            ),
        )
        assertEquals("справжній текст ще справжній текст", blocks.single().text)
    }

    @Test
    fun `a running header repeated across pages is dropped`() {
        val cities = listOf("Київ", "Львів", "Одеса", "Харків", "Полтава", "Суми")
        val doc = document(
            *cities.mapIndexed { index, city ->
                page(
                    index,
                    stack(
                        // The header is the first line on every page: chrome.
                        "Історія України" to BODY_SIZE,
                        // Body text differs non-numerically, so digit normalisation cannot
                        // collapse these into a repeated line.
                        "унікальний текст про $city" to BODY_SIZE,
                        // A footer takes the trailing edge slot, so the body is not at one.
                        "видавництво" to BODY_SIZE,
                    ),
                )
            }.toTypedArray(),
        )
        val text = DocumentStructure.reconstruct(doc).joinToString(" ") { it.text }
        assertTrue("унікальний текст про Київ" in text, "body text should survive: $text")
        assertTrue(
            "Історія України" !in text,
            "the repeated header should have been dropped, got: $text",
        )
    }

    @Test
    fun `a repeated header survives when chrome detection is disabled`() {
        val doc = document(
            *(0 until 6).map { index ->
                page(index, stack("Історія України" to BODY_SIZE, "текст $index" to BODY_SIZE))
            }.toTypedArray(),
        )
        val text = DocumentStructure
            .reconstruct(doc, StructureOptions.KeepChrome)
            .joinToString(" ") { it.text }
        assertTrue("Історія України" in text, "header should be kept, got: $text")
    }

    @Test
    fun `a line that appears once is never chrome however few the pages`() {
        // Regression: with 4 pages the threshold worked out to int(4 * 0.4) = 1, so a line
        // seen a single time counted as "repeating" and every page's last line was dropped.
        // Caught on a real 4-page scan, where each paragraph lost its final line.
        // Distinct WITHOUT digits: normalisation turns "рядок 1"/"рядок 2" into the same
        // "рядок #", which would make these look repeated for a different reason.
        val farewells = listOf("на добраніч", "до зустрічі", "бувай здоров", "щасти тобі")
        val doc = document(
            *farewells.mapIndexed { index, farewell ->
                page(
                    index,
                    stack(
                        "Історія України" to BODY_SIZE,
                        "тіло сторінки про місто" to BODY_SIZE,
                        farewell to BODY_SIZE,
                    ),
                )
            }.toTypedArray(),
        )
        val text = DocumentStructure.reconstruct(doc).joinToString(" ") { it.text }
        farewells.forEach { farewell ->
            assertTrue(farewell in text, "last line '$farewell' was eaten: $text")
        }
        assertTrue("Історія України" !in text, "the real header should still go: $text")
    }

    @Test
    fun `chrome detection needs enough pages to be meaningful`() {
        // Two pages that share a first line are not evidence of a running header.
        val doc = document(
            page(0, stack("Спільний рядок" to BODY_SIZE, "текст 0" to BODY_SIZE)),
            page(1, stack("Спільний рядок" to BODY_SIZE, "текст 1" to BODY_SIZE)),
        )
        val text = DocumentStructure.reconstruct(doc).joinToString(" ") { it.text }
        assertTrue("Спільний рядок" in text, "got: $text")
    }

    @Test
    fun `a chapter heading on every page is not mistaken for a running header`() {
        // Regression: digit normalisation turns "Розділ 3", "Розділ 4"... into one key
        // "розділ #", which repeats on every page and so passed the chrome threshold.
        // Every chapter title in the book was then deleted as page furniture.
        val doc = document(
            *(3..8).map { chapter ->
                page(
                    chapter - 3,
                    stack(
                        "Розділ $chapter" to BODY_SIZE * 2f,
                        "текст розділу про місто" to BODY_SIZE,
                        "кінцівка розділу $chapter" to BODY_SIZE,
                    ),
                )
            }.toTypedArray(),
        )
        val headings = DocumentStructure.reconstruct(doc).filterIsInstance<DocBlock.Heading>()
        assertEquals(
            (3..8).map { "Розділ $it" },
            headings.map { it.text },
            "every chapter heading should have survived",
        )
    }

    @Test
    fun `a running head set in display type is still dropped`() {
        // The other side of the test above. "Розділ 3" and "Розділ 4" only collapse to one
        // key BECAUSE of digit normalisation -- their raw text differs, so they are
        // content. A running head's raw text is identical on every page, and setting it
        // large does not turn page furniture into a chapter title.
        val doc = document(
            *(0..5).map { index ->
                page(
                    index,
                    stack(
                        "ІСТОРІЯ УКРАЇНИ" to BODY_SIZE * 2f,
                        "текст сторінки номер $index" to BODY_SIZE,
                    ),
                )
            }.toTypedArray(),
        )
        val blocks = DocumentStructure.reconstruct(doc)
        assertTrue(
            blocks.none { it is DocBlock.Heading },
            "a verbatim running head must not be emitted once per page, got $blocks",
        )
    }

    @Test
    fun `a single unreported type size does not turn the document into headings`() {
        // A NaN sorts last among Floats, so it can be the median -- and every comparison
        // against NaN is false, which means headingRatio rejects nothing and short lines
        // all become H2. Non-finite sizes are dropped before the median is taken.
        val doc = document(
            page(
                0,
                listOf(
                    line("рядок без розміру", top = 100f, fontSize = Float.NaN),
                    line("звичайний рядок", top = 120f),
                    line("ще звичайний рядок", top = 140f),
                    line("і третій звичайний", top = 160f),
                ),
            ),
        )
        val blocks = DocumentStructure.reconstruct(doc)
        assertTrue(blocks.none { it is DocBlock.Heading }, "expected no headings, got $blocks")
    }

    @Test
    fun `a document with no type sizes at all keeps its text as paragraphs`() {
        // A recognizer that reports no font size leaves every line at 0f. Headings become
        // undetectable, which is honest; losing the text would not be.
        val doc = document(
            page(
                0,
                listOf(
                    line("перший рядок", top = 100f, fontSize = 0f, height = BODY_SIZE),
                    line("другий рядок", top = 120f, fontSize = 0f, height = BODY_SIZE),
                ),
            ),
        )
        val blocks = DocumentStructure.reconstruct(doc)
        assertEquals("перший рядок другий рядок", assertIs<DocBlock.Paragraph>(blocks.single()).text)
    }

    @Test
    fun `lines with no reported size do not drag the median and invent headings`() {
        // The mixed case, which is the dangerous one: PDFBox reports 0 pt for Type3 fonts
        // and degenerate text matrices, so a single document can carry both real sizes and
        // zeros. If the zeros are allowed to vote, the median lands below the real body
        // size and ordinary text starts clearing the h1 ratio -- turning a page of prose
        // into a stack of headings.
        val doc = document(
            page(
                0,
                listOf(
                    line("нуль один", top = 100f, fontSize = 0f, height = BODY_SIZE),
                    line("нуль два", top = 120f, fontSize = 0f, height = BODY_SIZE),
                    line("справжній один", top = 140f, fontSize = BODY_SIZE, height = BODY_SIZE),
                    line("справжній два", top = 160f, fontSize = BODY_SIZE, height = BODY_SIZE),
                ),
            ),
        )
        val blocks = DocumentStructure.reconstruct(doc)
        assertTrue(
            blocks.none { it is DocBlock.Heading },
            "body text at the median size must not become a heading, got $blocks",
        )
    }

    @Test
    fun `KeepChrome keeps page numbers too`() {
        // The option says Keep. A page number is chrome found by a different rule, not a
        // different decision.
        val blocks = DocumentStructure.reconstruct(
            singlePage(
                "42" to BODY_SIZE,
                "справжній текст" to BODY_SIZE,
            ),
            StructureOptions.KeepChrome,
        )
        val text = blocks.joinToString(" ") { it.text }
        assertTrue("42" in text, "the page number should have been kept, got: $text")
    }

    @Test
    fun `lines are read top to bottom regardless of engine order`() {
        // Vision returns observations ordered by confidence, not position.
        val shuffled = page(
            0,
            listOf(
                line("третій", top = 132f),
                line("перший", top = 100f),
                line("другий", top = 116f),
            ),
        )
        val blocks = DocumentStructure.reconstruct(document(shuffled))
        assertEquals("перший другий третій", blocks.single().text)
    }
}
