package io.github.andrewmalitchuk.uncial.engine.tesseract.core.native

import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.warn
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Orientation
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import net.sourceforge.tess4j.ITessAPI
import net.sourceforge.tess4j.TessAPI
import java.awt.image.BufferedImage
import java.awt.image.ComponentSampleModel
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thin, Leptonica-free wrapper over libtesseract's C API.
 *
 * ### Why not Tess4J's `Tesseract.getWords`
 *
 * The obvious implementation — `Tesseract().getWords(image, RIL_TEXTLINE)` — routes the
 * image through Leptonica via `lept4j`, and `lept4j`'s bundled bindings are pinned to a
 * particular Leptonica ABI. On a machine whose system Leptonica is a different minor
 * version, recognition dies with a bare
 * `UnsatisfiedLinkError: symbol not found: pixFindBaselinesGen` — which is what happens
 * with Tess4J 5.20 (lept4j 1.24) against Leptonica 1.85, the current Homebrew build.
 *
 * An SDK cannot ship that: the consumer's system Leptonica is not something we control,
 * and the failure appears only at the first recognized page. Talking to libtesseract
 * directly removes Leptonica from the picture entirely — `TessBaseAPISetImage` takes raw
 * pixels — and, as a bonus, exposes the per-line deskew angle that Tess4J's high-level API
 * hides.
 *
 * It also brings the JVM engine to the same iteration model as Tesseract4Android on
 * Android, which is what behavioural parity between the two actually requires.
 */
