// The build's own build. It is a composite (`includeBuild("build-logic")` in the root
// settings), so the convention plugins compile in their own classloader and their
// classpath -- AGP, KGP, vanniktech, nmcp -- never leaks into the main build.
//
// The plugins live in the `:convention` subproject rather than in this root project: it
// keeps the build script that declares them separate from the settings that resolve them,
// and matches the layout of the sibling SDKs (axiom-sdk, bitshift-kmp).
rootProject.name = "build-logic"

include(":convention")

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        // The same catalog the main build uses: one place where versions are written down.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
