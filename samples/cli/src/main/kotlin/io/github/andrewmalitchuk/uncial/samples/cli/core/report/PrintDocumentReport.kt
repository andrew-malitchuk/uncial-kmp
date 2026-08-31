package io.github.andrewmalitchuk.uncial.samples.cli.core.report

import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument

/**
 * Prints everything a finished document can say about itself.
 *
 * Each figure is reported only when the engine actually produced it: per-line language,
 * skew and confidence are all platform-dependent (see `UncialClient.capabilities`), and
 * printing `0` for something that was never measured would be a claim rather than a
 * reading.
 */
internal fun printDocumentReport(document: OcrDocument, includeWords: Boolean, elapsedMs: Long) {
    println("source      ${document.source}")
    println("pages       ${document.pageCount}")
    println("lines       ${document.lines.size}")

    val confidences = document.lines.map { line -> line.confidence }.filter { c -> c.isKnown }
    if (confidences.isNotEmpty()) {
        println(
            "confidence  min ${"%.2f".format(confidences.min().value)} " +
                "avg ${"%.2f".format(confidences.map { c -> c.value }.average())}",
        )
    }
    if (includeWords) println("words       ${document.lines.sumOf { l -> l.words.size }}")

    val byLanguage = document.lines.mapNotNull { line -> line.language?.tesseractCode }
        .groupingBy { it }.eachCount()
    if (byLanguage.isNotEmpty()) {
        println("languages   " + byLanguage.entries.joinToString(", ") { "${it.key}=${it.value}" })
    }

    val byScript = document.lines.groupingBy { line -> line.script }.eachCount()
    println("scripts     " + byScript.entries.joinToString(", ") { "${it.key}=${it.value}" })
    println(
        "orientation " + document.pages.map { page -> page.orientation }.distinct()
            .joinToString(", "),
    )

    val skewed = document.lines.filter { line -> line.skewDegrees != 0f }
    if (skewed.isNotEmpty()) {
        println(
            "skew        ${skewed.size} lines, max " +
                "${"%.2f".format(skewed.maxOf { line -> line.skewDegrees })}°",
        )
    }
    println("elapsed     ${elapsedMs}ms")
}
