package uncial.convention.source.language

import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import uncial.convention.core.language.tessdataUrl
import uncial.convention.source.base.BaseConventionPlugin
import uncial.convention.core.dsl.androidComponents
import uncial.convention.core.dsl.kotlinMultiplatform
import uncial.convention.core.catalog.pluginIdOf
import uncial.convention.core.language.DownloadLanguageDataTask

/**
 * Makes a module ship a Tesseract language model.
 *
 * The `.traineddata` is not in git: it is downloaded and checksum-verified at build time
 * into `build/`, then wired into the artifact from there -- as a *generated* asset
 * directory on Android and as a resource directory on the JVM. Both wirings carry the task
 * dependency themselves, so nothing has to be ordered by hand, and neither can silently
 * package a model that was never verified. (PLAN.md §6.4)
 *
 * Applied on top of `uncial.target.android` + `uncial.target.jvm`; iOS needs no language
 * data at all, because Vision ships its models with the OS.
 */
class LanguageDataConventionPlugin : BaseConventionPlugin() {

    override fun Project.configurePlugins() {
        pluginManager.apply(pluginIdOf("kotlinMultiplatform"))
    }

    override fun Project.configureExtensions() {
        extensions.create<LanguageDataExtension>(EXTENSION_NAME)
    }

    override fun Project.configureTasks() {
        val languageData = extensions.getByType<LanguageDataExtension>()

        tasks.register<DownloadLanguageDataTask>(TASK_NAME) {
            group = "uncial"
            description = "Downloads this module's .traineddata from tessdata_fast and verifies it."

            language.set(languageData.language)
            sha256.set(languageData.sha256)
            sourceUrl.set(languageData.language.map(::tessdataUrl))

            assetsDirectory.set(layout.buildDirectory.dir("$GENERATED_DIRECTORY/assets"))
            resourcesDirectory.set(layout.buildDirectory.dir("$GENERATED_DIRECTORY/resources"))
        }
    }

    override fun Project.configureArtifacts() {
        val downloadLanguageData = tasks.named<DownloadLanguageDataTask>(TASK_NAME)

        // Android: the download's output is registered as a GENERATED asset directory.
        // Pointing at a static src/ directory does not work here -- the KMP Android library
        // plugin does not pick up src/androidMain/assets, and the AAR ships with no assets
        // at all. (`androidResources { enable = true }`, which the module itself sets, is
        // the other half of that trap.)
        androidComponents {
            onVariants(selector().all()) { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(
                    downloadLanguageData,
                    DownloadLanguageDataTask::assetsDirectory,
                )
            }
        }

        // JVM: an ordinary resource directory. The Provider carries the task dependency.
        kotlinMultiplatform {
            sourceSets.named("jvmMain") {
                resources.srcDir(downloadLanguageData.flatMap { it.resourcesDirectory })
            }
        }
    }

    private companion object {
        const val EXTENSION_NAME = "languageData"
        const val TASK_NAME = "downloadLanguageData"
        const val GENERATED_DIRECTORY = "generated/languageData"
    }
}