internal class TessHandle private constructor(
    private val api: TessAPI,
    private val handle: ITessAPI.TessBaseAPI,
    private val logger: OcrLogger,
) : AutoCloseable {

    /**
     * Guards every native call, and `close()` along with them.
     *
     * The recognizer above serializes `recognize()` with a coroutine `Mutex`, but
     * `close()` is a plain `AutoCloseable.close()` reachable from any thread — the runtime
     * calls it from `UncialClient.close()` — and a suspending mutex cannot be taken there.
     * Without this lock, closing a client while a page is in flight runs
     * `TessBaseAPIDelete` underneath a live `TessBaseAPIRecognize`, which is a native
     * use-after-free rather than an exception. A blocking lock is the right trade: closing
     * waits out the page that is already running.
     */
    private val nativeLock = ReentrantLock()
    private var closed = false

    /** Tells Tesseract the raster's resolution, which measurably helps its heuristics. */
    fun setSourceResolution(dpi: Int) = withHandle {
        api.TessBaseAPISetSourceResolution(handle, dpi)
    }

    /**
     * Hands the image to Tesseract as raw bytes.
     *
     * Anything that is not already 8-bit grayscale is converted first. Tesseract binarizes
     * internally, so this loses no accuracy and a quarter of the bytes cross the boundary.
     */
    fun setImage(image: BufferedImage) = withHandle {
        val gray = image.asGrayscale()
        val raster = gray.raster
        val buffer = raster.dataBuffer as DataBufferByte
        val sampleModel = raster.sampleModel as ComponentSampleModel
        api.TessBaseAPISetImage(
            handle,
            ByteBuffer.wrap(buffer.data),
            gray.width,
            gray.height,
            BYTES_PER_GRAY_PIXEL,
            // The scanline stride, not the width: rasters are frequently padded, and using
            // the width would shear the image by a pixel per row.
            sampleModel.scanlineStride,
        )
    }

    /** Runs recognition. Throws if Tesseract reports failure. */
    fun recognize() = withHandle {
        val status = api.TessBaseAPIRecognize(handle, null)
        if (status != 0) throw IllegalStateException("TessBaseAPIRecognize returned $status")
    }

    /**
     * Walks the recognized results at [level], calling [onElement] for each.
     *
     * The iterator is a native cursor; everything it yields is read before advancing.
     */
    fun forEachElement(level: Int, onElement: (TessElement) -> Unit) = withHandle {
        val iterator = api.TessBaseAPIGetIterator(handle) ?: return@withHandle
        try {
            // JNA's generated signatures carry no nullability, so Kotlin types these as
            // non-null while the C API returns NULL for a page with no layout at all.
            // Typing the local nullable is what keeps the guard below alive.
            val pageIterator: ITessAPI.TessPageIterator? =
                api.TessResultIteratorGetPageIterator(iterator)
            do {
                val textPointer = api.TessResultIteratorGetUTF8Text(iterator, level)
                val text = textPointer?.getString(0, "UTF-8")
                // Tesseract allocates the string on its own heap; it must be handed back.
                textPointer?.let { api.TessDeleteText(it) }
                if (text.isNullOrBlank()) continue

                val box = pageIterator?.let { readBoundingBox(it, level) } ?: continue
                val geometry = readOrientation(pageIterator)
                onElement(
                    TessElement(
                        text = text.trim(),
                        box = box,
                        confidence = Confidence.ofPercent(
                            api.TessResultIteratorConfidence(iterator, level),
                        ),
                        skewDegrees = geometry.skewDegrees,
                        orientation = geometry.orientation,
                        // The recognition language of this element. Only reachable through
                        // the C API: Tess4J's high-level wrapper drops it, and it is the
                        // one thing that makes a mixed ukr+eng document self-describing.
                        languageCode = api
                            .TessResultIteratorWordRecognitionLanguage(iterator)
                            ?.takeIf { it.isNotBlank() },
                    ),
                )
            } while (api.TessResultIteratorNext(iterator, level) != 0)
        } finally {
            api.TessResultIteratorDelete(iterator)
        }
    }

    private fun readBoundingBox(
        pageIterator: ITessAPI.TessPageIterator,
        level: Int,
    ): BoundingBox? {
        val left = IntBuffer.allocate(1)
        val top = IntBuffer.allocate(1)
        val right = IntBuffer.allocate(1)
        val bottom = IntBuffer.allocate(1)
        val found = api.TessPageIteratorBoundingBox(pageIterator, level, left, top, right, bottom)
        if (found == 0) return null
        return BoundingBox(
            left = left.get(0).toFloat(),
            top = top.get(0).toFloat(),
            width = (right.get(0) - left.get(0)).toFloat(),
            height = (bottom.get(0) - top.get(0)).toFloat(),
        )
    }

    /**
     * The element's orientation and deskew angle.
     *
     * One C call yields both, so they are read together. Tesseract reports the deskew in
     * radians as the angle needed to *straighten* the line, which is the negation of the
     * line's own tilt — hence the sign flip. Neither value is reachable through Tess4J's
     * high-level wrapper.
     */
    private fun readOrientation(pageIterator: ITessAPI.TessPageIterator): TessGeometry {
        val orientation = IntBuffer.allocate(1)
        val writingDirection = IntBuffer.allocate(1)
        val textlineOrder = IntBuffer.allocate(1)
        val deskewRadians = java.nio.FloatBuffer.allocate(1)
        api.TessPageIteratorOrientation(
            pageIterator,
            orientation,
            writingDirection,
            textlineOrder,
            deskewRadians,
        )
        val radians = deskewRadians.get(0)
        return TessGeometry(
            orientation = orientation.get(0).toOrientation(),
            skewDegrees = if (radians.isFinite()) (-radians * DEGREES_PER_RADIAN).toFloat() else 0f,
        )
    }

    override fun close() {
        nativeLock.withLock {
            if (closed) return
            closed = true
            try {
                api.TessBaseAPIDelete(handle)
            } catch (cause: Throwable) {
                // Nothing useful is left to do, but a native free that failed should not
                // vanish -- it is the shape of a leak that is otherwise invisible.
                logger.warn("TessBaseAPIDelete failed", cause)
            }
        }
    }

    /**
     * Runs [block] holding [nativeLock], refusing to touch a handle that is already freed.
     */
    private inline fun <T> withHandle(block: () -> T): T = nativeLock.withLock {
        check(!closed) { "this TessHandle has been closed" }
        block()
    }

    /** One recognized element — a line or a word, depending on the iteration level. */
    internal data class TessElement(
        val text: String,
        val box: BoundingBox,
        val confidence: Confidence,
        val skewDegrees: Float,
        val orientation: Orientation,
        /** Tesseract's own language code for this element, e.g. `ukr`. */
        val languageCode: String?,
    )

    private data class TessGeometry(
        val orientation: Orientation,
        val skewDegrees: Float,
    )

    internal companion object {
        private const val BYTES_PER_GRAY_PIXEL = 1
        private const val DEGREES_PER_RADIAN = 180.0 / Math.PI

        /**
         * Initializes libtesseract for [languages] out of [dataPath].
         *
         * @param dataPath the directory containing the `.traineddata` files. Note that this
         *   is Tesseract's own convention at the C level: unlike the Android Java wrapper,
         *   nothing appends `tessdata/` here.
         */
        fun open(
            dataPath: String,
            languages: String,
            pageSegMode: Int,
            logger: OcrLogger,
        ): TessHandle {
            val api = TessAPI.INSTANCE
            val handle = api.TessBaseAPICreate()
                ?: throw OcrError.EngineInit("TessBaseAPICreate returned null")
            // Everything past the create has to hand the handle back on the way out. Init2
            // can throw rather than return a status -- an UnsatisfiedLinkError or any other
            // JNA Error -- and the created TessBaseAPI would leak on that path.
            return try {
                // OEM_LSTM_ONLY: the combined mode needs legacy data that neither
                // tessdata_fast nor tessdata_best ships, so requesting it fails init.
                val status = api.TessBaseAPIInit2(
                    handle,
                    dataPath,
                    languages,
                    ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY,
                )
                if (status != 0) {
                    throw OcrError.NoLanguageData(
                        languages = emptyList(),
                        searchedPath = dataPath,
                        cause = IllegalStateException(
                            "TessBaseAPIInit2 returned $status for '$languages'",
                        ),
                    )
                }
                api.TessBaseAPISetPageSegMode(handle, pageSegMode)
                TessHandle(api, handle, logger)
            } catch (cause: Throwable) {
                api.TessBaseAPIDelete(handle)
                throw cause
            }
        }
    }
}

