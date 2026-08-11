package uncial.convention.core.dsl

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project

/**
 * Configures the Android target of a KMP library -- what `kotlin { android { } }` reaches in
 * a build script.
 *
 * Addressed through `targets.withType` rather than by name: the target is contributed by
 * `com.android.kotlin.multiplatform.library`, so a plugin that only applies KMP configures
 * nothing here instead of failing on a missing extension.
 */
internal fun Project.androidLibrary(
    configure: KotlinMultiplatformAndroidLibraryTarget.() -> Unit,
): Unit = kotlinMultiplatform {
    targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java)
        .configureEach(configure)
}
