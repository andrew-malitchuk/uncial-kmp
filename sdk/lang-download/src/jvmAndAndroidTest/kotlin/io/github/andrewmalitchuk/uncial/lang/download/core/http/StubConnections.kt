package io.github.andrewmalitchuk.uncial.lang.download.core.http

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Turns a per-URL routing function into the connection opener the provider takes. */
internal fun stubConnections(route: (String) -> StubResponse): (String) -> HttpURLConnection =
    { url -> StubHttpConnection(URI(url).toURL(), route(url)) }

private class StubHttpConnection(
    url: URL,
    private val response: StubResponse,
) : HttpURLConnection(url) {
    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = response.status
    override fun getContentLengthLong(): Long = response.advertised
    override fun getInputStream(): InputStream = ByteArrayInputStream(response.body)
}
