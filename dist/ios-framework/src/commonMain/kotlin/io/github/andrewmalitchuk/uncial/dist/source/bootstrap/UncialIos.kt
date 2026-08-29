package io.github.andrewmalitchuk.uncial.dist.source.bootstrap

import io.github.andrewmalitchuk.uncial.runtime.source.client.UncialClient
import io.github.andrewmalitchuk.uncial.core.source.engine.OcrEngineRegistry
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.dist.core.client.buildIosClient
import io.github.andrewmalitchuk.uncial.dist.core.extraction.collectDocument
import io.github.andrewmalitchuk.uncial.dist.core.interop.toByteArray
import io.github.andrewmalitchuk.uncial.dist.core.interop.upright
import io.github.andrewmalitchuk.uncial.dist.core.summary.toIosSummary
import io.github.andrewmalitchuk.uncial.dist.source.model.IosDocumentSummary
import io.github.andrewmalitchuk.uncial.dist.source.model.IosRunProgress
import io.github.andrewmalitchuk.uncial.engine.vision.source.install.installVisionEngine
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.language.OcrLanguage
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.structure.DocBlock
import io.github.andrewmalitchuk.uncial.model.source.text.OcrDocument
import io.github.andrewmalitchuk.uncial.structure.source.reconstruction.DocumentStructure
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGImageRef
import platform.Foundation.NSData
import platform.UIKit.UIImage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Swift-facing entry point.
 *
 * Kotlin's generated Obj-C API is usable but not pleasant: `ByteArray` arrives as
 * `KotlinByteArray` (so a Swift caller cannot hand over a `Data`), the `UncialClient { }`
 * builder becomes a lambda-taking top-level function on a `…Kt` class, and `Result` does
 * not survive the trip at all. PLAN.md §6.3 reserves a whole `:sdk:swift` module for
 * smoothing that over; this object is the minimum needed to make the iOS sample readable,
 * and doubles as a statement of what that module has to cover.
 *
 * It is deliberately a façade and nothing more: an `object` cannot be split across files,
 * so every body lives in `core/` and each member here is the one-line delegation that
 * names it. What stays is what Swift actually sees — the `@ObjCName`, the `@Throws` list,
 * the default arguments, and the `Raster` lifetime in the image overload.
 *
 * From Swift:
 *
 * ```swift
 * UncialBootstrap.shared.start()
 * let client = UncialBootstrap.shared.createClient(...)
 * let document = try await UncialBootstrap.shared.extract(client: client, pdf: data)
 * let blocks = UncialBootstrap.shared.reconstruct(document: document)
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("UncialBootstrap", exact = true)
public object UncialIos {

    /**
     * Registers the Vision engine. Call once at launch.
     *
     * iOS has no `androidx.startup`, so unlike Android this step cannot be automatic.
     * Idempotent.
     */
    public fun start() {
        installVisionEngine()
    }

    /** Engine identifiers currently registered, for diagnostics. */
    public fun registeredEngines(): List<String> = OcrEngineRegistry.engines().map { it.id }

    /**
     * Builds a client with the digital text-layer fast path already wired in.
     *
     * Note that Obj-C export drops Kotlin default arguments, so Swift passes every
     * parameter — another thing `:sdk:swift` would hide.
     *
     * @param onLog receives diagnostics; pass `null` to discard them.
     */
    public fun createClient(
        languages: List<OcrLanguage> = OcrLanguage.Default,
        renderDpi: Int = OcrOptions.DEFAULT_RENDER_DPI,
        includeWords: Boolean = false,
        preferDigitalLayer: Boolean = true,
        onLog: ((String) -> Unit)? = null,
    ): UncialClient = buildIosClient(
        languages = languages,
        renderDpi = renderDpi,
        includeWords = includeWords,
        preferDigitalLayer = preferDigitalLayer,
        onLog = onLog,
    )

    /**
     * Extracts a PDF handed over as `NSData`, reporting progress per page.
     *
     * Takes `NSData` rather than `ByteArray` so Swift can pass a `Data` straight from
     * `Bundle` or a file, and throws rather than returning a `Result`, which does not
     * export. In Swift this is `try await`.
     *
     * [onProgress] is how `extractAsFlow` reaches Swift at all: a Kotlin `Flow` does not
     * export. It is called on whichever thread the pipeline is on, so a caller updating UI
     * has to hop to the main actor itself.
     */
    @Throws(OcrError::class, CancellationException::class)
    public suspend fun extract(
        client: UncialClient,
        pdf: NSData,
        onProgress: (IosRunProgress) -> Unit,
    ): OcrDocument = collectDocument(client.extractAsFlow(pdf.toByteArray()), onProgress)

    /**
     * Recognizes a photo — from the camera or the photo library — with no PDF involved.
     *
     * Takes a `UIImage` rather than the `Raster(CGImageRef)` a Swift caller would otherwise
     * have to build: `CGImageRef` is a bare `CPointer` in Kotlin/Native and exports to
     * Obj-C as an untyped pointer, so from Swift it means `Unmanaged.passUnretained(_:)`
     * and hoping. Exactly the kind of edge this object exists to hide. (PLAN.md §6.3)
     *
     * The image is normalized to an upright `CGImage` first. Uncial does not rescale a
     * raster the caller supplies, and neither does this: `OcrOptions`' `renderDpi` and
     * `maxPageSide` govern rasterization, and nothing was rasterized here. A 48 MP photo is
     * recognized at 48 MP unless Swift downsizes it first.
     *
     * @throws OcrError.RecognitionFailed if the image carries no bitmap at all — a
     *   `UIImage` backed purely by a `CIImage`, which has no `CGImage` to hand over.
     */
    @OptIn(ExperimentalForeignApi::class)
    @Throws(OcrError::class, CancellationException::class)
    public suspend fun extract(
        client: UncialClient,
        image: UIImage,
        onProgress: (IosRunProgress) -> Unit,
    ): OcrDocument {
        // Explicitly nullable, because the Obj-C signature is unannotated: Kotlin types
        // `CGImage` as non-null and a plain `?: throw` on it would be dead code the
        // compiler warns about, while the property really does return null.
        val cgImage: CGImageRef? = image.upright().CGImage
        val bitmap = cgImage ?: throw OcrError.RecognitionFailed(
            pageIndex = 0,
            message = "this UIImage has no CGImage (CIImage-backed?)",
        )
        val raster = Raster(bitmap)
        try {
            return collectDocument(client.extractAsFlow(raster), onProgress)
        } finally {
            // Raster retains the CGImage in its constructor, so this is the release that
            // balances it. The UIImage the caller passed is untouched.
            raster.release()
        }
    }

    /** Turns a recognized document into headings and paragraphs. */
    public fun reconstruct(document: OcrDocument): List<DocBlock> =
        DocumentStructure.reconstruct(document)

    /**
     * Reduces a recognized document to the counts a UI shows, structure included.
     *
     * See [IosDocumentSummary] for why this arithmetic cannot be done on the Swift side.
     */
    public fun summarize(document: OcrDocument): IosDocumentSummary = document.toIosSummary()
}
