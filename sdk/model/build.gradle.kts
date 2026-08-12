plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "The Uncial OCR vocabulary: pages, lines, words, boxes and confidence."

// The SDK's vocabulary. It appears in the signature of every other module, so it must
// stay small enough that nobody wants to change it. Kotlin stdlib only — no coroutines,
// no serialization, no io. (PLAN.md §6.1)
