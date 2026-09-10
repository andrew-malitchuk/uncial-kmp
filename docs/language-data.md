# Language data

Tesseract needs a `.traineddata` model per language. Apple Vision does not — iOS ships its
models with the OS — so **everything on this page is about Android and the JVM only**.

Uncial gives you three ways to supply models, and one interface to plug in a fourth.

## 1. Bundle it: `uncial-lang-*`

```kotlin
implementation("io.github.andrew-malitchuk:uncial-lang-ukr:<version>")
implementation("io.github.andrew-malitchuk:uncial-lang-eng:<version>")
```

Nothing else to do. On Android the artifact's assets merge into your APK and the engine
finds them; on the JVM they are classpath resources and get unpacked to a cache directory
on first use.

Cost: **3.8 MB (ukr) + 4.1 MB (eng)** in your download size.

Those are `tessdata_fast` models — the same bytes the downloader fetches. There is no
smaller variant: the full `tessdata` models for these languages are 12 MB and 23 MB.

## 2. Use what the machine already has (JVM)

The JVM engine's default provider looks in `TESSDATA_PREFIX`, `/opt/homebrew/share`,
`/usr/local/share`, `/usr/share` and the usual Linux package paths. So on a developer
machine or a CI runner:

```bash
brew install tesseract tesseract-lang      # macOS
apt-get install tesseract-ocr tesseract-ocr-ukr   # Debian/Ubuntu
```

…and no `uncial-lang-*` dependency is needed. This is what makes the JVM the practical
place to run a golden corpus.

The default chains both: system install first, bundled artifacts second.

## 3. Download at runtime: `uncial-lang-download`

```kotlin
implementation("io.github.andrew-malitchuk:uncial-lang-download:<version>")
```

```kotlin
val languageData = downloadingLanguageDataProvider(logger = myLogger)

val client = UncialClient {
    languages = listOf(OcrLanguage.Ukrainian, OcrLanguage.English)
    this.languageData = languageData
}
```

The model arrives the first time OCR runs and is cached on disk afterwards
(`filesDir/uncial-tessdata` on Android). Trade the 7.9 MB in your artifact for a one-time
download.

Prefer what is already present, and fall back to fetching:

```kotlin
languageData = SystemTessDataProvider() + downloadingLanguageDataProvider()
```

### Do not download silently

`downloadingLanguageDataProvider` has no progress reporting, no retry policy and no
scheduling, on purpose: pulling several MB on someone's metered connection is a product
decision. Drive it yourself when that matters:

```kotlin
if (!languageData.isCached(OcrLanguage.Ukrainian) && askedTheUser()) {
    languageData.prefetch(listOf(OcrLanguage.Ukrainian))
}
```

### Verification

Downloaded models are checked against a pinned SHA-256 before they are written to the
cache; a mismatch throws `OcrError.NoLanguageData` and the file is discarded. The models
Uncial ships support for are pinned by default.

If you point `TessDataSource` somewhere else, **pin your own checksums**:

```kotlin
downloadingLanguageDataProvider(
    source = TessDataSource(
        baseUrl = "https://models.example.com/tessdata/",
        checksums = mapOf(OcrLanguage.custom("pol") to "…"),
    ),
)
```

With no pinned checksum the only protection is HTTPS, and the provider logs a warning
saying so. `.traineddata` is input to a native library — treat it accordingly. Plain
`http://` is rejected outright.

Two limits worth knowing: a model is verified when it is downloaded, not on every later
read, so a cache corrupted afterwards is trusted (call the suspending `clearCache()` if you suspect that);
and the cache lives in application-private storage, which is what makes that acceptable.

## 4. Bring your own

Any language Tesseract has a model for works, shipped however you like.

```kotlin
val polish = OcrLanguage.custom("pol")            // the .traineddata basename

val client = UncialClient {
    languages = listOf(polish)
    languageData = MyProvider()
}
```

```kotlin
class MyProvider : LanguageDataProvider {

    override suspend fun available(requested: List<OcrLanguage>): List<OcrLanguage> =
        requested.filter { modelExists(it) }

    // Must return the PARENT of a `tessdata/` directory -- Tesseract's own datapath
    // convention, quirk included:
    //   returning "/data/.../files" means "/data/.../files/tessdata/pol.traineddata" exists.
    override suspend fun materialize(languages: List<OcrLanguage>): String =
        unpackInto(File(context.filesDir, "tessdata")).parent
}
```

`OcrLanguage.custom` takes an optional BCP-47 tag as its second argument. Supply it only if
Vision can recognize that language; without one, the iOS engine drops the language rather
than failing, and reports the reduced set through `UncialClient.capabilities`.

## What actually got used

Never assume the languages you asked for are the languages you got. A missing
`uncial-lang-*` artifact, a machine without `tesseract-lang`, or iOS 15 (which has no
Ukrainian in Vision) all narrow the set silently — by design, because failing a 300-page
job over one language is usually worse.

```kotlin
val capabilities = client.capabilities
if (capabilities == null) {
    // No usable engine at all.
} else if (OcrLanguage.Ukrainian !in capabilities.languages) {
    warnUser()
}
```

A model that is missing entirely surfaces as `OcrError.NoLanguageData`, whose
`searchedPath` says where Uncial looked.
