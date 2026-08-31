package io.github.andrewmalitchuk.uncial.samples.cli.core.args

/**
 * Reads the value that follows [flag], or `null` when the flag is absent or last.
 *
 * Deliberately the whole of this harness's argument parsing: a sample that pulled in a CLI
 * library would be demonstrating the library.
 */
internal fun List<String>.valueOf(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0 && index + 1 <= lastIndex) this[index + 1] else null
}
