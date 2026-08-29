package io.github.andrewmalitchuk.uncial.dist.core.interop

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Copies the bytes out of an `NSData` so the SDK can take them as a `ByteArray`.
 *
 * Swift hands over a `Data`; every entry point below the façade speaks `ByteArray`. The
 * copy is what bridges the two, and it is a copy on purpose: the `NSData` may be autoreleased
 * the moment the façade returns, while the extraction outlives that.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    return bytes
}
