package io.github.andrewmalitchuk.uncial.samples.android.ui.source.kit.atom.chip

/** What a [Chip] is saying about the thing it is attached to. */
enum class ChipTone {
    /** A fact: a language, a code, a count. */
    Neutral,

    /** A fact the user chose, or one the engine confirmed. */
    Selected,

    /** Something the engine is doing — the OCR path, an active engine. */
    Accent,

    /** Something this device cannot do. Shown, never hidden. */
    Unavailable,
}
