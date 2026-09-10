# Samples

Three runnable consumers, one per platform. They are the reference for how Uncial is meant
to be used — and, until publishing lands, the only verified way to consume it at all
(see [Installation](getting-started/installation.md)).

All three offer the **same three actions**: the scanned fixture, the digital fixture, and a
file picker. That symmetry is deliberate — it is the quickest way to re-check a change on a
device.

| Sample | Gradle path | What it proves |
|---|---|---|
| CLI | `:samples:cli` | JVM: PDFBox rasterizing, system `libtesseract` recognizing |
| Android app | `:samples:android-app` | Android: `PdfRenderer`, Tesseract4Android, `androidx.startup`, R8 |
| iOS app | *(an Xcode project)* | iOS: PDFKit, Vision, the `Uncial.framework` umbrella |

---

## `samples/cli` — the JVM harness

The only way to exercise recognition end to end on a developer machine, with no emulator
and no device. It needs a **system Tesseract**:

```bash
brew install tesseract tesseract-lang
```

### Commands

```bash
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"        # image-only PDF
./gradlew :samples:cli:run --args="fixture /tmp/text.pdf --text --pages 2" # with text layer
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"
```

- **`capabilities`** — reports the engine, its version, the languages it actually got, and
  whether it can do word boxes, confidence, per-line language, orientation and skew. Exits
  non-zero when no engine is available.
- **`fixture <out.pdf> [--pages N]`** — generates a test document. See below.
- **`ocr <file.pdf>`** — extracts and prints the document: source, page count, line count,
  confidence range, per-language line counts, scripts, orientation, skew, elapsed time, and
  then the reconstructed `Heading` / `Paragraph` structure. Flags: `--dpi N` (default 200),
  `--words`, `--no-digital`, `--quiet`.
- **`download <file.pdf> [--cache DIR]`** — the same as `ocr`, but fetches `.traineddata`
  over the network into an empty cache instead of using the system Tesseract data. It is
  what proves the runtime-download path of [Language data](language-data.md).

Running with no arguments prints the usage text.

### Why `fixture` generates an image-only PDF

`fixture` rasterizes its Ukrainian text into an image and puts *the image* into the PDF, so
the document has no text layer at all.

That is the whole point. A PDF generated the normal way carries its text, so Uncial takes
the **digital fast path** and never touches an OCR engine — proving nothing about
recognition. The scanned fixture produces the input the SDK actually exists for.

`--text` generates the opposite case: a PDF **with** a text layer, for checking that the
fast path is taken and the engine never starts — the difference between milliseconds and
seconds per page. Adding `--ascii` to that forces Helvetica and English text, giving a
small, byte-stable PDF that does not depend on which fonts the machine has.

The generated pages deliberately carry a large heading, a smaller subheading, ordinary body
text, a repeated running header and a page number, so `DocumentStructure` has something
real to reconstruct.

---

## `samples/android-app`

A Compose app that runs the SDK on a real device. Note what it does **not** contain: no
Uncial initialization, no engine selection, no `tessdata` copying. The Tesseract engine
registers itself through `androidx.startup`, and the `uncial-lang-ukr` / `-eng` assets merge
into the APK where the engine finds them.

```bash
./gradlew :samples:android-app:installDebug
```

The release build is the one that matters for shrinking:

```bash
./gradlew :samples:android-app:assembleRelease
```

!!! warning "R8 is only proved by a release build"
    `isMinifyEnabled = true` is on in the sample's release build type, and it is the only
    build that proves the consumer rules work. JNI entry points and `androidx.startup`
    providers are invisible to R8 and get stripped without the `consumer-rules/*.pro` each
    AAR carries.

### Five screens

The sample is laid out as a small app rather than one screen of buttons, following
`sample-app-design.html` in the repository root:

| Screen | What it proves |
|---|---|
| **Welcome** | The one screen that spends the gradient. Everything after it is paper, so the chrome never competes with recognized text. |
| **Home** | Every entry point the SDK has, with the code path named on the card: `OCR` for the image-only fixture, `fast path` for the one with a text layer, and the image row, which goes to `extractAsFlow(raster)` with no PDF involved. |
| **Progress** | Pages, because a page is what `extractAsFlow` emits and the granularity at which cancellation lands. The ring is indeterminate until `OcrProgress.Started` has said how many there are. |
| **Document** | Counts, per-page confidence, then the `DocBlock`s. Three views of one object, none of which is a string — a sample that printed `document.text` would prove nothing. |
| **Device** | `UncialClient.capabilities`, row by row, with `no` written out rather than left blank. |

