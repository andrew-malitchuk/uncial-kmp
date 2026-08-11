plugins {
    `kotlin-dsl`
}

group = "uncial.convention"

// The plugin classpath. Each of these is applied or configured by a convention plugin, and
// nothing here reaches the SDK's own compile classpath -- this is build tooling only.
dependencies {
    implementation(libs.plugin.kotlin.multiplatform)
    implementation(libs.plugin.android)
    implementation(libs.plugin.vanniktech.publish)
    implementation(libs.plugin.nmcp)

    // The id -> class mapping is the one thing here that no compiler checks.
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    compilerOptions {
        // Kotlin's built-in ABI validation is still experimental; opting in once here
        // keeps the @OptIn noise out of every convention plugin.
        optIn.add("org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation")
    }
}

// The registry of everything a module can apply. IDs are stable API for the module build
// scripts: renaming one is a breaking change to every `plugins { }` block in the repository.
gradlePlugin {
    plugins {
        register("kmpBase") {
            id = "uncial.kmp.base"
            implementationClass = "uncial.convention.source.kmp.KmpBaseConventionPlugin"
        }
        register("targetAndroid") {
            id = "uncial.target.android"
            implementationClass = "uncial.convention.source.target.AndroidTargetConventionPlugin"
        }
        register("targetJvm") {
            id = "uncial.target.jvm"
            implementationClass = "uncial.convention.source.target.JvmTargetConventionPlugin"
        }
        register("targetIos") {
            id = "uncial.target.ios"
            implementationClass = "uncial.convention.source.target.IosTargetConventionPlugin"
        }
        register("languageData") {
            id = "uncial.language-data"
            implementationClass = "uncial.convention.source.language.LanguageDataConventionPlugin"
        }
        register("publish") {
            id = "uncial.publish"
            implementationClass = "uncial.convention.source.publish.PublishConventionPlugin"
        }
        register("publishAggregation") {
            id = "uncial.publish.aggregation"
            implementationClass = "uncial.convention.source.publish.PublishAggregationConventionPlugin"
        }
    }
}
