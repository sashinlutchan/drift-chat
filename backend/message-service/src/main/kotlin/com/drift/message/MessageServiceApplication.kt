package com.drift.message

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** message-service entry point. */
@SpringBootApplication
class MessageServiceApplication

fun main(args: Array<String>) {
    runApplication<MessageServiceApplication>(*args)
}
