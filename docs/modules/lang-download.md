# lang-download

Fetches `.traineddata` at runtime instead of shipping it, for consumers who cannot afford 3.8–4.1 MB per language in their download size.

## Features

- **No HTTP dependency.** It uses `HttpURLConnection`, which both targets already have — a networking library inside an SDK is a version conflict waiting to happen, and this module makes exactly one kind of request.
- **Checksums are part of the source, not an afterthought**: a `.traineddata` goes straight into a native library, so a mismatch is fatal rather than a warning.
- **Cached on disk**, with partial downloads written to `.part` files and the cache guarded by a mutex.
- Targets: android + jvm, with the whole download implementation shared through a `jvmAndAndroid` intermediate source set. iOS needs no language data at all.
- Depends on `core` (`api`) only.

## Core Components

### `DownloadingLanguageDataProvider`

A `LanguageDataProvider` with three additions:

- `isCached(language): Boolean` — whether a model is already on disk, so no network call is needed.
- `suspend prefetch(languages): List<OcrLanguage>` — downloads now so the first extraction does not pay for it. Best-effort: failures are logged, not thrown.
- `suspend clearCache(): Int` — deletes cached models and any `.part` files a killed download left behind, and returns how many files were removed. Suspending because it takes the same lock the download path does.

Created with `downloadingLanguageDataProvider(source, cacheDirectoryPath, logger)`. The default cache directory is `filesDir/uncial-tessdata` on Android — deliberately `filesDir` and not `cacheDir`, because re-downloading several MB after the OS reclaimed 4 MB is the worse trade — and a per-user directory under the system temp dir on the JVM.

### `TessDataSource`

- `baseUrl`: required to be `https://` and to end in `/`. Defaults to `TESSDATA_FAST_BASE_URL`, the same `tessdata_fast` repository the `uncial-lang-*` artifacts are built from; `TESSDATA_BASE_URL` points at the full models (12 MB for `ukr`, 23 MB for `eng`).
- `checksums`: language to hex SHA-256, defaulting to `TessDataSource.DEFAULT_CHECKSUMS`, which pins Ukrainian and English. Case and surrounding whitespace are ignored, so a hash pasted out of `sha256sum` or a CI log works as-is.
- `urlFor(language)`, `checksumFor(language)`.

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

!!! warning "It will not ask the user first"
    No progress reporting, no background scheduling, no retry policy. Downloading several MB
    silently on a metered connection is a product decision, not a library one — check
    `isCached` and drive `prefetch` yourself if your users should be asked.

!!! warning "An unpinned language is trusted on HTTPS alone"
    Leaving a language out of `checksums` is allowed — some consumers genuinely cannot pin a
    hash for a model chosen at runtime — but the provider says so through its logger, and a
    pinned checksum that does not match throws `OcrError.NoLanguageData` rather than letting
    the bytes reach the native layer.
