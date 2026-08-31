package io.github.andrewmalitchuk.uncial.samples.cli.core.args

/** The first bare argument — the path every command takes — or `null` if there is none. */
internal fun List<String>.firstPathOrNull(): String? = firstOrNull { !it.startsWith("--") }
