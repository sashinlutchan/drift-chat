package com.drift.message.socket

fun main() {
    val s = DriftSocketServer(port = 8090, allowedOrigins = "*")
    s.start()
    println("SPIKE SERVER READY")
    // Force-close all client connections every 2s so the client can prove reconnection.
    Thread {
        while (true) {
            Thread.sleep(2000)
            s.forceDisconnectAll()
        }
    }.apply { isDaemon = true }.start()
    Thread.currentThread().join()
}
