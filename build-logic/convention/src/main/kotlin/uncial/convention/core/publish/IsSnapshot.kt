package uncial.convention.core.publish

import org.gradle.api.Project

/**
 * Snapshots sign optionally and releases sign mandatorily; this is the only thing that
 * decides which.
 */
internal val Project.isSnapshot: Boolean
    get() = version.toString().endsWith(SNAPSHOT_SUFFIX)

private const val SNAPSHOT_SUFFIX = "-SNAPSHOT"
