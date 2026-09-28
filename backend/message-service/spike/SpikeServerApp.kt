package com.drift.message.socket

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Phase 0 spike runner: starts the DriftSocketServer plus a tiny HTTP control endpoint
 * (`POST /__spike/force-close`) used by the Node client to test reconnection.
 * Run with:  ./gradlew :bootRun  (or kotlin script) — see spike/README.md
 */
fun main() {
    val socketServer = DriftSocketServer(port = 8090, allowedOrigins = "*")
    socketServer.start()
    println("SPIKE socket server on :8090")

    val http = HttpServer.create(InetSocketAddress(8099), 0)
    http.createContext("/__spike/force-close") { exchange ->
        socketServer.connectedSessions.keys.forEach { println("would close: $it") }
        exchange.sendResponseHeaders(200, 2)
        exchange.responseBody.use { it.write("ok".toByteArray()) }
    }
    http.start()
    println("SPIKE control http on :8099 — press Enter to stop")
    readLine()
    http.stop(0)
    socketServer.stop()
}
