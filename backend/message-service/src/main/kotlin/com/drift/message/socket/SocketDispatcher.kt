package com.drift.message.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

class SocketDispatcher(
    private val scope: CoroutineScope,
) {
    private val queues = ConcurrentHashMap<String, Channel<suspend () -> Unit>>()

    fun submit(
        sessionId: String,
        task: suspend () -> Unit,
    ) {
        queues.computeIfAbsent(sessionId) { newQueue() }.trySend(task)
    }

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
