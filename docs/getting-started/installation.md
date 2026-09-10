# Installation

!!! warning "Prepared, but not released"
    Uncial is **not on Maven Central yet**, so pasting a coordinate into a
    `build.gradle.kts` still fails to resolve. Publishing is wired, though: every module
    produces signed, Central-valid artifacts, and `./gradlew publishToMavenLocal` installs
    all twelve into `~/.m2` today. The iOS side is the same story — the XCFramework and its
    `Package.swift` are generated, but no GitHub Release exists for SwiftPM to download.

    So there are two working paths right now: build from source as the
    [samples](../samples.md) do, or publish to `mavenLocal` and consume the real
    coordinates from there.

---

## What works today

The samples are the reference consumers, and they depend on the SDK as Gradle projects:

```kotlin
// samples/cli/build.gradle.kts
dependencies {
    implementation(projects.sdk.runtime)
    implementation(projects.sdk.structure)
    implementation(projects.sdk.engineTesseract)   // an engine is not optional — see below
    implementation(projects.sdk.pdfText)           // optional: the digital fast path
    runtimeOnly(projects.sdk.langUkr)
    runtimeOnly(projects.sdk.langEng)
}
```

So the verified path for trying Uncial in your own code is to clone this repository, add
your module to its `settings.gradle.kts`, and depend on `projects.sdk.*` the same way.

```bash
git clone https://github.com/andrew-malitchuk/uncial-kmp.git
cd uncial-kmp
./gradlew build          # compile + test + checkKotlinAbi
```

### Or publish to `mavenLocal` and consume the coordinates

```bash
./gradlew publishToMavenLocal
```

Twelve modules become 58 Maven coordinates — a root module per library plus one per target
— and the root coordinate is the one you name. Add `mavenLocal()` to your repositories:

```kotlin
dependencies {
    implementation("io.github.andrew-malitchuk:uncial-runtime:0.1.0-SNAPSHOT")
    implementation("io.github.andrew-malitchuk:uncial-engine-tesseract:0.1.0-SNAPSHOT")
    implementation("io.github.andrew-malitchuk:uncial-lang-ukr:0.1.0-SNAPSHOT")
}
```

!!! warning "Tesseract4Android comes from JitPack"
    `uncial-engine-tesseract` declares a dependency on
    `com.github.adaptech-cz.Tesseract4Android`, which exists only on JitPack. Until the fat
    AAR of `PLAN.md` §8 embeds it, a consumer has to add that repository:

    ```kotlin
    maven {
        url = uri("https://jitpack.io")
        content { includeGroupAndSubgroups("com.github.adaptech-cz") }
    }
    ```

    Scoping it with `content { }` keeps JitPack from resolving anything else.

!!! note "Composite builds are untested"
    Pulling this repository into another build with `includeBuild` has not been tried.
    Publication coordinates now exist, so dependency substitution has something to match
    against, but nobody has run it. Treat it as unverified rather than as a documented
    option.

---

## Prerequisites

| Tool | Version | Note |
|---|---|---|
| JDK | **21** | see the JDK note below |
| Android | `minSdk` 24, `compileSdk` 36 | the Android sample overrides `compileSdk` to 37 for itself |
| iOS | deployment target 15.0 | Vision has no Ukrainian before iOS 16 |
| Xcode | any version that builds the iOS sample | only needed for iOS |
| Tesseract | system install | **JVM only** — see below |

### The JDK note

The default JDK on the development machine is **25**, which AGP and KGP do not support.
The build pins its daemon to JDK 21 through `gradle/gradle-daemon-jvm.properties`
(`toolchainVersion=21`), so a plain `./gradlew` works with no extra setup. If some tool
bypasses that mechanism, point `JAVA_HOME` at a 21 install:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

The build compiles on 21 and targets JVM bytecode 11, so the JVM artifact stays usable on
Java 11.

### JVM: a system Tesseract is required

The JVM engine binds to the **system** `libtesseract` — the JVM artifact is deliberately
not self-contained, and `OcrEngineFactory.isAvailable()` reports honestly when the library
is missing.

```bash
brew install tesseract tesseract-lang      # macOS
apt-get install tesseract-ocr tesseract-ocr-ukr   # Debian/Ubuntu
```

**Android and iOS need nothing installed.** On Android, Tesseract4Android bundles
`libtesseract.so` inside the AAR; on iOS, Vision is a system framework.

---

## The modules, and which ones you need

