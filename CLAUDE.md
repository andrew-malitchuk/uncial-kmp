# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Uncial** — an on-device OCR SDK for Kotlin Multiplatform (Android / iOS / JVM) that turns
a PDF or a raster image into a **structured document** (pages → lines → words, boxes,
confidence, plus `Heading`/`Paragraph` blocks), not a blob of text. No UI, no networking,
no camera.

`PLAN.md` is the design document and decision log; read it before changing anything
structural. Phases 0–2 and 4 of its §10 are done (scaffolding, code lifted from the POC,
re-contracting, language data including the runtime downloader). Phase 3 (golden corpus)
and phases 5–6 (Maven Central, Fat AAR, XCFramework/SPM, Dokka) are not started; nothing
is publishable yet, not even to `mavenLocal`.

## Commands

The default JDK on this machine is 25, which AGP and KGP do not support. The build pins
its daemon to JDK 21 via `gradle/gradle-daemon-jvm.properties`, so plain `./gradlew` works;
if a tool bypasses that, set `JAVA_HOME` to a 21 install.

```bash
./gradlew build                       # everything: compile, test, checkKotlinAbi
./gradlew allTests                    # tests on jvm + androidHostTest + iosSimulatorArm64
./gradlew :sdk:structure:jvmTest      # one module, one target
./gradlew updateKotlinAbi             # regenerate api/*.api after an intentional API change
./gradlew :samples:android-app:assembleRelease   # R8 on; the only build that proves the rules
./gradlew :sdk:lang-ukr:downloadLanguageData     # fetch + checksum a .traineddata
```

### The iOS sample

An Xcode project, not a Gradle module. Its first build phase runs Gradle to link
`:dist:ios-framework` and stages `Uncial.framework` into `samples/ios-app/build/framework`,
which is where `FRAMEWORK_SEARCH_PATHS` points — so the search path needs no
per-configuration conditionals.

