package uncial.convention.source.base

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Base for every Uncial convention plugin -- a template method over a fixed sequence of
 * configuration steps.
 *
 * A subclass overrides only the steps it has something to say about; the rest are no-ops.
 * [apply] is `final` because the ORDER is the point: a plugin id has to be applied before
 * the extension it registers can be configured, a target has to exist before its source
 * sets do, and a task has to be registered before it can be wired into an artifact. Every
 * "Extension of type ... does not exist" failure in a Gradle build is that order being
 * broken, so it is not a subclass's decision.
 *
 * Step order:
 * 1. [configurePlugins] -- apply Gradle plugin ids
 * 2. [configureExtensions] -- register the DSL blocks this plugin contributes
 * 3. [configureKotlin] -- project-wide Kotlin settings (toolchain, explicit API, ABI validation)
 * 4. [configureTargets] -- register and configure KMP targets
 * 5. [configureSourceSets] -- source sets and their dependencies
 * 6. [configureTasks] -- register tasks
 * 7. [configureArtifacts] -- what ends up packaged and published
 *
 * The plugins are deliberately composable rather than one-per-module-type: Uncial's modules
 * genuinely differ in their platform matrix (`engine-vision` is iOS-only, `engine-tesseract`
 * is Android+JVM, the `lang-*` pair adds language data), and a module's `plugins { }` block
 * is the clearest place for that to be visible.
 */
abstract class BaseConventionPlugin : Plugin<Project> {

    final override fun apply(target: Project) {
        with(target) {
            logger.info("Applying ${this@BaseConventionPlugin::class.simpleName} to $path")

            configurePlugins()
            configureExtensions()
            configureKotlin()
            configureTargets()
            configureSourceSets()
            configureTasks()
            configureArtifacts()
        }
    }

    /** Applies Gradle plugin ids via [getPluginManager]. Nothing else may apply a plugin. */
    protected open fun Project.configurePlugins(): Unit = Unit

    /** Registers the DSL extensions this plugin contributes, e.g. `languageData { }`. */
    protected open fun Project.configureExtensions(): Unit = Unit

    /** Project-wide Kotlin configuration: toolchain, `explicitApi()`, ABI validation, compiler options. */
    protected open fun Project.configureKotlin(): Unit = Unit

    /** Registers and configures KMP targets. */
    protected open fun Project.configureTargets(): Unit = Unit

    /** Adds source sets and their dependencies. */
    protected open fun Project.configureSourceSets(): Unit = Unit

    /** Registers tasks. Wiring their output into an artifact belongs in [configureArtifacts]. */
    protected open fun Project.configureTasks(): Unit = Unit

    /** Artifact naming, packaging, publication and signing -- everything about what ships. */
    protected open fun Project.configureArtifacts(): Unit = Unit
}