Both columns are usable: the Gradle path inside this repository, the artifact after a
`publishToMavenLocal` (and, once released, from Maven Central). KMP appends the target to
the artifact id itself, so `uncial-core` also publishes as `uncial-core-android`,
`uncial-core-jvm`, `uncial-core-iosarm64` and so on — Gradle picks the right one from the
module metadata, and only a Maven consumer without Gradle metadata names them directly.

| Gradle project | Artifact | Targets | When you need it |
|---|---|---|---|
| `:sdk:runtime` | `uncial-runtime` | android, jvm, ios | **Always.** The entry point. Exposes `model` and `core` as `api`, and pulls `raster` in as an implementation detail. |
| `:sdk:engine-tesseract` | `uncial-engine-tesseract` | android, jvm | The engine on Android and the JVM. |
| `:sdk:engine-vision` | `uncial-engine-vision` | ios | The engine on iOS. |
| `:sdk:structure` | `uncial-structure` | all | Turning a document into `Heading` / `Paragraph` blocks. |
| `:sdk:pdf-text` | `uncial-pdf-text` | android, jvm, ios | The digital text-layer fast path. Optional. |
| `:sdk:lang-ukr` / `:sdk:lang-eng` | `uncial-lang-ukr` / `-eng` | android, jvm | Bundled Tesseract models. Optional — see [Language data](../language-data.md). |
| `:sdk:lang-download` | `uncial-lang-download` | android, jvm | Fetching models at runtime instead of bundling them. |
| `:sdk:engine-fake` | `uncial-engine-fake` | all | Tests only. |
| `:sdk:model`, `:sdk:core`, `:sdk:raster` | `uncial-model`, `uncial-core`, `uncial-raster` | all | Transitive; you rarely name them yourself. |
| `:dist:ios-framework` | — | ios | The iOS umbrella framework (`Uncial.framework`), and the source of the XCFramework behind `Package.swift`. Not a Maven artifact. |

So a working set is: **runtime + an engine**, plus optionally `pdf-text`, `structure` and a
language-data artifact.

### The runtime alone cannot recognize anything

`:sdk:runtime` has **no compile-time dependency on any engine**, by design. Engines
announce themselves through `OcrEngineRegistry`, so the runtime never knows an engine's
class name. The consequence is on you: if you do not add an engine module, every `extract`
call fails with `OcrError.Unsupported`, and the message says exactly that.

=== "Android"

    ```kotlin
    implementation(projects.sdk.runtime)
    implementation(projects.sdk.engineTesseract)
    ```

    Nothing else. The Tesseract engine registers itself at process start through
    `androidx.startup`, and `:sdk:core` supplies the `Context` the same way. There is no
    `init()` call in the Android sample at all.

=== "JVM"

    ```kotlin
    implementation(projects.sdk.runtime)
    implementation(projects.sdk.engineTesseract)
    ```

    There is no `androidx.startup` here, so register once, early — in `main`, or in a DI
    module:

    ```kotlin
    installTesseractEngine()
    ```

=== "iOS"

    iOS consumers get one binary rather than a set of modules: `:dist:ios-framework` is a
    static `Uncial.framework` that re-exports `model`, `core`, `structure`, `runtime`,
    `engine-vision` and `pdf-text`. Register Vision once at launch:

    ```swift
    UncialBootstrap.shared.start()
    ```

    How the sample stages that framework is described under [Samples](../samples.md).

---

## Language data

Tesseract needs a `.traineddata` per language; Vision does not. The `lang-*` models are
**not committed to git** — they are downloaded and checksum-verified by a Gradle task that
packaging already depends on, so a normal build fetches them for you. To do it explicitly:

```bash
./gradlew :sdk:lang-ukr:downloadLanguageData
./gradlew :sdk:lang-eng:downloadLanguageData
```

Bundling is only one of four ways to supply models; the system install, the runtime
downloader and bring-your-own are covered in [Language data](../language-data.md).

---

## Verifying the build

```bash
./gradlew build                     # compile, test, checkKotlinAbi
./gradlew allTests                  # jvm + androidHostTest + iosSimulatorArm64
./gradlew :sdk:structure:jvmTest    # one module, one target
```

!!! tip "A green build may have run nothing"
    `org.gradle.caching=true`, so a repeat `build` can be entirely cache hits and the tests
    may not have executed. Add `--rerun-tasks` when you need to be sure.

An intentional public-API change fails `checkKotlinAbi` until the dump is regenerated:

```bash
./gradlew updateKotlinAbi           # then review the diff in api/*.api
```

R8 is the one thing only a release build proves — JNI entry points and `androidx.startup`
providers are invisible to it without the consumer rules each AAR carries:

```bash
./gradlew :samples:android-app:assembleRelease
```

---

Next: [Quick start](quick-start.md).
