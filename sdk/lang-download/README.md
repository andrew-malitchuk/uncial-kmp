# lang-download

> Fetches `.traineddata` at runtime instead of shipping it, for consumers who cannot afford 3.8–4.1 MB per language in their download size.

Gradle `:sdk:lang-download` · Maven `io.github.andrew-malitchuk:uncial-lang-download` · [full docs](../../docs/modules/lang-download.md)

## Responsibility

A `LanguageDataProvider` that downloads a model on first use, verifies its SHA-256, and
caches it on disk. Checksums are part of the source rather than an afterthought: a
`.traineddata` goes straight into a native library, so a mismatch is fatal, not a warning.

Android + JVM only — iOS needs no language data at all. The whole download implementation is
shared through a `jvmAndAndroid` intermediate source set, because `HttpURLConnection`,
`java.io.File` and `MessageDigest` exist on both and the code that verifies checksums is
exactly the code that must not exist twice.

## Layout

```
source/provider/   DownloadingLanguageDataProvider (expect/actual), downloadingLanguageDataProvider
source/tessdata/   TessDataSource
core/provider/     JvmDownloadingLanguageDataProvider    (jvmAndAndroidMain)
core/verification/ DownloadVerification
```

## Dependencies

`api(:sdk:core)` only. **No HTTP library** — a networking dependency inside an SDK is a
version conflict waiting to happen, and this module makes exactly one kind of request.

## Public API

| Declaration | Notes |
|---|---|
| `downloadingLanguageDataProvider(source, cacheDirectoryPath, logger)` | the `expect`/`actual` factory |
| `isCached(language)` | whether a model is already on disk, so no network call is needed |
| `suspend prefetch(languages)` | download now so the first extraction does not pay for it; best-effort, failures are logged |
| `suspend clearCache()` | deletes cached models and any `.part` files a killed download left behind; returns the count |
| `TessDataSource(baseUrl, checksums)` | `baseUrl` must be `https://` and end in `/`; `urlFor`, `checksumFor` |
| `TESSDATA_FAST_BASE_URL` / `TESSDATA_BASE_URL` | fast models (the same bytes the `lang-*` artifacts bundle) and the full ones |
| `TessDataSource.DEFAULT_CHECKSUMS` | pins Ukrainian and English; case and whitespace are ignored, so a hash pasted from `sha256sum` works as-is |

The default cache directory is `filesDir/uncial-tessdata` on Android — deliberately `filesDir`
and not `cacheDir`, because re-downloading several MB after the OS reclaimed 4 MB is the worse
trade — and a per-user directory under the system temp dir on the JVM.

## Usage

```kotlin
val client = UncialClient {
    languageData = downloadingLanguageDataProvider(logger = myLogger)
}
```

Chain it behind a bundled or system provider to prefer what is already there:

```kotlin
languageData = SystemTessDataProvider() + downloadingLanguageDataProvider()
```

## Known behaviours and pitfalls

- **It will not ask the user first.** No progress reporting, no background scheduling, no
  retry policy. Downloading several MB silently on a metered connection is a product
  decision, not a library one — check `isCached` and drive `prefetch` yourself.
- **An unpinned language is trusted on HTTPS alone.** Leaving one out of `checksums` is
  allowed, and the provider says so through its logger; a pinned checksum that does not match
  throws `OcrError.NoLanguageData` rather than letting the bytes reach the native layer.
- **The intermediate source set is declared through `applyDefaultHierarchyTemplate { }`**, not
  with manual `dependsOn` calls, which conflict with the default template and warn on every
  build.

## Build

```bash
./gradlew :sdk:lang-download:allTests
./gradlew :samples:cli:run --args="download /tmp/scan.pdf"   # the real network path, into an empty cache
```
