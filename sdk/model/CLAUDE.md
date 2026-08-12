# model — Claude Instructions

## Module Purpose

The SDK's vocabulary: the types that appear in the signature of every other module. Kotlin
stdlib only. Read `README.md` for what the types are; this file is about changing them.

## Package Layout

```
source/{geometry,text,structure,options,language,error}/
```

No `core/` — this module has no internal declarations, and should not grow any. A type here
that nobody outside the module uses does not belong here.

## What Belongs Here vs `core`

| Belongs in `model` | Belongs in `core` |
|---|---|
| Data: `OcrPage`, `BoundingBox`, `Confidence` | Behaviour: `TextRecognizer`, `PageRasterizer` |
| Configuration the caller writes: `OcrOptions` | Configuration an engine reports: `OcrCapabilities` |
| `OcrError` — the closed failure set | The registry, the logger, the `Raster` expect class |

If a new type needs `suspend`, a coroutine type, a file, or a platform API, it is not a model
type. Move it to `core`.

## Rules

- **No dependencies. Ever.** Not coroutines, not serialization, not `kotlinx-io`. The whole
  point of this module is that everything can depend on it without inheriting anything.
- **Every change here is an ABI change for every module.** Run `./gradlew updateKotlinAbi`
  and read the diff before committing; a package rename also rewrites the value-class
  mangling suffixes (`component3-Wf2TQyI` → `component3-CXFpPdI`), which is expected.
- **Validate in `init`, do not fail later.** `OcrOptions` rejects an out-of-range dpi and a
  reversed `pageRange` at construction, because the alternative is producing nothing and
  saying nothing. New options follow that.
- **Do not turn `OcrLanguage` into an enum.** It is a class precisely so bring-your-own
  `.traineddata` stays possible. Custom codes stay restricted to `[A-Za-z0-9_-]{1,32}`,
  because the code becomes a filename and a URL segment.
- **Adding an `OcrError` case or a `DocBlock` subtype is a breaking change for exhaustive
  `when`s.** Both hierarchies are documented as growing, and callers are told to write an
  `else` — keep that documented on the type itself.
- **Do not add an `OcrError.Cancelled`.** Cancellation is `CancellationException`, unwrapped,
  or structured concurrency stops working.
- **Derived state stays derived.** `OcrDocument.text`, `.lines`, `.pageCount` are computed
  from `pages`; do not cache them into constructor parameters, and remember that
  `copy(pages = …)` does not re-run the `source` default.

## Verify

```bash
./gradlew :sdk:model:allTests :sdk:model:checkKotlinAbi
```
