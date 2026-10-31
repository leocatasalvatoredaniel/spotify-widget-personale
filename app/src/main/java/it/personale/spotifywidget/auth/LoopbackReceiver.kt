package it.personale.spotifywidget.auth

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Receives the end of the Spotify login on http://127.0.0.1:<port>/callback.
 * Spotify accepts only HTTPS or loopback redirects for new apps, and a phone
 * app has no HTTPS address: it listens on the loopback address for the
 * browser, as RFC 8252 suggests for native apps. Only this phone can reach it.
 */
class LoopbackReceiver(private val scope: CoroutineScope) {
    private var server: ServerSocket? = null

    /**
     * Starts listening, and returns the redirect URI with the chosen port.
     * [onCallback] gets the callback URI and returns the page for the browser.
     */
    fun start(onCallback: suspend (Uri) -> String): String {
        stop()
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName(HOST), PORT), 1)
        }
        socket.soTimeout = TIMEOUT_MS
        server = socket
        scope.launch(Dispatchers.IO) {
            try {
                while (!socket.isClosed) {
                    socket.accept().use { client -> if (handle(client, onCallback)) socket.close() }
                }
            } catch (_: IOException) {
                // Timeout, or closed by a new login
            } finally {
                socket.close()
            }
        }
        return "http://$HOST:${socket.localPort}$PATH"
    }

    fun stop() {
        server?.close()
        server = null
    }

    /** True when the login callback arrived */
    private suspend fun handle(client: Socket, onCallback: suspend (Uri) -> String): Boolean {
        client.soTimeout = READ_TIMEOUT_MS
        val requestLine = client.getInputStream().bufferedReader().readLine() ?: return false
        val target = requestLine.split(" ").getOrNull(1).orEmpty()
        // Browsers also ask for /favicon.ico
        if (!target.startsWith(PATH)) {
            respond(client, "404 Not Found", "")
            return false
        }
        respond(client, "200 OK", onCallback("http://$HOST$target".toUri()))
        return true
    }

    private fun respond(client: Socket, status: String, html: String) {
        val body = html.toByteArray()
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        client.getOutputStream().apply {
            write(headers.toByteArray())
            write(body)
            flush()
        }
    }

    companion object {
        /**
         * Fixed port: the Spotify dashboard refuses a loopback redirect URI
         * without a port, so http://127.0.0.1:43821/callback is registered
         */
        const val PORT = 43821
        private const val HOST = "127.0.0.1"
        private const val PATH = "/callback"
        private const val TIMEOUT_MS = 10 * 60 * 1000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
