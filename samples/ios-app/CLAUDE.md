# samples/ios-app — Claude Instructions

## Module Purpose

Proves the iOS side of the SDK on a device: PDFKit rasterizing, Vision recognizing, and the
`Uncial.framework` umbrella being usable from Swift. **It is an Xcode project, not a Gradle
module** — there is no `:samples:ios-app` to invoke.

## Layout

```
UncialSample/UncialSampleApp.swift   entry point
UncialSample/ContentView.swift       the navigation stack
UncialSample/Screens/                Welcome, Home, Progress, Document, Device
UncialSample/Kit/                    AppCard, PillButton, StatRow, PageConfidenceChart, …
UncialSample/Theme/Theme.swift       the tokens, written a second time in SwiftUI
UncialSample/OcrModel.swift          the observable model over UncialBootstrap
UncialSample/ImagePickers.swift      camera and photo library
```

The five screens and the tokens mirror the Android sample deliberately, and **nothing is
shared** — this is a second implementation, in SwiftUI, of the same design. Keep them in
step when either changes.

## How It Builds

The project's **first build phase runs Gradle** to link `:dist:ios-framework` and stages
`Uncial.framework` into `samples/ios-app/build/framework`, which is exactly where
`FRAMEWORK_SEARCH_PATHS` points — so the search path needs no per-configuration conditionals
and nothing has to be built by hand first.

It stays on the direct framework build phase rather than on the Swift package: a binary
target is pinned to a published zip by checksum and cannot serve a development loop.

## Rules

- **Deployment target is iOS 15.** `NavigationStack` is iOS 16, so the app uses a plain
  `VStack`-based stack. Expect this class of paper cut for anything SwiftUI, and check the
  availability before using an API.
- **Vision has no Ukrainian before iOS 16**, so on 15 a Ukrainian document recognizes as
  Latin nonsense rather than failing. The Device screen exists to show what
  `capabilities` really reported — never hard-code a language list into the UI.
- **The Simulator recognizes nothing** (no Neural Engine): `performRequests` succeeds and
  returns no observations. The Simulator proves the UI; only a device proves recognition.
- **Call `UncialBootstrap.shared.start()` once at launch.** iOS has no `androidx.startup`.
- **Go through `UncialBootstrap`, not through the Kotlin types directly.** If something is
  awkward from Swift, that is a finding for `:dist:ios-framework` (and for the planned
  `:sdk:swift`), not something to work around here.
- **`DEVELOPMENT_TEAM` stays empty in the committed project.** A team ID belongs to a person,
  not a repository. Pass it on the command line for a device build, and revert
  `project.pbxproj` before committing — Xcode writes it back in.
- **Watch the disk.** Derived data is large and this build stages a whole static framework;
  pass `-derivedDataPath` somewhere you control and clean it up.

## Verify

```bash
cd samples/ios-app
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath /tmp/uncial-dd build
rm -rf /tmp/uncial-dd
```

Swap `-sdk iphoneos -destination 'generic/platform=iOS'` for a device. Both are verified.
