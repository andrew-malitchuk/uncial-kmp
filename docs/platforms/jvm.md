# JVM

The JVM target runs Tesseract through Tess4J and rasterizes with PDFBox. It has the richest
capability surface of the three platforms — and it is the one artifact that is deliberately
**not** self-contained.

## It needs a system Tesseract

The JVM engine binds to the operating system's `libtesseract`. That is an acceptable trade
for a JVM artifact — but it has to be documented, and `OcrEngineFactory.isAvailable()` has
to be honest about it, because the failure otherwise surfaces as an `UnsatisfiedLinkError`
from inside JNA.

```bash
brew install tesseract tesseract-lang            # macOS
apt-get install tesseract-ocr tesseract-ocr-ukr  # Debian / Ubuntu
```

`isAvailable()` returns `true` only when both halves are present:

1. a `libtesseract` in a directory the SDK knows to look in, **and**
2. models — either a system `tessdata` directory, or a `tessdata/` resource on the
   classpath (which is what the `uncial-lang-*` artifacts ship).

If it returns `false`, `UncialClient.capabilities` is `null` and every `extract` fails with
`OcrError.Unsupported` rather than crashing in native code.

### Finding the native library

Tess4J reaches `libtesseract` through JNA, which searches the system loader path plus
`jna.library.path`. Homebrew installs into `/opt/homebrew/lib`, which is in neither — so on
a stock Apple Silicon Mac, Tess4J fails to load a library that is plainly installed.

`JnaLibraryPath` fixes that by scanning a fixed list of directories and appending the ones
that actually contain a `libtesseract` to `jna.library.path`, preserving anything already
set:

```
/opt/homebrew/lib
/usr/local/lib
/usr/lib
/usr/lib/x86_64-linux-gnu
/usr/lib/aarch64-linux-gnu
```

Library names recognized: `libtesseract.dylib`, `libtesseract.5.dylib`, `libtesseract.so`,
`libtesseract.so.5`, `tesseract.dll`.

If your installation is somewhere else, set `-Djna.library.path=...` yourself — the existing
value is merged, never replaced. The `EngineInit` error names the directories that were
searched.

## Registering the engine

There is no `androidx.startup` off Android, so call this once — from `main`, a DI module, or
a test fixture:

```kotlin
installTesseractEngine()                                      // Auto page segmentation
installTesseractEngine(TesseractPageSegmentation.SparseText)  // or pick a mode
```

Idempotent. `tesseractEngine(pageSegmentation)` returns the factory directly if you would
rather pass it to the `UncialClient` builder explicitly.

## Where language data comes from

The JVM default is a **chain**:

```kotlin
public actual fun defaultTessDataProvider(): LanguageDataProvider =
    SystemTessDataProvider() + ClasspathTessDataProvider()
```

### `SystemTessDataProvider`

Looks for a directory whose `tessdata/` subdirectory holds at least one `.traineddata`, in
this order:

1. any `extraSearchPaths` passed to the constructor
2. `$TESSDATA_PREFIX` — and, if that points straight at a `tessdata` directory, its parent
   as well
3. `/opt/homebrew/share`
4. `/usr/local/share`
5. `/usr/share`
6. `/usr/share/tesseract-ocr/5`
7. `/usr/share/tesseract-ocr/4.00`

!!! note "`TESSDATA_PREFIX` has meant two different things"
    Across Tesseract versions it has meant both "the parent of `tessdata/`" and "`tessdata/`
    itself", and Homebrew sets it to neither. Both shapes are accepted and normalized to the
    parent, which is what Tesseract's `datapath` wants.

### `ClasspathTessDataProvider`

This is how the `uncial-lang-*` artifacts work on the JVM: the model ships as a resource
under `tessdata/`, and Tesseract cannot read a resource — it needs a real directory. So the
model is unpacked once and reused.

**Cache location:** `~/.cache/uncial/tessdata-<user>` by default, where `<user>` is
`user.name` with everything outside `[A-Za-z0-9_.-]` replaced by `_`. If `user.home` is
unset the fallback is `java.io.tmpdir`, then the working directory. Pass a `File` to the
constructor to choose your own.

