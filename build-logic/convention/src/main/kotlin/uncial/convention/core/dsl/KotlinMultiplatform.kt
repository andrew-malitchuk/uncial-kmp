package uncial.convention.core.dsl

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Configures `kotlin { }`. The KMP plugin must already be applied. */
internal fun Project.kotlinMultiplatform(configure: KotlinMultiplatformExtension.() -> Unit): Unit =
    extensions.configure<KotlinMultiplatformExtension>(configure)
