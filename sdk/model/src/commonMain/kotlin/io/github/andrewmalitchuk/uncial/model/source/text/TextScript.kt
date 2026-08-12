package io.github.andrewmalitchuk.uncial.model.source.text

/**
 * The writing system a piece of text is made of.
 *
 * This is deliberately coarser than a language: engines are far more reliable at telling
 * Cyrillic from Latin than `uk` from `ru`, and mixed ukr+eng documents — the case Uncial
 * is built for — routinely put both scripts on one line.
 */
public enum class TextScript {
    Latin,
    Cyrillic,

    /** Both Latin and Cyrillic present, e.g. a Ukrainian sentence quoting an English term. */
    Mixed,

    /** Digits, punctuation, or a script Uncial does not classify. */
    Unknown,
    ;

    public companion object {
        /**
         * Classifies [text] by counting Cyrillic and Latin letters and ignoring everything
         * else, so `"ISO 9001"` is [Latin] rather than [Mixed].
         */
        public fun of(text: String): TextScript {
            var cyrillic = 0
            var latin = 0
            for (ch in text) {
                when {
                    ch in 'Ѐ'..'ӿ' || ch in 'Ԁ'..'ԯ' -> cyrillic++
                    ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
                }
            }
            return when {
                cyrillic > 0 && latin > 0 -> Mixed
                cyrillic > 0 -> Cyrillic
                latin > 0 -> Latin
                else -> Unknown
            }
        }
    }
}
