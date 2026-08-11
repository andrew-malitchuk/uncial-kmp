package uncial.convention.core.catalog

import org.gradle.api.Project

/** A version from the catalog as an Int, e.g. `intVersionOf("android-minSdk")` -> `24`. */
internal fun Project.intVersionOf(alias: String): Int = versionOf(alias).toInt()
