package uncial.convention.core.naming

import org.gradle.api.Project
import uncial.convention.core.identity.Uncial

/**
 * `uncial-engine-tesseract` -- the Maven artifact id, and the AAR/JAR/klib base name.
 *
 * Gradle paths stay readable as `:sdk:engine-tesseract` while the published coordinates and
 * the files on disk agree with each other. KMP appends the target itself, so this also
 * names `uncial-engine-tesseract-android` and the rest. (PLAN.md §6)
 */
internal val Project.artifactId: String
    get() = "${Uncial.ARTIFACT_PREFIX}$name"
