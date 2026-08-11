package uncial.convention.source.publish

import nmcp.NmcpExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.publish.PublishingSecrets

/**
 * The root project's half of publishing: one deployment out of twelve modules.
 *
 * Every published module produces its artifacts locally; this collects all of them into ONE
 * bundle and makes ONE deployment out of it, so the Portal shows a single thing to release
 * instead of twelve. It is the only genuinely root-scoped concern in the build, which is
 * why the root project applies exactly one plugin. (PUBLISHING.md §A1)
 *
 * Note that nmcp 0.0.9's aggregation Zip task is not configuration-cache compatible:
 * `zipAggregationPublication` and the upload need `--no-configuration-cache`. Nothing else
 * in the build does, and 0.1.0 -- which fixes it -- is unresolvable on Central.
 */
class PublishAggregationConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply("com.gradleup.nmcp")
    }

    override fun Project.configureArtifacts() {
        val secrets = PublishingSecrets(project)

        extensions.configure<NmcpExtension> {
            publishAggregation {
                PUBLISHED_MODULES.forEach { module -> project(module) }

                // Empty rather than null: nmcp wants a value, and an empty one fails at
                // upload with a 401 instead of at configuration time on a machine that is
                // only building.
                username.set(secrets.centralUsername.orEmpty())
                password.set(secrets.centralPassword.orEmpty())

                // The deployment waits in the Portal until it is released by hand.
                // Automatic release is a decision for after a few clean releases, not before
                // the first.
                publicationType.set("USER_MANAGED")
            }
        }
    }

    private companion object {
        /**
         * The published set, and the only place it is written down.
         *
         * A new module is published by applying `uncial.publish` to it and adding a line
         * here; anything absent from this list simply never reaches the Portal. The samples
         * and `:dist:ios-framework` are absent on purpose -- the framework is the input to
         * an XCFramework, not a Maven artifact. (PUBLISHING.md §A2)
         */
        val PUBLISHED_MODULES = listOf(
            ":sdk:model",
            ":sdk:core",
            ":sdk:structure",
            ":sdk:raster",
            ":sdk:runtime",
            ":sdk:engine-tesseract",
            ":sdk:engine-vision",
            ":sdk:engine-fake",
            ":sdk:pdf-text",
            ":sdk:lang-ukr",
            ":sdk:lang-eng",
            ":sdk:lang-download",
        )
    }
}
