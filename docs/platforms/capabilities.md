# Capabilities

Uncial runs Tesseract on Android and the JVM and Apple Vision on iOS. The three are not
interchangeable: they populate different fields, their confidences are not comparable, and
their language sets differ. The SDK does not paper over that — it reports it.

```kotlin
val ocr = UncialClient { languages = listOf(OcrLanguage.Ukrainian) }

when (val caps = ocr.capabilities) {
    null -> showUnavailable()          // no engine, or the one present cannot run here
    else -> {
        if (!caps.perLineLanguage) hideLanguageColumn()
        if (caps.droppedAnyOf(ocr.options.languages)) warnAboutDroppedLanguages()
    }
}
```

## `UncialClient.capabilities`

The property is `OcrCapabilities?`. It is computed on every read from the resolved engine
factory — `engineProvider()?.takeIf { it.isAvailable() }?.capabilities(options)` — so it
reflects the engine that is registered *now*, with *this* client's `OcrOptions`.

`null` means one of two things: no engine module is on the classpath, or the engine that is
present cannot run here (a JVM without a system `libtesseract`, for example). In that state
every `extract` call fails with `OcrError.Unsupported`. `UncialClient.isAvailable` is
exactly `capabilities != null`.

!!! note "This is a cheap query, but not a free one"
    `capabilities` calls `OcrEngineFactory.isAvailable()`, and on Android that probe
    triggers `TessBaseAPI`'s `System.loadLibrary` the first time. The result is cached in
    the factory, but the first read should not happen on the main thread.

## Fields of `OcrCapabilities`

| Field | Type | Meaning |
|---|---|---|
| `engineId` | `String` | Stable identifier — `tesseract` (`TESSERACT_ENGINE_ID`) or `vision` (`VISION_ENGINE_ID`). |
| `engineVersion` | `String?` | The engine's own version when it reports one. Tesseract returns `TessVersion()` / `TessBaseAPI.version`; Vision returns `vision-<revision>`. `null` when it could not be read. |
| `languages` | `List<OcrLanguage>` | The languages actually available — the request intersected with what this engine and its data can do. May be **smaller** than `OcrOptions.languages`. |
| `wordLevel` | `Boolean` | Per-word boxes are available when `OcrOptions.includeWords` is set. |
| `confidence` | `Boolean` | Lines and words carry a real `Confidence`, not `Unknown`. |
| `perLineLanguage` | `Boolean` | `OcrLine.language` is populated. |
| `orientationDetection` | `Boolean` | `OcrPage.orientation` is measured rather than assumed to be `Orientation.Up`. |
| `skewDetection` | `Boolean` | `OcrLine.skewDegrees` is measured rather than left at zero. |

`OcrCapabilities` also carries one helper:

```kotlin
public fun droppedAnyOf(requested: List<OcrLanguage>): Boolean
```

`true` when one or more requested languages had to be dropped — the single call that answers
"did the user's language choice actually take effect?".

## The matrix

| | words | confidence | per-line language | orientation | skew |
|---|---|---|---|---|---|
| Tesseract (JVM) | yes | yes | **yes** | **yes** | **yes** |
| Tesseract (Android) | yes | yes | no | no | no |
| Vision (iOS) | yes[^1] | yes | no | no | no |

[^1]: Derived, not native — see below.

### Why the JVM column is richer

Both Tesseract columns are the *same engine* reading the *same* `.traineddata`. The
difference is the binding:

- The **JVM** engine talks to libtesseract's C API directly. That API reports the
  recognition language, the orientation and the deskew angle per element, so
  `JvmTesseractRecognizer` reads `element.languageCode`, `element.orientation` and
  `element.skewDegrees` and the factory reports `perLineLanguage`,
  `orientationDetection` and `skewDetection` as `true`.
- The **Android** engine goes through the Tesseract4Android Java wrapper, whose
  `ResultIterator` exposes text, a bounding box and a confidence — and nothing else. The
  data exists in the native layer; the wrapper does not surface it. So the Android factory
  reports all three as `false`.

