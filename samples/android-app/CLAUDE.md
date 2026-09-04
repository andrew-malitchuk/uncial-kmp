# samples/android-app — Claude Instructions

## Module Purpose

Proves the Android side of the SDK on a real device: `PdfRenderer`, Tesseract4Android,
language data from the `uncial-lang-*` assets, `androidx.startup`, and R8. It is a *consumer*,
and its main job is to demonstrate how little integration Uncial needs.

## Package Layout

```
source/activity/   MainActivity
source/app/        the navigation stack
source/screen/     welcome, home, progress, document, device
source/viewmodel/  OcrViewModel
source/model/      UI state
core/image/        EXIF orientation, downscaling
core/{asset,content,document,text}/
ui/                the design system — its own core/source pair
```

## Rules

- **No SDK logic here.** No engine selection, no `tessdata` copying, no Uncial
  initialization. If the sample needs a workaround to use the SDK, the workaround belongs in
  the SDK — that is the signal this module exists to give.
- **Compose lives in the sample, never in the SDK.** Do not let a UI dependency drift into
  `:sdk:*`.
- **Release builds are the ones that matter.** `isMinifyEnabled = true`; JNI entry points and
  `androidx.startup` providers are invisible to R8 and survive only through each AAR's
  `consumer-rules/*.pro`. Verify with `assembleRelease`, not `installDebug`.
- **`android:name=".source.activity.MainActivity"` is a string in the manifest.** So is the
  `FileProvider` authority and every initializer. Moving a class is a runtime crash, not a
  compile error — check the merged manifest.
- **XML comments cannot contain `--`.** In `AndroidManifest.xml` that is a manifest merger
  failure reported only as "Error parsing AndroidManifest.xml".
- **Apply EXIF orientation and cap the size in `core/image/`.** `BitmapFactory` applies
  neither, and neither does Tesseract: a portrait photo recognizes as a sideways page, which
  under `PSM_AUTO` means *no* text rather than bad text. `maxPageSide` guards the rasterizers
  only, and a caller-built raster never goes through one.
- **Do not add `android.permission.CAMERA`.** Launching another app's camera activity needs
  no permission; declaring it would make it required at install time for a capability this
  app never uses directly. The `<queries>` element is what makes the camera app visible.
- **No `catch (Throwable)` around an extraction** — it reports a superseded run as a failure.
- **No tab bar.** The screens are a stack: `Home` is the root, back (gesture included, via
  `BackHandler`) returns there, and on the progress screen back also cancels the run.

## The Design System (`ui/`)

Tokens are data classes in `ui/core`, carried down by `staticCompositionLocalOf` and read
through the single `Theme` accessor. Components live in `ui/source/kit/atom` and
`ui/source/kit/molecule`. **No component names a colour or a size directly** — if you are
reaching for `Color(0xFF…)` or `16.dp` in a screen, add or use a token.

Material3 stays underneath (`Scaffold`, `Snackbar`) with its `ColorScheme` **derived** from
the same tokens, so there is one palette rather than two.

**Fraunces has no Cyrillic**, and Google Fonts' default subset has none either. Inter is
bundled with the Cyrillic subset explicitly, and recognized text renders in the platform
serif. A Ukrainian scan in a Latin-subset font is tofu — and this sample's whole point is a
Ukrainian document. `ui/source/theme/Fonts.kt` says so; keep it saying so.

Add a component to `ui/source/showcase/StyleguideScreen` so a token change can be judged
against the whole inventory at once.

## Verify

```bash
./gradlew :samples:android-app:installDebug
./gradlew :samples:android-app:assembleRelease
```

On a Pixel 8 the scanned fixture runs 4 pages / 31 lines / 207 words at ~95 % confidence in
roughly two seconds, the digital fixture reads from the text layer in ~0.1 s, and a gallery
image goes through `extractAsFlow(raster)` in ~0.5 s. A large deviation is a regression worth
chasing.
