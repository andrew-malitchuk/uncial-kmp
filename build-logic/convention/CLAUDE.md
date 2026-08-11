# build-logic/convention — Claude Instructions

## Module Purpose

Every module's build configuration, written once. A composite build, so these plugins compile
in their own classloader and their classpath — AGP, KGP, vanniktech, nmcp — never leaks into
the main build. `README.md` documents what the plugins do; this file is about changing them.

## Package Layout

```
uncial/convention/
├── core/      internal infrastructure, never applied
│   ├── identity/   the constants more than one plugin agrees on
│   ├── catalog/    version-catalog access, by alias
│   ├── naming/     androidNamespace, artifactId
│   ├── dsl/        typed access to kotlin { }, android { }, androidComponents { }
│   ├── language/   the download task and its URL
│   └── publish/    isSnapshot, PublishingSecrets, the shared POM
└── source/    the surface a module's build script reaches for
    ├── base/       BaseConventionPlugin
    ├── kmp/ target/ language/ publish/
```

Same rules as every other module: `core/` is what is `internal`, `source/` is the surface, no
`.kt` file loose at a package root, and **one file per artifact**, named after it
(`versionOf` → `VersionOf.kt`). A `private` helper serving exactly one artifact stays in that
artifact's file — `moduleName` inside `AndroidNamespace.kt` is the example.

## The Step Order Is Not Negotiable

`BaseConventionPlugin.apply` is `final` and runs seven steps:

```
configurePlugins → configureExtensions → configureKotlin → configureTargets
  → configureSourceSets → configureTasks → configureArtifacts
```

Override only the steps you need. A plugin id must be applied before the extension it
registers can be configured; a target must exist before its source sets; a task must be
registered before it is wired into an artifact. Every *"Extension of type … does not exist"*
failure is that order being broken. **Do not call `pluginManager.apply` outside
`configurePlugins`.**

## Rules

- **Plugin ids are API.** Every module's `plugins { }` block names them as strings; renaming
  one is a repository-wide breaking change that no compiler catches.
- **Adding a plugin means four edits:** the class in `source/<concern>/`, a `register` block
  in `build.gradle.kts`, an `apply false` declaration in the root `build.gradle.kts` (or two
  copies of KGP get loaded and Gradle refuses to share its build services), and the tables in
  `README.md`.
- **Versions come from the catalog, never from a literal.** Use `versionOf` / `intVersionOf` /
  `pluginIdOf`; every lookup ends in `.get()` so a typo fails configuration instead of
  silently skipping a version.
- **Address the Android target by type** (`targets.withType<KotlinMultiplatformAndroidLibraryTarget>()`),
  not by extension name, so a module that only applies KMP configures nothing rather than
  failing on a missing extension.
- **Consumer keep rules are resolved eagerly and cannot be made lazy.** AGP's
  `consumerKeepRules.file(Any)` unwraps a `Provider` on the spot.
- **Never reach for `providers.gradleProperty` for a credential.** Gradle 9 loads properties
  before the settings script runs, so `configure/signing/secrets.properties` can never become
  one. `PublishingSecrets` hands plain values to the plugins.
- **Publishing a module is two edits:** apply `uncial.publish` *and* add the path to
  `PublishAggregationConventionPlugin.PUBLISHED_MODULES`. A module missing from that list
  builds signed artifacts locally and never reaches the Portal.
- **nmcp stays on 0.0.9.** 0.1.0 depends on an unpublished `gratatouille-runtime` snapshot
  and cannot resolve. 0.0.9's aggregation Zip task is not configuration-cache compatible, so
  the upload needs `--no-configuration-cache` — nothing else in the build does.

## Verify

A green `:convention:assemble` only proves it compiles. What proves it configures:

```bash
./gradlew -p build-logic :convention:assemble
./gradlew build                                   # tests + checkKotlinAbi + R8 release APK
./gradlew zipAggregationPublication --no-configuration-cache
```

Then check the artifacts themselves: `uncial-*.aar` names, `proguard.txt` inside the AARs
that have `consumer-rules/`, `assets/tessdata/*.traineddata` in the `lang-*` AARs, and the
namespace in a packaged `AndroidManifest.xml`.
