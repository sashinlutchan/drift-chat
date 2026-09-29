package com.drift.message.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * Runs each socket's events one after another, off the network threads. Blocking work (Valkey, gRPC, JDBC) must never
 * run on Netty's event loop, and one socket's events must stay in order (enter a room *then* send to it), while
 * different sockets run in parallel.
 */
class SocketDispatcher(
    private val scope: CoroutineScope,
) {
    private val queues = ConcurrentHashMap<String, Channel<suspend () -> Unit>>()

    /** Queues [task] behind whatever this socket is already doing. */
    fun submit(
        sessionId: String,
        task: suspend () -> Unit,
    ) {
        queues.computeIfAbsent(sessionId) { newQueue() }.trySend(task)
    }

    /** Lets what's already queued finish, then stops the socket's queue. */
    fun close(sessionId: String) {
        queues.remove(sessionId)?.close()
    }

    private fun newQueue(): Channel<suspend () -> Unit> {
        val channel = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        scope.launch {
            for (task in channel) {
                try {
                    task()
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    log.error(e) { "Socket task failed" }
                }
            }
        }
        return channel
    }
}
