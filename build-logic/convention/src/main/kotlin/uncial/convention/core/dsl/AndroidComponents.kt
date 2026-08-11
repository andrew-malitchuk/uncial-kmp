package uncial.convention.core.dsl

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Configures `androidComponents { }` -- the variant API, which is where generated sources are wired. */
internal fun Project.androidComponents(
    configure: KotlinMultiplatformAndroidComponentsExtension.() -> Unit,
): Unit = extensions.configure<KotlinMultiplatformAndroidComponentsExtension>(configure)
