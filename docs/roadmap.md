# Roadmap

`PLAN.md` in the repository root is the design document and decision log. This page is the
short version: what works today, what is missing, and what has not been decided.

## Status

Uncial recognizes text on all three platforms today, and that has been verified on real
hardware rather than only in CI:

| Platform | Verified against |
|---|---|
| JVM | `samples/cli`, against a generated image-only Ukrainian PDF |
| Android | the sample on a Pixel 8: the scanned fixture at 4 pages, 31 lines, 207 words, 95 % confidence in ~2.0 s; the digital fixture from the text layer in ~0.1 s |
| iOS | the sample on a device, Vision reporting `uk-UA+en-US` |

!!! warning "Prepared, not released"
    Publishing is wired end to end — signed artifacts for all twelve modules, a single
    aggregated Central Portal bundle, an XCFramework and a generated `Package.swift` — but
    nothing has been uploaded. No Maven Central deployment, no GitHub Release. Consuming
    Uncial today means building from source or publishing to `mavenLocal`. See
    [Installation](getting-started/installation.md).

## Phases

The phase breakdown from `PLAN.md` §10, with current state:

| Phase | Work | State |
|---|---|---|
| 0. Scaffolding | `settings.gradle.kts`, version catalog, `build-logic` convention plugins, `explicitApi` + ABI dumps | Done |
| 1. Lift and shift | move the engine code out of the POC into modules | Done |
| 2. Re-contract | suspend + `Flow`, sealed errors, richer model, `Rasterizer`/`Recognizer` split, constants into `OcrOptions` | Done |
| 3. Corpus | golden PDFs + a character-error-rate harness | **Not started** |
| 4. Language data | separate `lang-*` artifacts, `androidx.startup`, runtime downloader | Done |
| 5. Distribution | Maven Central, Fat AAR, XCFramework, SPM | **In progress** — Maven and SPM machinery built and verified locally, nothing uploaded; the Fat AAR is not started |
| 6. Polish | Dokka, README, CHANGELOG, samples against published artifacts | **In progress** — these docs |

The absence of a "write the engines" phase is deliberate: that code was lifted from a
working proof of concept rather than written fresh.

## What is missing

**A golden corpus (phase 3).** There is currently no measurement of recognition *quality* —
only that the pipeline runs. Tests assert structure, geometry and cancellation behaviour,
not character error rate. A green test run says the plumbing works, not that the output is
good.

**A release (phase 5).** The machinery is built: `uncial.publish` signs every publication
with a real GPG key, `publishToMavenLocal` produces 58 verified coordinates, one aggregated
bundle uploads to the Central Portal, and `updateSwiftPackage` assembles the XCFramework and
writes its checksum into `Package.swift`. What is missing is the part that cannot be
rehearsed: a consumer smoke test against the published artifacts, a GitHub repository for
the POM to point at, and the two irreversible clicks that follow. `PUBLISHING.md` in the
repository root is the step-by-step list.

**The Fat AAR (phase 5).** Not started, and it is the reason an Android consumer still has
to add the JitPack repository by hand. `PLAN.md` §8 covers why it is genuinely needed here
rather than merely convenient.

**Dokka.** The version is in the catalog; the plugin is not applied. Public KDoc is written
and kept in English for this reason, but no API reference is generated yet.

## Open questions

Most of `PLAN.md` §11 is now settled: `pdf-text` and image input are both in v1, the
`lang-ukr` / `lang-eng` / `lang-download` artifacts all exist, and the floors are minSdk 24
and iOS 15. What remains open:

**Publishing the JVM artifact.** It is not self-contained — it needs a system
`libtesseract` — which makes it a poor thing to hand an unsuspecting consumer. It is also
the only practical way to run a corpus in CI. The leaning is to always build it and publish
it with an honest disclaimer.

**Whether to build a `-slim` variant** now, or wait for the first complaint about artifact
size.

**Whether iOS 15 is the right floor.** Vision has no Ukrainian before iOS 16, so on iOS 15
a Ukrainian document does not fail — it recognizes as Latin nonsense. The SDK reports this
honestly through [capabilities](platforms/capabilities.md), but reporting a bad outcome is
not the same as preventing it, and the floor itself is a product decision that has not been
revisited since it was first set.
