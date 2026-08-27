package io.github.andrewmalitchuk.uncial.lang.download.core.http

import java.net.HttpURLConnection

/**
 * A canned HTTP response, handed to the provider in place of a real connection.
 *
 * There is no server here on purpose. [TessDataSource] refuses anything but `https`, and
 * standing up a TLS endpoint with a throwaway certificate to test "the body is too big" is
 * more machinery than the thing being tested. Faking the one connection the provider opens
 * gives exact control over the cases that matter — a status, an honest Content-Length, and
 * a lying one.
 *
 * @property advertised what the response *claims* its length is. Defaults to the truth;
 *   set it lower than [body] to model a server that lies, or `-1` for a chunked response
 *   that says nothing at all.
 */
internal class StubResponse(
    val status: Int = HttpURLConnection.HTTP_OK,
    val body: ByteArray = ByteArray(0),
    val advertised: Long = body.size.toLong(),
)