The directory is created with mode `rwx------` in the same call that creates it, and if it
already exists and belongs to **another user** the provider refuses it with
`OcrError.EngineInit` rather than handing its contents to libtesseract in native code. (The
old predictable `/tmp` path let another account plant a `.traineddata`.) On Windows, which
has no POSIX permissions, the per-user home directory carries that guarantee instead.

Extraction is compared by **size**, not timestamp — an upgraded artifact ships a different
model under the same name — and goes through a temp file plus a move, so a crashed or
concurrent run cannot leave a half-written model that looks complete.

### How the chain resolves

```kotlin
public operator fun LanguageDataProvider.plus(
    fallback: LanguageDataProvider,
): LanguageDataProvider
```

- `available()` is the **union** of both providers' answers.
- `materialize()` picks the provider covering the **most** of the requested languages,
  earlier providers winning ties, and falls forward to the next candidate if one fails.
  Tesseract takes exactly one `datapath`, so the languages cannot be split across providers.

Net effect: a developer machine with `brew install tesseract-lang` keeps using its own
models; a server with only the JAR falls back to the ones `uncial-lang-*` ships.

!!! note "Two datapath conventions"
    `LanguageDataProvider.materialize` returns the **parent** of `tessdata/`, which is what
    the Tesseract4Android wrapper wants. Tess4J hands the path straight to libtesseract 5,
    which treats it as the `tessdata` directory itself — so the JVM engine descends one
    level before calling in. If you write your own provider, follow the documented contract
    (return the parent); the engine does the adapting.

## The richest capability surface

The JVM engine talks to libtesseract's C API directly rather than going through Tess4J's
high-level wrapper, and that API reports things the other two bindings do not:

| | words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|---|
| **Tesseract (JVM)** | yes | yes | **yes** | **yes** | **yes** |
| Tesseract (Android) | yes | yes | no | no | no |
| Vision (iOS) | yes | yes | no | no | no |

So on the JVM, `OcrLine.language`, `OcrLine.skewDegrees` and `OcrPage.orientation` carry
real measured values. This is a property of the *binding*, not of Tesseract: the Android
wrapper's `ResultIterator` simply does not expose them. See
[Capabilities](capabilities.md).

Two consequences:

- The JVM is the practical place to run a golden corpus. Android and the JVM run the same
  engine over the same `.traineddata`, so results agree — but only the JVM can be driven
  from a plain test.
- `RecognitionQuality` is **ignored** by both Tesseract engines. Their only equivalent is
  `OEM_TESSERACT_LSTM_COMBINED`, which needs legacy data that neither `tessdata_fast` nor
  `tessdata_best` ships, so asking for it fails at init instead of running faster.

!!! warning "Do not route the JVM engine back through Tess4J's `getWords`"
    That path goes via `lept4j`, whose bundled bindings are pinned to a Leptonica ABI, and
    it dies with `symbol not found: pixFindBaselinesGen` against Homebrew's Leptonica 1.85.
    The direct C API call is deliberate.

## Running OCR from the command line

`:samples:cli` is the harness that exercises recognition end to end on a developer machine:

```bash
./gradlew :samples:cli:run --args="capabilities"
./gradlew :samples:cli:run --args="fixture /tmp/scan.pdf --pages 6"
./gradlew :samples:cli:run --args="ocr /tmp/scan.pdf --words"
```

`fixture` generates a **scanned** (image-only) Ukrainian PDF on purpose: a normally
generated PDF carries its own text, so Uncial takes the digital fast path and proves nothing
about recognition.

## Other notes

- **Compiled for Java 11.** The SDK compiles on JDK 21 and targets bytecode 11, so consumers
  on Java 11 can use the JVM artifact.
- **`Raster.release()` is a no-op** on the JVM: the GC reclaims a `BufferedImage` like any
  other object. Build one from `ImageIO` output with `Raster(image, sourceDpi)` to recognize
  an image with no PDF involved.
- **`close()` blocks** here too — the recognizer takes a blocking lock around its native
  calls, so closing waits out the native call currently executing.
- **Publishing the JVM artifact is still an open question** (`PLAN.md` §11); nothing is
  published yet, not even to `mavenLocal`.
