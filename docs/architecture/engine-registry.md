# Engine Registry

`OcrEngineRegistry` is the seam that lets `uncial-runtime` have no compile-time dependency
on any engine while still letting a consumer avoid writing platform-specific engine
selection code. Engine modules put themselves in; the runtime asks for whatever is there.

It lives in `uncial-core` — `io.github.andrewmalitchuk.uncial.core.source.engine` — and is a
process-global `object`.

## API

```kotlin
public object OcrEngineRegistry {
    public fun register(factory: OcrEngineFactory)
    public fun unregister(id: String)
    public fun engines(): List<OcrEngineFactory>
    public fun firstAvailable(): OcrEngineFactory?
    public fun lastProbeFailure(): Throwable?
}
```

| Member | Behaviour |
|---|---|
| `register` | Adds a factory, **replacing** any previously registered engine with the same `OcrEngineFactory.id`. Idempotent, so a module can call it from both an initializer and an explicit `install…()` without ending up registered twice. |
| `unregister` | Removes the engine with that id. Intended for tests. |
| `engines()` | Every registered factory, in registration order. |
| `firstAvailable()` | The first registered engine that reports itself usable here, or `null`. |
| `lastProbeFailure()` | The exception thrown by an `isAvailable()` probe during the **most recent** `firstAvailable()` call, or `null`. |

### Thread safety

Registration is lock-free: the list is immutable and swapped atomically through an
`AtomicReference` with a compare-and-set loop. It has to be — on Android an initializer
registers from the `androidx.startup` thread while application code may already be reading.

## How engines announce themselves

### Android — automatically

`uncial-engine-tesseract` ships a `TesseractEngineInitializer` in its own
`AndroidManifest.xml`, merged into the consumer's app as an `androidx.startup` initializer.
It declares `UncialContextInitializer` (from `uncial-core`) as a dependency, so the
application `Context` is captured before the engine that needs it is registered.

Adding the dependency is genuinely all that is required: no init call, no engine selection.
Registration only stores a factory — nothing loads `libtesseract` or reads `.traineddata`
until an extraction starts — so it costs no measurable startup time.

### Everywhere else — one call

```kotlin
installTesseractEngine()   // JVM (and Android apps that strip InitializationProvider)
installVisionEngine()      // iOS
```

Both are thin wrappers over `OcrEngineRegistry.register(...)` and both are idempotent. On
iOS, `UncialBootstrap.shared.start()` calls `installVisionEngine()` for Swift callers.

`installTesseractEngine` takes an optional `TesseractPageSegmentation` for the factory it
registers; engine-specific knobs live on the engine rather than in `OcrOptions`, which stays
generic across all three engines.

### Or bypass the registry entirely

```kotlin
val ocr = UncialClient {
    engine = FakeOcrEngine()   // or any OcrEngineFactory
}
```

An explicitly-set `engine` is used as-is and the registry is never consulted. This is what
tests do.

## How `UncialClient` resolves one

The builder captures a **provider**, not an engine:

```kotlin
engineProvider = { explicitEngine ?: OcrEngineRegistry.firstAvailable() }
```

Resolution is therefore lazy, and that is deliberate on two counts: client construction can
never fail (safe to do in an initializer or a DI graph), and an engine registered *after*
the client was built is still found.

The provider is consulted in two places:

- **`UncialClient.capabilities`** — `engineProvider()?.takeIf { it.isAvailable() }?.capabilities(options)`.
  Returns `null` when nothing resolves. `isAvailable` is just `capabilities != null`.
- **`obtainRecognizer()`**, on the first extraction. It runs under a `Mutex`, creates the
  recognizer once, and reuses it for the client's lifetime — creation loads the language
  model, which for `ukr+eng` is ~8 MB and takes real time, so doing it per page, or even per
  document, would dominate everything else.

### `firstAvailable()` in detail

