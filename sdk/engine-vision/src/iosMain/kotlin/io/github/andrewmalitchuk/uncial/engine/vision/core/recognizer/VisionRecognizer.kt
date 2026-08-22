package io.github.andrewmalitchuk.uncial.engine.vision.core.recognizer

import io.github.andrewmalitchuk.uncial.core.source.engine.OcrCapabilities
import io.github.andrewmalitchuk.uncial.core.source.log.OcrLogger
import io.github.andrewmalitchuk.uncial.core.source.log.debug
import io.github.andrewmalitchuk.uncial.core.source.raster.Raster
import io.github.andrewmalitchuk.uncial.core.source.recognizer.TextRecognizer
import io.github.andrewmalitchuk.uncial.engine.vision.core.language.visionTag
import io.github.andrewmalitchuk.uncial.model.source.error.OcrError
import io.github.andrewmalitchuk.uncial.model.source.geometry.BoundingBox
import io.github.andrewmalitchuk.uncial.model.source.geometry.Size
import io.github.andrewmalitchuk.uncial.model.source.options.OcrOptions
import io.github.andrewmalitchuk.uncial.model.source.options.RecognitionQuality
import io.github.andrewmalitchuk.uncial.model.source.text.Confidence
import io.github.andrewmalitchuk.uncial.model.source.text.ExtractionSource
import io.github.andrewmalitchuk.uncial.model.source.text.OcrLine
import io.github.andrewmalitchuk.uncial.model.source.text.OcrPage
import io.github.andrewmalitchuk.uncial.model.source.text.OcrWord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSRange
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.Vision.VNRequestTextRecognitionLevelFast

