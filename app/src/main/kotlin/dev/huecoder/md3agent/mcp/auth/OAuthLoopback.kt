package dev.huecoder.md3agent.mcp.auth

import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

/** Loopback redirect for OAuth on the desktop (RFC 8252 §7.3): a one-path HTTP listener on 127.0.0.1 at a free port. */
class OAuthLoopback private constructor(private val server: HttpServer) : Closeable {
    val redirectUri: String get() = "http://127.0.0.1:${server.address.port}$PATH"

    override fun close() = server.stop(0)

    companion object {
        private const val PATH = "/callback"
        private const val PAGE = "<!doctype html><meta charset=utf-8><title>MD3 Agent</title>" +
            "<body style=\"font-family:sans-serif;padding:3em\"><h2>Signed in</h2><p>You can close this tab and go back to MD3 Agent.</p>"

        fun start(onCallback: (Map<String, String>) -> Unit): OAuthLoopback {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            server.createContext("/") { exchange ->
                exchange.use {
                    if (it.requestURI.path != PATH) {
                        it.sendResponseHeaders(404, -1)
                        return@use
                    }
                    val bytes = PAGE.toByteArray()
                    it.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    it.sendResponseHeaders(200, bytes.size.toLong())
                    it.responseBody.write(bytes)
                    onCallback(query(it.requestURI.rawQuery))
                }
            }
            server.start()
            return OAuthLoopback(server)
        }

        private fun query(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = URLDecoder.decode(pair.substringBefore('='), Charsets.UTF_8)
            key to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
        }
    }
}
