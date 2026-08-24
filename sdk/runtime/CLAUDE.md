# runtime — Claude Instructions

## Module Purpose

`UncialClient` — the single entry point a consumer calls. It orchestrates: open, decide
digital-vs-OCR per page, drive the engine, emit progress, map failures, return one document.

## Package Layout

```
source/client/     UncialClient, UncialClientBuilder, UncialClientFactory
source/progress/   OcrProgress
core/pipeline/     ExtractionPipeline   — the orchestration itself
core/error/        ErrorMapper, runCatchingOcr
core/fake/         test doubles (commonTest)
```

`UncialClient` is at `…uncial.runtime.source.client.UncialClient`, **not** at the package
root. It had to move: its internals would otherwise be `…uncial.core.*`, the package
`:sdk:core` already owns — a split package across two artifacts. Do not move it back.

## The One Rule That Matters

**Never add a dependency on an engine module.** Not `engine-tesseract`, not `engine-vision`,
not `engine-fake` outside `commonTest`. Engines are reached through `OcrEngineFactory`,
resolved from `OcrEngineRegistry` at first use. If something here needs to know an engine's
name, the design is wrong — ask the registry.

`pdf-text` is the same story: the contract (`DigitalTextExtractor`) lives in `core`, and the
consumer supplies the implementation. Do not wire it by default; it costs several MB of
PDFBox-Android to an app that may only OCR photographs.

## Rules

- **`kotlin.runCatching` is banned.** It swallows `CancellationException`. Use
  `runCatchingOcr`.
- **Building never throws.** A missing or unusable engine surfaces at the first `extract` as
  `OcrError.Unsupported`, so a client is safe to construct in an initializer or a DI graph.
  Keep it that way.
- **Every failure a consumer sees is an `OcrError`.** Raw platform exceptions are mapped in
  `core/error/ErrorMapper`; add a case there rather than letting one escape.
- **`extractAsFlow` throws rather than emitting a failure,** and checks the coroutine's state
  at every page boundary. Progress order is `Started` → `Page`* → `Done`, with `Started`
  carrying the total so a progress bar can exist before page 1.
- **One recognizer serves concurrent pages.** The client creates it once; do not create one
  per page "for safety" — that is a native handle per page.
- **`close()` is idempotent, blocking, and safe during an extraction.** All three properties
  are load-bearing; changing any of them is an API-behaviour change worth documenting.
- **No DI framework.** `UncialClientBuilder` is the composition root, hand-written on
  purpose: a container inside a library is a dependency forced on the consumer.

## Verify

```bash
./gradlew :sdk:runtime:allTests :sdk:runtime:checkKotlinAbi
```

Tests drive a real client through `engine-fake`, including cancellation and progress — add
to those rather than mocking the pipeline.
