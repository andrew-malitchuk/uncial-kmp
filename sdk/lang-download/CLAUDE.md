# lang-download — Claude Instructions

## Module Purpose

A `LanguageDataProvider` that fetches `.traineddata` at runtime, verifies its SHA-256 and
caches it on disk — for consumers who cannot afford 3.8–4.1 MB per language in their
download size. Android + JVM only.

## Package Layout

```
source/provider/     DownloadingLanguageDataProvider (expect/actual), the factory
source/tessdata/     TessDataSource — URLs and checksums
core/provider/       JvmDownloadingLanguageDataProvider   (jvmAndAndroidMain — the real one)
core/verification/   DownloadVerification
core/http/           test stubs (jvmAndAndroidTest)
```

The whole download lives in the **`jvmAndAndroid` intermediate source set**: `HttpURLConnection`,
`java.io.File` and `MessageDigest` exist on both, and the code that verifies checksums is
exactly the code that must not exist twice. Declare source sets through
`applyDefaultHierarchyTemplate { }`, never with manual `dependsOn` — that conflicts with the
default template and warns on every build.

## Rules

- **No HTTP library.** A networking dependency inside an SDK is a version conflict waiting to
  happen, and this module makes exactly one kind of request. `HttpURLConnection` stays.
- **A checksum mismatch throws `OcrError.NoLanguageData`.** The bytes go straight into a
  native library; a mismatch is fatal, never a warning. An *unpinned* language is allowed —
  some consumers choose a language at runtime — but the provider says so through its logger.
- **Partial downloads go to `.part` files** and are never promoted before verification;
  `clearCache` removes them too, because a killed download leaves them behind.
- **The cache is guarded by a mutex**, and `clearCache` suspends because it takes the same
  lock as the download path.
- **`filesDir`, not `cacheDir`, on Android.** Re-downloading several MB after the OS
  reclaimed 4 MB is the worse trade.
- **No UI, no scheduling, no retry policy, no permission handling.** Downloading on a metered
  connection is a product decision: expose `isCached` and `prefetch` and let the app decide.
  Do not add a progress callback without an explicit product reason — it changes the
  provider contract.
- **`baseUrl` must be `https://` and end in `/`.** Enforced, not documented.

## Verify

```bash
./gradlew :sdk:lang-download:allTests        # stubbed connections, no network
./gradlew :samples:cli:run --args="download /tmp/scan.pdf --cache /tmp/td"   # the real network path
```
