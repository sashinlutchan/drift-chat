package com.drift.room

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** room-service entry point. */
@SpringBootApplication
class RoomServiceApplication

fun main(args: Array<String>) {
    runApplication<RoomServiceApplication>(*args)
}