This is a real asymmetry between two bindings of one engine, and it is precisely the kind
of thing `OcrCapabilities` exists to state rather than hide.

Where `perLineLanguage` is `false`, use `OcrLine.script` instead: it is derived from the
recognized text, so it is always available on every platform.

### Vision's word boxes are derived

Vision has no word-level results. What it has is
`VNRecognizedText.boundingBoxForRange`, which maps a substring of the recognized line back
onto the image. `VisionRecognizer` splits each line on whitespace and queries the box for
every span, which is why iOS reports `wordLevel = true` and matches the Tesseract engines.

Two consequences worth knowing:

- A span whose box Vision declines to compute keeps its word, with `BoundingBox.Zero`.
  Dropping the word would silently lose text.
- Vision reports confidence per **line**, not per word. Each word repeats its line's
  `Confidence` — more honest than inventing a per-word number or claiming `Unknown`.

## Vision's languages are a per-request answer, not a static list

This is the subtlest item on the page.

`VNRecognizeTextRequest.supportedRecognitionLanguages` is an instance method, and Apple's
own header says a language supported at one recognition level might not be available at
another. Ukrainian is one of those: Vision offers `uk-UA` at the **accurate** level only.

So the engine's capability probe builds a throwaway `VNRecognizeTextRequest` configured
*exactly* as the real request will be — same `recognitionLevel`, derived from
`OcrOptions.recognitionQuality` — and asks that. Probing a differently-configured request
would report `uk-UA` as available under `RecognitionQuality.Fast`, and the extraction would
then quietly recognize Latin.

!!! warning "`RecognitionQuality.Fast` genuinely offers fewer languages on iOS"
    Switching a client from `Accurate` to `Fast` can change `capabilities.languages`. Read
    the capabilities of the client you are about to use, not of a default one.

Two further Vision-specific rules:

- **No Ukrainian before iOS 16.** Vision gained it in text-recognition revision 3. On
  iOS 15 the request does not offer `uk-UA`, and a Ukrainian document comes back as Latin
  nonsense rather than failing. `capabilities.languages` is where that shows up.
- **If the language query itself fails**, the engine passes the requested languages
  through unchanged (minus any with a `null` `bcp47`) rather than filtering against an
  empty set. Filtering against nothing would hand Vision an empty
  `recognitionLanguages`, which is an undeclared fallback to `en-US`.

## Language availability is engine-specific too

`OcrLanguage` carries both identifiers because the engines disagree: Tesseract keys off a
`.traineddata` basename (`tesseractCode`, e.g. `ukr`), Vision off a BCP-47 tag (`bcp47`,
e.g. `uk-UA`). A language with a `null` `bcp47` is skipped by the iOS engine rather than
failing the request.

On the Tesseract side, `OcrEngineFactory.capabilities(options)` is deliberately
**optimistic**: it reports what the engine *would* do with these options, because knowing
which models are really present needs I/O. The authoritative answer arrives after the
recognizer is created — the factory copies the resolved set into the recognizer's own
`TextRecognizer.capabilities`:

```kotlin
capabilities(options).copy(languages = usable)
```

`UncialClient.capabilities` reads the factory, so treat its `languages` on
Android/JVM as "requested and plausible", and a `NoLanguageData` failure at extraction time
as the real verdict. See [Language data](../language-data.md).

## Read them; do not assume uniformity

The practical rule the SDK is built around:

- Check `capabilities != null` before promising the user an OCR feature at all.
- Check `capabilities.languages` (or `droppedAnyOf`) before promising a *language*.
- Check `perLineLanguage` / `orientationDetection` / `skewDetection` before rendering a UI
  column that depends on them — on two of the three platforms they are empty.
- Do not compare a confidence produced by Tesseract against one produced by Vision. Both
  are real; they are not the same scale.
