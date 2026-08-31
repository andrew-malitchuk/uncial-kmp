package io.github.andrewmalitchuk.uncial.samples.cli.core.command

import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.samples.cli.core.log.consoleLogger
import kotlin.system.exitProcess

/**
 * Reports what this machine's engine can actually do.
 *
 * The asymmetry is the point: the JVM binding exposes per-line language, orientation and
 * skew that the Android wrapper of the same engine does not. Uncial reports that rather
 * than pretending otherwise, and this is how a developer reads it.
 */
internal fun reportCapabilities() {
    val client = UncialClient { logger = consoleLogger(verbose = true) }
    client.use {
        val capabilities = it.capabilities
        if (capabilities == null) {
            println("no engine available on this machine")
            println("  install one: brew install tesseract tesseract-lang")
            exitProcess(2)
        }
        println("engine        ${capabilities.engineId} ${capabilities.engineVersion.orEmpty()}")
        println("languages     ${capabilities.languages.joinToString("+") { l -> l.tesseractCode }}")
        println("word level    ${capabilities.wordLevel}")
        println("confidence    ${capabilities.confidence}")
        println("per-line lang ${capabilities.perLineLanguage}")
        println("orientation   ${capabilities.orientationDetection}")
        println("skew          ${capabilities.skewDetection}")
    }
}