Two states are deliberately not errors. A page the engine gave no confidence for draws an
empty bar (`not reported` is not `reported as zero` — a digital text layer does not guess),
and a document with no lines gets a card explaining itself rather than a spinner or a
failure.

### The design system

`ui/` holds a small design system in the shape the sample borrows from
[bitshift-kmp](https://github.com/andrew-malitchuk)'s `presentation-core-ui`: token groups
as data classes in `ui/core`, carried down by `staticCompositionLocalOf` and read through a
single `Theme` accessor, with components under `ui/source/kit/atom` and
`ui/source/kit/molecule`. No component names a colour or a size directly.

Material3 is still underneath — `Scaffold` and `Snackbar` — so the `ColorScheme` is
**derived** from the same tokens rather than living beside them. Leaving it at its defaults
would put purple chrome around a warm paper palette.

There is no tab bar. The screens form a stack: `Home` is the root, the device screen is one
action in the top bar, a finished run is a *Last result* card on `Home`, and back (the
gesture included, through `BackHandler`) returns there. On the progress screen back also
cancels the run — leaving one going and unreachable would be the dishonest option.

Fraunces and Inter are bundled in `res/font`. Inter is the Cyrillic subset, not the default
Latin one: the sample's own copy is English, but a recognized line is not. Recognized text
renders in the platform serif rather than Fraunces, which has no Cyrillic at all —
`ui/source/theme/Fonts.kt` says so.

`StyleguideScreen` in `ui/source/showcase` renders the whole inventory into the preview
pane. It is not reachable from the app; it exists so a token change can be judged against
every component at once.

### Camera and gallery

`core/image/` is where the sample's real image work lives, and it is all the part the SDK
deliberately leaves to the app:

- **EXIF orientation.** A camera writes the sensor's pixels and records the rotation in a
  tag. `BitmapFactory` does not apply it and neither does Tesseract, so without this a
  portrait photo recognizes as a sideways page — which under `PSM_AUTO` means no text at
  all, not bad text.
- **A size cap.** `OcrOptions.maxPageSide` guards the rasterizers, and a raster the caller
  built never goes through one. `inSampleSize` brings the long side down to 2400 px without
  ever materializing the full image.
- **`FileProvider`.** `TakePicture` needs somewhere to write, and a `file://` URI handed to
  another app has been a `FileUriExposedException` since Nougat. The provider's paths are
  scoped to one cache subdirectory rather than the whole cache.
- **A `<queries>` element.** Since API 30 an app cannot see that a camera app exists without
  declaring the intent it wants to resolve. Note what is *not* in the manifest:
  `android.permission.CAMERA`. Launching another app's camera activity needs no permission,
  and declaring it would make it required at install time for a capability this app never
  uses directly.

No `sourceDpi` is passed. Nobody rendered the photo at a known resolution, so Tesseract is
told nothing and estimates — better than asserting a number that was never true.

On hardware the scanned fixture runs 4 pages / 31 lines in roughly 2.3 s.

---

## `samples/ios-app`

An **Xcode project, not a Gradle module** — there is no `:samples:ios-app` to invoke.

Its first build phase runs Gradle to link `:dist:ios-framework` and stages
`Uncial.framework` into `samples/ios-app/build/framework`, which is exactly where
`FRAMEWORK_SEARCH_PATHS` points. So the search path needs no per-configuration conditionals
and nothing has to be built by hand first.

```bash
cd samples/ios-app
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

Swapping in `-sdk iphoneos -destination 'generic/platform=iOS'` builds for a device. Both
are verified.

!!! warning "Watch the disk"
    Xcode's derived data is large and this build stages a whole static framework. Pass
    `-derivedDataPath` to keep it somewhere you control, and clean it up afterwards:

    ```bash
    xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
      -derivedDataPath /tmp/uncial-dd build
    rm -rf /tmp/uncial-dd
    ```

### Signing

`DEVELOPMENT_TEAM` is deliberately empty in the committed project — a team ID belongs to a
person, not a repository. The simulator needs none. For a device, supply yours on the
command line rather than committing it:

```bash
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  DEVELOPMENT_TEAM=YOURTEAMID build
```

If you set it in Xcode instead, revert `project.pbxproj` before committing — Xcode writes
the choice straight into the project file.

### What a green Simulator run does not prove

!!! warning "Vision recognizes nothing on the Simulator"
    There is no Neural Engine: `performRequests` succeeds, `error` is `nil`, and `results`
    is empty. The iOS tests therefore assert the pipeline and capabilities, and only check
    text when Vision returned some.

    On hardware this sample recognizes the bundled Ukrainian scan and reports `uk-UA+en-US`.
    Vision also has no Ukrainian before iOS 16, and the deployment target here is 15; where
    that bites, `UncialClient.capabilities` says so rather than hiding it.

### The same five screens

The iOS sample carries the same design as the Android one — welcome, home, progress,
result, device — and the same rule about navigation: a stack, no tab bar, `home` as the
root, back to it. Nothing is shared between the two: SwiftUI and Compose have no common
code, so the tokens (`Theme/Theme.swift`), the kit (`Kit/`) and the screens (`Screens/`)
are written a second time from the same list of values. Fraunces and Inter are bundled in
`Fonts/` and declared in `UIAppFonts`; recognized text uses the system serif, because
Fraunces has no Cyrillic.

Three things the SDK had to grow for this, all in `:dist:ios-framework`:

- **`extract(client:pdf:onProgress:)`** — a Kotlin `Flow` does not export, so per-page
  progress reaches Swift as a callback. It fires on whichever thread the pipeline is on;
  the model hops to the main actor itself.
- **`summarize(document:)`** — `Confidence` is a Kotlin value class over a `Float`, and
  value classes do not survive Obj-C export in a form Swift can do arithmetic on. So the
  counts and the per-page means are computed in Kotlin and handed over as
  `UncialDocumentSummary`.
- **`UncialRunProgress` / `UncialPageSummary`** — the plain types those two speak in.

Both are exactly the kind of edge `:sdk:swift` (PLAN.md §6.3) exists to smooth over, which
is why they live in the umbrella rather than in the app.

### Camera and gallery

The same two rows as the Android sample. `ImagePickers.swift` wraps two UIKit controllers
for SwiftUI — `PHPickerViewController` for the library and `UIImagePickerController` for the
camera — because as of iOS 15 there is no native SwiftUI camera at all, and `PhotosPicker`
is iOS 16. The same deployment-target decision that costs this sample `NavigationStack`.

The photo itself goes to `UncialBootstrap.extract(client:image:)`, which takes a `UIImage`
rather than the `Raster(CGImageRef)` a Swift caller would otherwise have to build by hand:
`CGImageRef` is a bare `CPointer` in Kotlin/Native and exports to Obj-C as an untyped
pointer, so from Swift it would mean `Unmanaged.passUnretained(_:)` and hoping. That
function also normalizes the image's orientation by redrawing it — Vision is handed the
`CGImage`, which does not carry `imageOrientation`, so a camera photo would otherwise reach
it sideways.

Two permissions notes, which differ between the pickers: the library picker runs out of
process and needs nothing, while the camera runs in-process and needs
`NSCameraUsageDescription` in `Info.plist` — missing it is a crash on presentation, not a
denied prompt. The Simulator has no camera, so that button is disabled there.

The Swift side goes through `UncialBootstrap` rather than the raw Kotlin API — see the
[Quick start](getting-started/quick-start.md#ios-from-swift).

---

## Verified end to end

Recognition has been run for real on all three platforms, not merely compiled:

- **JVM** — `:samples:cli`, against a generated image-only Ukrainian PDF.
- **Android** — the redesigned sample on a Pixel 8: the scanned fixture at 4 pages, 31
  lines, 207 words, 95 % confidence in ~2.0 s; the digital fixture read from the text layer
  in ~0.1 s; and the image path, via the photo picker, at 10 lines / 62 words / 92 %.
- **iOS** — the redesigned sample on hardware, Vision reporting `uk-UA+en-US`.
