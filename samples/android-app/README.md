# samples/android-app

> A Compose app that runs the SDK on a real device — and the only build that proves the R8 rules.

Gradle `:samples:android-app` · not published

## Responsibility

Proves the Android side end to end: `PdfRenderer` rasterizing, Tesseract4Android recognizing,
language data arriving from the `uncial-lang-*` assets, and the engine registering itself
through `androidx.startup`.

Note what this module does **not** contain: no Uncial initialization, no engine selection, no
`tessdata` copying. That is the DX the SDK is supposed to deliver — adding the dependencies is
the integration.

```bash
./gradlew :samples:android-app:installDebug
./gradlew :samples:android-app:assembleRelease     # R8 on; the build that matters
```

## Layout

```
source/activity/    MainActivity
source/app/         the navigation stack
source/screen/      welcome, home, progress, document, device
source/viewmodel/   OcrViewModel
source/model/       the UI state
core/image/         EXIF orientation, downscaling — the part the SDK leaves to the app
core/{asset,content,document,text}/
ui/                 a small design system, with its own core/source pair
```

## Dependencies

`:sdk:runtime`, `:sdk:structure`, `:sdk:engine-tesseract`, `:sdk:pdf-text`, `:sdk:lang-ukr`,
`:sdk:lang-eng`, plus Compose (BOM), activity, lifecycle and coroutines.

**Compose lives in the sample, never in the SDK.** That is what keeps the Fat AAR and the
XCFramework small and free of version conflicts.

## Five screens

| Screen | What it proves |
|---|---|
| Welcome | the one screen that spends the gradient; everything after it is paper, so chrome never competes with recognized text |
| Home | every entry point the SDK has, with the code path named on the card — `OCR`, `fast path`, and the image row, which goes to `extractAsFlow(raster)` with no PDF involved |
| Progress | pages, because a page is what `extractAsFlow` emits and the granularity at which cancellation lands |
| Document | counts, per-page confidence, then the `DocBlock`s — three views of one object, none of which is a string |
| Device | `UncialClient.capabilities`, row by row, with `no` written out rather than left blank |

Five actions, the same five the iOS sample offers: scanned fixture, digital fixture, PDF
picker, camera photo, gallery image.

## Known behaviours and pitfalls

- **R8 is only proved by a release build.** `isMinifyEnabled = true` is on in the release
  build type, and JNI entry points and `androidx.startup` providers are invisible to R8 —
  they survive only because each AAR carries its own `consumer-rules/*.pro`.
- **Photo input needs EXIF orientation applied by the app.** `BitmapFactory` does not apply
  it and neither does Tesseract, so without `core/image/ImageDecoding.kt` a portrait photo
  recognizes as a sideways page — which under `PSM_AUTO` means *no* text rather than bad text.
  The same file caps the long side at 2400 px, because `maxPageSide` guards the rasterizers
  and a caller-built raster never goes through one.
- **No `sourceDpi` is passed for a photo.** Nobody rendered it at a known resolution, so
  Tesseract is told nothing and estimates.
- **No `CAMERA` permission, and a `<queries>` element instead.** Launching another app's
  camera activity needs no permission; declaring one would make it required at install time
  for a capability this app never uses directly.
- **Fraunces has no Cyrillic.** Inter is bundled with the Cyrillic subset explicitly, and
  recognized text renders in the platform serif — a Ukrainian scan in a Latin-subset font is
  tofu, and would demonstrate the wrong thing.
- **XML comments cannot contain `--`.** In `AndroidManifest.xml` that is a manifest merger
  parse failure reported only as "Error parsing AndroidManifest.xml".
- **No tab bar.** The screens form a stack: `Home` is the root, back (gesture included, via
  `BackHandler`) returns there, and on the progress screen back also cancels the run.

## The design system

`ui/` holds token groups as data classes in `ui/core`, carried down by
`staticCompositionLocalOf` and read through a single `Theme` accessor, with components under
`ui/source/kit/atom` and `ui/source/kit/molecule`. No component names a colour or a size
directly. Material3 stays underneath (`Scaffold`, `Snackbar`) with its `ColorScheme` derived
from the same tokens, so there is one palette rather than two.

`StyleguideScreen` in `ui/source/showcase` renders the whole inventory into the preview pane.
It is not reachable from the app; it exists so a token change can be judged against every
component at once.
