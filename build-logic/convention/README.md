# build-logic/convention

> Uncial's Gradle convention plugins: every module's build configuration, written once.

## Responsibility

A module's `build.gradle.kts` should declare **what the module is** — its platforms, its
dependencies, its description — and nothing about how any of that is configured. Everything
else lives here: toolchains, `explicitApi()`, ABI validation, Android namespaces, consumer
ProGuard rules, language-data downloads, POMs, signing and the Central Portal upload.

The plugins are **composable rather than one-per-module-type**, which is the one place this
layout departs from its siblings (axiom-sdk, bitshift-kmp). Uncial's modules genuinely
differ in their platform matrix — `engine-vision` is iOS-only, `engine-tesseract` is
Android + JVM, the `lang-*` pair adds language data — and a module's `plugins { }` block is
the clearest place for that to be visible.

## Package layout

```
src/main/kotlin/uncial/convention/
├── core/                                  # internal infrastructure, never applied directly
│   ├── identity/Uncial.kt                 # the constants more than one plugin agrees on
│   ├── catalog/{Libs,VersionOf,IntVersionOf,PluginIdOf}.kt      # the version catalog, by alias
│   ├── naming/{AndroidNamespace,ArtifactId}.kt                  # what a module is called, everywhere
│   ├── dsl/{KotlinMultiplatform,AndroidLibrary,AndroidComponents}.kt  # typed access to Gradle extensions
│   ├── language/{TessdataUrl,DownloadLanguageDataTask}.kt
│   └── publish/{IsSnapshot,PublishingSecrets,UncialMetadata}.kt # signing inputs and the shared POM
└── source/                                # the surface: what a module's build script touches
    ├── base/BaseConventionPlugin.kt       # Template Method over a fixed step order
    ├── kmp/KmpBaseConventionPlugin.kt
    ├── target/{Android,Jvm,Ios}TargetConventionPlugin.kt
    ├── language/LanguageDataConventionPlugin.kt + LanguageDataExtension.kt
    └── publish/{Publish,PublishAggregation}ConventionPlugin.kt
```

**Convention:** `core/` holds `internal` infrastructure; `source/` holds what a module
reaches for — the classes named by a plugin id, the base class they extend, and the
`languageData { }` DSL a `lang-*` module writes. Never flatten `source/`, and never leave a
`.kt` file loose at a package root: each file sits one level deep in a semantic folder.

**One file, one artifact** — the repository-wide rule, and it applies here too. Every
top-level `class`/`object`/`fun`/`val` that is `public` or `internal` lives in its own file,
named after it (`versionOf` → `VersionOf.kt`, the same way `:sdk:core` splits `debug` into
`Debug.kt`). The only thing that shares a file is a `private` helper serving exactly one
artifact — `moduleName` inside `AndroidNamespace.kt`, say — because moving it out would
force it to `internal` and widen the surface for no gain.

## Registered plugins

| Plugin id | Class | Purpose |
|---|---|---|
| `uncial.kmp.base` | `KmpBaseConventionPlugin` | KMP, JVM toolchain, `explicitApi()`, ABI validation, `uncial-<module>` archive names |
| `uncial.target.android` | `AndroidTargetConventionPlugin` | Android target, namespace, SDK levels, host tests, consumer ProGuard rules |
| `uncial.target.jvm` | `JvmTargetConventionPlugin` | `jvm()` target at the catalog's JVM target |
| `uncial.target.ios` | `IosTargetConventionPlugin` | `iosArm64` + `iosSimulatorArm64` + `iosX64` |
| `uncial.language-data` | `LanguageDataConventionPlugin` | `languageData { }`, the `.traineddata` download, and its packaging |
| `uncial.publish` | `PublishConventionPlugin` | Publications, POM, sources jar, signing, nmcp handshake |
| `uncial.publish.aggregation` | `PublishAggregationConventionPlugin` | **Root project only.** One deployment out of twelve modules |

## Usage

```kotlin
// A module on every platform — :sdk:model, :sdk:core, :sdk:runtime, …
plugins {
    id("uncial.kmp.base")
    id("uncial.target.android")
    id("uncial.target.jvm")
    id("uncial.target.ios")
    id("uncial.publish")
}

description = "What this module is. Publishing fails without it, on purpose."
```

Drop `uncial.target.ios` for an Android/JVM-only module (`engine-tesseract`), keep only
`uncial.target.ios` for an iOS-only one (`engine-vision`), and add `uncial.language-data`
for a `lang-*` module.

## Step order

`BaseConventionPlugin.apply` is `final`, and runs seven steps in a fixed order. A subclass
overrides only the ones it has something to say about:

```
1. configurePlugins()     — apply Gradle plugin ids
2. configureExtensions()  — register the DSL blocks this plugin contributes
3. configureKotlin()      — toolchain, explicit API, ABI validation, compiler options
4. configureTargets()     — register and configure KMP targets
5. configureSourceSets()  — source sets and their dependencies
6. configureTasks()       — register tasks
7. configureArtifacts()   — naming, packaging, publication, signing
```

The order is the point: a plugin id has to be applied before the extension it registers can
be configured, a target has to exist before its source sets do, and a task has to be
registered before it can be wired into an artifact. Every *"Extension of type … does not
exist"* failure is that order being broken, so it is not a subclass's decision to change.

## Adding a plugin

1. Create `source/<concern>/<Name>ConventionPlugin.kt` extending `BaseConventionPlugin`.
2. Register it in `build.gradle.kts` under `gradlePlugin { plugins { … } }` with its id and
   fully-qualified `implementationClass`.
3. Declare any new Gradle plugin it applies in the root `build.gradle.kts` with
   `apply false`, and add its artifact to this project's `dependencies { }`.
4. Update the tables above and `docs/modules/build-logic.md`.

## Known behaviours and pitfalls

**Plugin ids are API.** Every module's `plugins { }` block names them as strings. Renaming
one is a breaking change across the repository, and nothing will catch it but a failed
build.

**Every plugin must also be declared in the root `build.gradle.kts` with `apply false`.**
Otherwise a module applying Kotlin directly (the samples) and modules applying it through
these plugins (the SDK) load two copies of KGP, and Gradle refuses to share its build
services between them.

**AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android` is rejected; KMP
library modules use `com.android.kotlin.multiplatform.library`, configured here through
`targets.withType<KotlinMultiplatformAndroidLibraryTarget>()` — addressed by type rather
than by name so a module that only applies KMP configures nothing instead of failing on a
missing extension.

**A binary plugin has no `libs.` accessors.** Catalog lookups go through
`core/ext/Project.kt` by alias, and every one of them ends in `.get()`: a typo should fail
configuration, not silently skip a version.

**Nothing reads `providers.gradleProperty` for a credential.** Gradle 9 loads its properties
before the settings script runs, so `configure/signing/secrets.properties` can never become
a Gradle property. `PublishingSecrets` hands plain values to the plugins instead.
(`PUBLISHING.md` §A0)

## Build

```bash
./gradlew -p build-logic :convention:assemble   # the plugins compile
./gradlew build                                 # what they configure actually works
```