/**
 * Recognition via Apple Vision.
 *
 * Unlike the Tesseract engines this holds no expensive state: Vision requests are cheap,
 * per-call objects and the models live in the OS. Recognition is nevertheless serialized
 * per instance to keep memory predictable when a caller extracts several documents at once.
 * See [lock].
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class VisionRecognizer(
    override val capabilities: OcrCapabilities,
    private val logger: OcrLogger,
) : TextRecognizer {

    /**
     * Serializes recognition, because the runtime shares one recognizer across the
     * concurrent `recognize()` calls of an extraction and implementations are required to
     * tolerate that.
     *
     * Unlike the Tesseract recognizers there is no native handle to protect -- a request
     * and its handler are per-call, so concurrency here would be *correct*. It is still
     * serialized: each in-flight request pins a full page raster plus Vision's own working
     * buffers, so running N pages at once multiplies peak memory by N, and Vision already
     * parallelizes a single request across the Neural Engine, leaving little throughput to
     * win. Peak memory should be a function of page size, not of document length.
     */
    private val lock = Mutex()

    override suspend fun recognize(
        raster: Raster,
        pageIndex: Int,
        options: OcrOptions,
    ): OcrPage = withContext(Dispatchers.Default) {
        lock.withLock {
            currentCoroutineContext().ensureActive()

            val request = VNRecognizeTextRequest(null).apply {
                recognitionLevel = when (options.recognitionQuality) {
                    RecognitionQuality.Fast -> VNRequestTextRecognitionLevelFast
                    RecognitionQuality.Accurate -> VNRequestTextRecognitionLevelAccurate
                }
                // Only the languages Vision confirmed it supports, rather than the POC's
                // hardcoded list, which asked iOS 15 for a Ukrainian it does not have.
                recognitionLanguages = capabilities.languages.mapNotNull { it.visionTag() }
                usesLanguageCorrection = options.recognitionQuality == RecognitionQuality.Accurate
            }

            val handler = VNImageRequestHandler(
                cGImage = raster.image,
                options = emptyMap<Any?, Any?>(),
            )
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val performed = handler.performRequests(listOf(request), error.ptr)
                if (!performed) {
                    throw OcrError.RecognitionFailed(
                        pageIndex = pageIndex,
                        message = "Vision failed on page $pageIndex: " +
                            (error.value?.localizedDescription ?: "unknown error"),
                    )
                }
            }
            currentCoroutineContext().ensureActive()

            val width = raster.width.toFloat()
            val height = raster.height.toFloat()
            val lines = request.results.orEmpty()
                .mapNotNull { it as? VNRecognizedTextObservation }
                .mapNotNull { observation ->
                    observation.toOcrLine(width, height, options.includeWords)
                }
            logger.debug("page $pageIndex: ${lines.size} lines")

            OcrPage(
                index = pageIndex,
                size = Size(width, height),
                lines = lines,
                source = ExtractionSource.Ocr,
            )
        }
    }

    /**
     * Converts one Vision observation into an [OcrLine].
     *
     * Vision reports normalized coordinates with the origin at the bottom-left; Uncial
     * uses top-down pixels. This is the single place that conversion happens, which is why
     * a caller never has to know which engine produced a box. (see `Point`)
     */
    private fun VNRecognizedTextObservation.toOcrLine(
        pageWidth: Float,
        pageHeight: Float,
        includeWords: Boolean,
    ): OcrLine? {
        val candidate = topCandidates(1u).firstOrNull() as? VNRecognizedText ?: return null
        val text = candidate.string.trim()
        if (text.isEmpty()) return null

        val box = boundingBox.useContents {
            val left = origin.x.toFloat() * pageWidth
            val widthPx = size.width.toFloat() * pageWidth
            val heightPx = size.height.toFloat() * pageHeight
            // normalized bottom-up origin -> top-down pixels
            val top = (1f - (origin.y.toFloat() + size.height.toFloat())) * pageHeight
            BoundingBox(left = left, top = top, width = widthPx, height = heightPx)
        }
        val confidence = Confidence.of(candidate.confidence)
        return OcrLine(
            text = text,
            box = box,
            fontSize = box.height,
            // The POC discarded this even though Vision always provides it. (PLAN.md §5.4)
            confidence = confidence,
            words = if (includeWords) {
                candidate.words(candidate.string, confidence, pageWidth, pageHeight)
            } else {
                emptyList()
            },
        )
    }

    /**
     * Splits a recognized line into words, asking Vision for each one's box.
     *
     * Vision has no word-level results the way Tesseract does; what it offers instead is
     * [VNRecognizedText.boundingBoxForRange], which maps a substring of the recognized text
     * back onto the image. Splitting on whitespace and querying each span is therefore the
     * only way to get word geometry out of Vision — and it is worth doing, because it is
     * the difference between iOS reporting `wordLevel = false` and matching the Tesseract
     * engines.
     *
     * A span whose box Vision declines to compute keeps the word but with
     * [BoundingBox.Zero], since dropping the word would silently lose text.
     */
    private fun VNRecognizedText.words(
        text: String,
        lineConfidence: Confidence,
        pageWidth: Float,
        pageHeight: Float,
    ): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break
            val start = index
            while (index < text.length && !text[index].isWhitespace()) index++
            words += OcrWord(
                text = text.substring(start, index),
                box = boxForRange(start, index - start, pageWidth, pageHeight)
                    ?: BoundingBox.Zero,
                // Vision reports confidence per line, not per word; repeating the line's
                // value is more honest than inventing one or claiming Unknown.
                confidence = lineConfidence,
            )
        }
        return words
    }

    private fun VNRecognizedText.boxForRange(
        start: Int,
        length: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): BoundingBox? {
        // NSRange counts UTF-16 code units, which is exactly how Kotlin indexes a String.
        val range = cValue<NSRange> {
            this.location = start.toULong()
            this.length = length.toULong()
        }
        val rectangle = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            boundingBoxForRange(range, error.ptr)
        } ?: return null

        return rectangle.boundingBox.useContents {
            BoundingBox(
                left = origin.x.toFloat() * pageWidth,
                top = (1f - (origin.y.toFloat() + size.height.toFloat())) * pageHeight,
                width = size.width.toFloat() * pageWidth,
                height = size.height.toFloat() * pageHeight,
            )
        }
    }

    /** Nothing to release: Vision holds no per-recognizer native state. */
    override fun close() {
        // No-op.
    }
}