/** This image if it is already 8-bit grayscale, otherwise a grayscale copy of it. */
private fun BufferedImage.asGrayscale(): BufferedImage {
    // A sub-image shares its parent's DataBuffer and merely offsets into it, so handing
    // that buffer to Tesseract from index 0 would recognize the parent's top-left corner
    // instead of the crop. `Raster(BufferedImage)` is public, so getSubimage() really can
    // arrive here: copy in that case rather than reinterpret.
    if (type == BufferedImage.TYPE_BYTE_GRAY && !isOffsetIntoParent()) return this
    val gray = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    val graphics = gray.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return gray
}

/** Whether this image's pixels start somewhere other than the beginning of its buffer. */
private fun BufferedImage.isOffsetIntoParent(): Boolean {
    val raster = raster
    val buffer = raster.dataBuffer
    return raster.sampleModelTranslateX != 0 ||
        raster.sampleModelTranslateY != 0 ||
        buffer.offset != 0
}

/** Maps libtesseract's `TessOrientation` to the SDK's [Orientation]. */
private fun Int.toOrientation(): Orientation = when (this) {
    ITessAPI.TessOrientation.ORIENTATION_PAGE_RIGHT -> Orientation.Right
    ITessAPI.TessOrientation.ORIENTATION_PAGE_DOWN -> Orientation.Down
    ITessAPI.TessOrientation.ORIENTATION_PAGE_LEFT -> Orientation.Left
    else -> Orientation.Up
}