```kotlin
public fun firstAvailable(): OcrEngineFactory? {
    lastFailure.store(null)
    return engines().firstOrNull { it.probe() }
}
```

- Engines are probed in **registration order**, and the first one whose `isAvailable()`
  returns `true` wins. There is no scoring or preference mechanism; register the engine you
  want first, or set it explicitly on the builder.
- `isAvailable()` is defined as "can this engine run here **at all**, checked without
  creating anything". Vision returns `true` unconditionally (it is part of the OS); the
  Android Tesseract factory requires both a loadable native library and an application
  `Context`; the JVM factory requires a `libtesseract` plus models.
- `lastProbeFailure` is cleared **before** probing, not after. The registry is
  process-global, so a failure left over from an unrelated earlier lookup would otherwise be
  attached as the cause of a later "no OCR engine available" whose real reason is that
  nothing is registered at all — blaming an engine the caller may not even have installed.

## When a factory throws while being probed

A factory that throws from `isAvailable()` is treated as **unavailable** rather than being
allowed to take the whole lookup down: one broken third-party engine must not hide a working
one registered after it.

The throwable is not discarded, though — it is stored, and `lastProbeFailure()` returns it
for the duration of that lookup. `UncialClient` attaches it as the `cause` of the error it
throws when nothing resolved:

```kotlin
throw OcrError.Unsupported(
    "no OCR engine available. Add uncial-engine-tesseract (Android/JVM) or " +
        "uncial-engine-vision (iOS), or pass one to the UncialClient builder. " +
        "Outside Android, call installTesseractEngine() / installVisionEngine() once.",
    cause = OcrEngineRegistry.lastProbeFailure(),
)
```

!!! note "It is a diagnostic, not control flow"
    `lastProbeFailure()` is scoped to one lookup so it always answers "why did the lookup I
    just performed come back empty" — a value kept indefinitely could not. Report it
    alongside the message; it is the only record of why an engine vanished. Do not branch on
    it.

## When no engine is available

Two distinguishable states, both surfacing as `OcrError.Unsupported`:

| State | Message |
|---|---|
| Nothing resolved — no engine module on the classpath, or the ones present cannot run | `no OCR engine available. Add uncial-engine-tesseract (Android/JVM) or uncial-engine-vision (iOS)…` |
| An engine resolved, then failed a second `isAvailable()` check inside `obtainRecognizer` | `engine '<id>' is present but not usable here. On the JVM this usually means no system libtesseract or no .traineddata…` |

Before either happens, `UncialClient.capabilities` is already `null` and
`UncialClient.isAvailable` is already `false` — which is the check to make before promising
a user an OCR feature. See [Capabilities](../platforms/capabilities.md).

## Registering your own engine

Implement `OcrEngineFactory`:

```kotlin
class MyEngineFactory : OcrEngineFactory {
    override val id: String = "my-engine"

    override fun isAvailable(): Boolean = /* cheap, no allocation, no I/O if avoidable */

    override fun capabilities(options: OcrOptions): OcrCapabilities =
        OcrCapabilities(engineId = id, /* … be honest here … */)

    override suspend fun create(
        options: OcrOptions,
        logger: OcrLogger,
        languageData: LanguageDataProvider?,
    ): TextRecognizer = MyRecognizer(/* … */)
}

OcrEngineRegistry.register(MyEngineFactory())
```

Three obligations worth spelling out:

- **`capabilities` must be cheap** — cheap enough to call for a UI capability check — and
  honest. It is what callers read instead of assuming uniformity.
- **`create` may throw `OcrError.EngineInit` or `OcrError.NoLanguageData`**, and the caller
  owns (and must close) the returned recognizer.
- **The recognizer must tolerate concurrent, re-entrant `recognize` calls.** One
  `UncialClient` creates a single recognizer and hands that same instance to every
  concurrent `extract`. Every bundled recognizer holds a single native handle and serializes
  internally with a mutex; anything similarly non-reentrant has to do the same.
