# iOS sample

An Xcode project rather than a Gradle module. Its first build phase runs Gradle to link
`:dist:ios-framework` and stages `Uncial.framework` into `samples/ios-app/build/framework`,
which is where `FRAMEWORK_SEARCH_PATHS` points — so the search path needs no
per-configuration conditionals and nothing has to be built by hand first.

```bash
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

Swap in `-sdk iphoneos -destination 'generic/platform=iOS'` for a device.

## Signing

`DEVELOPMENT_TEAM` is deliberately empty in the committed project: a team ID belongs to a
person, not to a repository, and a hard-coded one is a signing failure for everyone else
who clones this.

The simulator needs no team. For a device, set yours **without committing it**:

```bash
xcodebuild -project UncialSample.xcodeproj -scheme UncialSample \
  -sdk iphoneos -destination 'generic/platform=iOS' \
  DEVELOPMENT_TEAM=YOURTEAMID build
```

In Xcode, pick your team under *Signing & Capabilities* and revert `project.pbxproj`
before committing (`git checkout -- UncialSample.xcodeproj/project.pbxproj`) — Xcode writes
the choice straight into the project file.

## What the sample proves

Vision text recognition returns **zero observations on the Simulator**: there is no Neural
Engine, `performRequests` succeeds, and `results` is empty. A green run in the Simulator
therefore says nothing about recognition quality — only a real device does. On hardware
this sample recognizes the bundled Ukrainian scan and reports `uk-UA+en-US`.

Note also that Vision has no Ukrainian before iOS 16, and the deployment target here is 15.
Where that bites, `UncialClient.capabilities` says so rather than hiding it.