```bash
cd samples/ios-app
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

Swapping `-sdk iphoneos -destination 'generic/platform=iOS'` builds for a device. Both are
verified. Derived data is large; this machine has been close to full, so pass
`-derivedDataPath` and clean up after.

`--rerun-tasks` matters here: `org.gradle.caching=true`, so a green `build` may be entirely
cache hits and tests may not have executed.

### Running OCR for real

The JVM harness is the only way to exercise recognition end to end on a developer machine.
It needs a system Tesseract (`brew install tesseract tesseract-lang`).

```bash
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"        # image-only PDF
./gradlew :samples:cli:run --args="fixture /tmp/text.pdf --text --pages 2" # with text layer
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"
```

`fixture` generates a **scanned** (image-only) Ukrainian PDF on purpose: a normally
generated PDF carries its text, so Uncial takes the digital fast path and proves nothing
about recognition.

## Architecture

The module graph in `PLAN.md` §6.6 is the law. Additions to remember:

- **`runtime` has no compile-time dependency on any engine.** Engines announce themselves
  through `OcrEngineRegistry` (in `core`); on Android the Tesseract engine registers itself
  via `androidx.startup`, elsewhere the consumer calls `installTesseractEngine()` /
  `installVisionEngine()`. Never add an engine to `runtime`'s dependencies.
- **Rasterization and recognition are separate roles** (`PageRasterizer` /
  `TextRecognizer`), which is what makes image input, PNG-based tests, and engine swapping
  possible.
- **One coordinate model:** top-down pixels, origin at the page's top-left. Vision reports
  bottom-up normalized and PDF is bottom-up in points; each engine converts at its own edge
  so no caller ever has to. `pdf-text` scales points by `OcrOptions.renderScale` so digital
  and OCR'd pages of one document are directly comparable.
- **`core` owns the Android `Context`** (`UncialContext` + `UncialContextInitializer`),
  because both `engine-tesseract` and `pdf-text` need one.
- **Platform asymmetry is reported, not hidden:** read `UncialClient.capabilities`. The
  current honest matrix:

  | | words | confidence | per-line language | orientation | skew |
  |---|---|---|---|---|---|
  | Tesseract (JVM) | yes | yes | **yes** | **yes** | **yes** |
  | Tesseract (Android) | yes | yes | no | no | no |
  | Vision (iOS) | yes¹ | yes | no | no | no |

  ¹ derived from `boundingBoxForRange`, not native. The JVM column is richer because
  libtesseract's C API exposes things the Tesseract4Android *Java wrapper* does not — a real
  asymmetry between two bindings of the same engine. Vision also has no Ukrainian before
  iOS 16; the engine queries `supportedRecognitionLanguages` at runtime and drops what it
  cannot do. That query is **per request, not static** — Apple's own header says a language
  available at one recognition level may be absent at another, and Ukrainian is
  accurate-level-only — so the probe must be configured exactly as the real request will
  be, or `RecognitionQuality.Fast` reports `uk-UA` and then quietly recognizes Latin.
- **`:dist:ios-framework`** is the iOS umbrella: one static framework re-exporting every
  module, plus `UncialIos` (exported to Swift as `UncialBootstrap` via `@ObjCName`) which
  hides the roughest edges of the generated Obj-C API — `ByteArray`, the builder lambda,
  `Result`, which does not survive export at all, and `Raster`'s `CGImageRef`, which
  exports as an untyped pointer (hence `extract(client:image: UIImage)`). This is the seed
  of `:sdk:swift` (PLAN.md §6.3), and the umbrella is the one place the engine-isolation
  rule stops applying.
- `structure` is pure `commonMain` and its thresholds live in `StructureOptions`.

Every module carries its own `README.md` (what it is, its API, its pitfalls) and `CLAUDE.md`
(what not to break when editing it). Read the module's pair before changing it — this file is
the repository-wide view, and the per-module ones are where the local rules live.

## Conventions

- Every published module: `explicitApi()` strict + a committed ABI dump. Kotlin's built-in
  `abiValidation` replaces the standalone binary-compatibility-validator; `checkKotlinAbi`
  runs as part of `build`, so an API change fails the build until `updateKotlinAbi` is run
  and the diff reviewed.
- Convention plugins in `build-logic/convention/`: `uncial.kmp.base`,
  `uncial.target.{android,jvm,ios}`, `uncial.language-data`, `uncial.publish`,
  `uncial.publish.aggregation` (root only). Target set is exactly android + jvm + iosArm64 +
  iosSimulatorArm64 + iosX64. They are `Plugin<Project>` classes under
  `uncial.convention.{core,source}`, not precompiled scripts: `core/` is internal
  infrastructure, `source/` holds the class behind each id, and all of them extend
  `BaseConventionPlugin`, whose `final apply` fixes the step order (plugins → extensions →
  kotlin → targets → source sets → tasks → artifacts). `build-logic/convention/README.md`
  is the reference; the ids are API, named as strings by every module.
- A module's `consumer-rules/*.pro` is packaged into its AAR automatically.
- Public KDoc is English (these artifacts go to Maven Central).

## Layout: `core/` and `source/`

Borrowed from `~/StudioProjects/bitshift-kmp`, where every module states it the same way:
**`core/` is what is `internal`, `source/` is the surface**, each nested one level deep in a
semantic folder, and no `.kt` file sits loose at a package root. This holds in **every**
module — the 10 SDK modules, `:dist:ios-framework`, both samples and the convention plugins:

```
sdk/model/…/model/          source/{geometry,text,structure,language,options,error}/
sdk/core/…/core/            source/{engine,raster,language,log,recognizer,android}/
sdk/raster/…/raster/        source/rasterizer/
sdk/runtime/…/runtime/      source/{client,progress}/   core/{pipeline,error,fake}/
sdk/structure/…/structure/  source/{reconstruction,options}/  core/{builder,fixture}/
sdk/pdf-text/…/pdftext/     source/extractor/           core/assembly/
sdk/engine-tesseract/…/     source/{engine,install,options,language}/
                            core/{recognizer,native,language,cancel}/
sdk/engine-vision/…/        source/{engine,install}/    core/{recognizer,language,fixture}/
sdk/engine-fake/…/          source/{engine,raster}/
sdk/lang-download/…/        source/{provider,tessdata}/ core/{provider,http,verification}/
dist/ios-framework/…/dist/  source/{bootstrap,model}/   core/{client,extraction,interop,summary}/
samples/android-app/…/      source/{activity,app,viewmodel,model,screen}/
                            core/{asset,content,document,image,text}/  ui/ (own core/source pair)
samples/cli/…/cli/          source/entrypoint/          core/{command,fixture,report,log,args}/
build-logic/…/convention/   source/{base,kmp,target,language,publish}/
                            core/{identity,catalog,naming,dsl,language,publish}/
```

`core/` exists only where a module really has internal declarations: `model`, `raster` and
`engine-fake` have none and so have no `core/`.

Things to know before moving any of it back:

- **`:sdk:runtime` does not sit on the bare package root.** `UncialClient` is
  `…uncial.runtime.source.client.UncialClient`, not `…uncial.UncialClient`. It had to move:
  its internals would otherwise be `…uncial.core.*`, the package the whole `:sdk:core`
  module already owns — a split package across two artifacts.
- **`UncialIos` is a façade on purpose.** A Kotlin `object` cannot span files and splitting
  it into several objects would rename the Swift API, so every member is a one-line
  delegation into `core/` and the bodies live there. Package moves are invisible to Swift —
  every exported type carries `@ObjCName(exact = true)`.
- **Five bindings name classes by string and break silently**, because no compiler checks
  them: the `androidx.startup` initializers in `sdk/core` and `sdk/engine-tesseract`'s
  `androidMain/AndroidManifest.xml`, the matching `-keep` lines in their
  `consumer-rules/*.pro`, the sample manifest's
  `android:name=".source.activity.MainActivity"`, and the CLI's `application.mainClass`.
  A green build proves nothing about these; the APK's merged manifest and its dex are where
  you check. The AGP `namespace` is unchanged, so `R` is unaffected.
- **One file, one artifact.** Every top-level `class`/`interface`/`object`/`fun`/`val` that
  is `public` or `internal` lives in its own file, named after it. The one exception is a
  `private` helper or constant that serves exactly one artifact: it stays in that artifact's
  file, because moving it out would force it to `internal` and widen the surface for no gain.
- **On the JVM a file name is part of the binary API.** Top-level declarations compile into a
  synthetic `<FileName>Kt` class, so splitting a file renames that class: `PdfTextExtractorKt`
  became `PdfTextExtractorIdKt`, `UncialClientBuilderKt` became `UncialClientFactoryKt`, and
  `OcrLoggerKt` became `DebugKt`/`InfoKt`/`WarnKt`/`ErrorKt`. Kotlin callers never see this;
  Java callers do. Use `@file:JvmName` if one of these ever has to be pinned.
- **Every `api/*.api` dump carries the package path**, so `updateKotlinAbi` is part of any
  such move. A package rename also changes the value-class mangling suffixes
  (`component3-Wf2TQyI` → `component3-CXFpPdI`) — that is expected, not a lost declaration.

The Android sample is laid out as five screens (welcome → home → progress → document →
device) following `sample-app-design.html` in the repository root, and carries a small
design system in `ui/` — token data classes in `ui/core`, a `Theme` accessor over
`staticCompositionLocalOf`, components under `ui/source/kit/atom|molecule`, the whole
inventory previewable through `ui/source/showcase/StyleguideScreen`. The structure is
borrowed from `~/StudioProjects/bitshift-kmp/presentation-core-ui`; the palette is Uncial's
own. Material3 stays underneath (`Scaffold`, `Snackbar`) with its `ColorScheme` **derived**
from the same tokens, so there is one palette rather than two.

The iOS sample carries the same five screens and the same tokens, written a second time in
SwiftUI (`Theme/`, `Kit/`, `Screens/`) — nothing is shared. Two additions to `UncialIos`
exist for it: `extract(…, onProgress:)`, because a `Flow` does not export, and
`summarize(document:)`, because `Confidence` is a value class and Swift cannot do
arithmetic on one.

**No tab bars** — the owner does not want them. Navigation is a stack: `Home` is the root,
secondary destinations are reached from a top-bar action or a card in the content, and back
(including the gesture, via `BackHandler`) returns to the root.

## Traps already paid for

Do not rediscover these:

- **AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android` is rejected. KMP
  library modules use `com.android.kotlin.multiplatform.library` with config in
  `kotlin { android { } }` — no flavors, no build types, no `BuildConfig`.
- **All plugins must be declared in the root `build.gradle.kts` with `apply false`,** or a
  module applying Kotlin directly and modules applying it via `build-logic` load two copies
  of KGP and Gradle refuses to share its build services.
- **`androidResources { enable = true }` is required for a KMP Android library to package
  assets at all** — without it the `lang-*` AARs ship empty, silently. Their `.traineddata`
  is also wired as a *generated* source directory; a static `src/androidMain/assets` is not
  picked up.
- **The JVM engine talks to libtesseract's C API directly** (`TessHandle`), not through
  Tess4J's `getWords`. That path goes via `lept4j`, whose bundled bindings are pinned to a
  Leptonica ABI, and dies with `symbol not found: pixFindBaselinesGen` against Homebrew's
  Leptonica 1.85. Do not "simplify" it back.
- **Tesseract's datapath convention differs by binding:** the Tesseract4Android Java wrapper
  appends `tessdata/` itself and wants the parent; Tess4J/libtesseract wants the `tessdata`
  directory. `LanguageDataProvider` returns the parent and the JVM engine descends.
- **`SetSourceResolution` must be called after `SetImage`,** or libtesseract ignores it and
  estimates the dpi instead. It also must be told the **effective** dpi, which is not
  `OcrOptions.renderDpi`: rasterizers clamp that whenever the page would exceed
  `maxPageSide`. `Raster.sourceDpi` carries the real figure, and is `null` for an image the
  caller supplied — in which case say nothing and let Tesseract estimate rather than
  asserting a number nobody rendered at.
- **`kotlin.runCatching` is banned in the extraction path** — it swallows
  `CancellationException`. Use `runCatchingOcr` (runtime) or `orElseOnFailure` /
  `runReportingFailure` (`engine-tesseract`, which must not depend on `runtime`). The rule
  covers the samples too: a hand-rolled `catch (Throwable)` around an extraction is the
  same bug, and reports a superseded run as a failure.
- **`close()` on a recognizer blocks.** Engines take a *blocking* `ReentrantLock` around
  their native calls, because `AutoCloseable.close()` cannot take a suspending mutex and
  freeing a handle underneath a running `TessBaseAPIRecognize` is a SIGSEGV, not an
  exception. So closing waits out the work in flight — Android calls `stop()` first to cut
  that short. Do not call `UncialClient.close()` on the main thread without expecting a
  stall.
- **`PDFTextStripper.writeString` is not a per-line callback.** With `sortByPosition` it
  fires once per *word* whenever a PDF positions words with `Td`/`TJ` offsets instead of
  real space glyphs — which is common. Buffer the chunks and flush on `writeLineSeparator`,
  plus `endArticle` and `endPage`, which are not followed by a separator.
- **`TextPosition.yDirAdj` is the baseline, not the top of the glyphs.** A box anchored
  straight to it sits one cap-height below its own text. `PDFTextStripper` itself uses
  `positionY - positionHeight`.
- **Use the crop box, never the media box.** `LegacyPDFStreamEngine` expresses every
  `TextPosition` relative to the crop box, and all three rasterizers render it. Rotation is
  a second frame: both renderers swap the sides for `/Rotate 90|270` and the reported page
  size has to swap with them.
- **`CGImageRef` is a raw `CPointer` in Kotlin/Native, with no ARC.** Storing one neither
  retains it nor keeps its owner alive, so a `CGImage` taken from a `UIImage` that then goes
  out of scope is a use-after-free waiting for the GC. `Raster` retains in its constructor
  and releases in `release()`.
- **A `0f` font size must not vote in `DocumentStructure`'s median.** PDFBox reports 0 pt
  for Type3 fonts and degenerate text matrices, so a document can mix real sizes with
  zeros; letting the zeros count drags the median below the body size and turns ordinary
  prose into headings. `NaN` is the same trap from the other side — every comparison
  against it is false.
- **Vision text recognition returns zero observations on the iOS Simulator** (no Neural
  Engine): `performRequests` succeeds, `error` is null, `results` is empty. Verified against
  the framework. It works on a real device — confirmed on hardware, recognizing Ukrainian
  from the bundled scan — so the iOS tests assert the pipeline and capabilities and only
  check text when Vision returned some. A green iOS test run proves nothing about
  recognition quality; only a device does.
- **Photo input needs orientation handling from the caller, on both platforms.** A camera
  writes the sensor's pixels and records the rotation beside them — EXIF on Android,
  `UIImage.imageOrientation` on iOS — and the engines see neither: Tesseract gets a `Bitmap`
  and Vision gets a `CGImage`, which are pixels only. A portrait photo therefore recognizes
  as a sideways page, which under `PSM_AUTO` is *no* text rather than bad text. The Android
  sample applies `ExifInterface.TAG_ORIENTATION` with a `Matrix`
  (`core/image/ImageDecoding.kt`);
  `UncialIos.extract(client:image:)` redraws the `UIImage` into a context. Nothing clamps
  the size of a caller-supplied raster either — `OcrOptions.maxPageSide` guards the
  rasterizers only — so a 48 MP photo is recognized at 48 MP unless the caller downscales.
- **Fraunces has no Cyrillic, and Google Fonts' default subset has none either.** The
  Android sample bundles Inter with the Cyrillic subset explicitly (the legacy CSS endpoint
  with `subset=latin,latin-ext,cyrillic`, which also yields a static TTF rather than a
  variable one) and renders recognized text in the platform serif, not in Fraunces. A
  Ukrainian scan in a Latin-subset font is tofu, and a sample whose whole point is a
  Ukrainian document would be demonstrating the wrong thing.
- **XML comments cannot contain `--`.** The em-dash-ish `--` this file's prose uses freely
  is a manifest merger parse failure in `AndroidManifest.xml`, reported only as
  "Error parsing AndroidManifest.xml".
- Android host (unit) tests cannot create a `Bitmap`. `FakePageRasterizer` therefore uses
  `placeholderRaster`, which carries dimensions and no pixels.
- **Unannotated Obj-C and JNA signatures type as non-null in Kotlin, but really do return
  null.** `PDFDocument(data:)` and `TessResultIteratorGetPageIterator` both do. A plain
  `?: throw` on them is dead code the compiler warns about; assign to an explicitly
  nullable local so the guard survives, and validate for real (page count) as well.
- **`NavigationStack` is iOS 16.** The deployment target is 15, so the sample uses a plain
  `VStack`. Expect this class of paper cut for anything SwiftUI.
- **Custom intermediate source sets** (`lang-download`'s `jvmAndAndroidMain`) must be
  declared through `applyDefaultHierarchyTemplate { }`, not with manual `dependsOn` calls,
  or every build warns that the default template was not applied.
- **Backtick test names must not contain a comma.** Kotlin/Native rejects it (`Name
  contains illegal characters`) while the JVM target accepts it happily, so the break
  only shows up when the iOS targets compile.
- `-Xexpect-actual-classes` is set in `uncial.kmp.base`: `expect class Raster` is deliberate
  and the Beta warning is noise.

## Verified end to end

Recognition has been run for real on all three platforms, not just compiled:

- **JVM** — `:samples:cli`, against a generated image-only Ukrainian PDF.
- **Android** — the redesigned sample on a Pixel 8: the scanned fixture at 4 pages, 31
  lines, 207 words, 95 % confidence in ~2.0 s; the digital fixture read from the text layer
  in ~0.1 s; and the image path, via the photo picker, at 10 lines / 62 words / 92 % in
  ~0.5 s.
- **iOS** — the redesigned sample on hardware, Vision reporting `uk-UA+en-US`. The
  Simulator proves the UI and nothing else: Vision text recognition needs the Neural
  Engine, so it returns no observations there.

Both samples offer the same five actions — scanned fixture, digital fixture, PDF picker,
camera photo, gallery image — which is the quickest way to re-check a change on a device.
The image actions go through `extractAsFlow(raster)`, so they exercise the recognizer with
no PDF machinery at all.

## Open questions

`PLAN.md` §11 is now mostly settled: `pdf-text` and image input are in v1; `lang-ukr` /
`lang-eng` and `lang-download` all exist; minSdk 24 / iOS 15. Still open: publishing the
JVM artifact, and `-slim`.

**iOS 15 deserves revisiting.** Vision has no Ukrainian before iOS 16, so on 15 a Ukrainian
document recognizes as Latin nonsense rather than failing. The SDK reports this through
`capabilities`, but the floor itself is a product decision that has not been remade.

Documentation for the language-data mechanisms — bundled, system, downloaded, and
bring-your-own — is in `docs/language-data.md`.

## Base project

The engine code was lifted from
`~/StudioProjects/yet-another-playground-project-kmp/composeApp/src/*/kotlin/dev/yappk/io/pdf/`
(mapping in `PLAN.md` §3). That project is untouched and still the reference for anything
that looks like a behavioural regression.

## Publishing

`PUBLISHING.md` is the distribution plan and status; `configure/signing/README.md` (gitignored,
along with the GPG key and Portal token beside it) is the release checklist. The short version:

- **Prepared, not published.** All 12 modules produce signed, Central-valid artifacts and the
  aggregated upload bundle builds. Nothing has been uploaded; no deployment exists.
- `uncial.publish` (vanniktech 0.37 `.base`) owns publications, POM, sources and an empty
  javadoc jar; `uncial.publish.aggregation` on the root owns the Central Portal upload through
  **nmcp 0.0.9**, which also holds the list of published modules. A module is published by
  applying `uncial.publish`, giving it a `description` (the POM fails without one, on purpose)
  and adding it to that list.
- **vanniktech's own uploader cannot be used.** It reads credentials only through
  `providers.gradleProperty`, and a project-local secrets file can never become a Gradle
  property: Gradle 9 loads its properties before the settings script runs, so injecting them
  into `gradle.startParameter.projectProperties` from `settings.gradle.kts` silently does
  nothing. nmcp takes credentials as plain values, which is why it does the upload.
- **nmcp 0.1.0 is broken** (depends on an unpublished `gratatouille-runtime` snapshot), and
  nmcp 0.0.9's aggregation Zip task is not configuration-cache compatible, so the upload task
  needs `--no-configuration-cache`. Nothing else in the build does.
- Artifact ids are `uncial-<module>` with KMP appending the target: 12 modules → 58 Gradle
  modules in the repository.

```bash
./gradlew publishToMavenLocal                  # signs with the real key; inspect ~/.m2
./gradlew zipAggregationPublication --no-configuration-cache   # build the bundle, upload nothing
```

### The Swift package

`Package.swift` at the repository root is **generated**, not hand-written:

```bash
./gradlew :dist:ios-framework:updateSwiftPackage   # assemble XCFramework -> zip -> manifest
```

The chain is `assembleUncialReleaseXCFramework` (KGP's own task: three iOS slices, lipo of the
two simulator architectures, `xcodebuild -create-xcframework`) → `zipXcframework` →
`updateSwiftPackage`, which writes the release-asset URL and the zip's SHA-256 into the
manifest. Two properties of SPM drive the whole release order: the manifest is read **at the
git tag**, so it must be committed before tagging and therefore before the GitHub Release it
names exists; and the checksum pins the exact bytes, so the uploaded zip must be the one that
was hashed. Details and the release commands are in `PUBLISHING.md` §5.

The iOS sample stays on the direct framework build phase — a binary target is pinned to a
published zip and cannot serve a dev loop.
