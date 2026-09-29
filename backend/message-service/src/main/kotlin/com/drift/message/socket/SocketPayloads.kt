package com.drift.message.socket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Turns our DTOs into plain JSON-friendly maps before they go over Socket.IO, so ids and instants are always strings
 * and the wire format is exactly the same as REST.
 */
object SocketPayloads {
    private val mapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    /**
     * Plain values pass through; anything else (a DTO, or a map or list holding DTOs) is converted, all the way down, to
     * maps, lists and strings, so nothing depends on how Socket.IO's own serializer treats Kotlin classes.
     */
    fun toWire(payload: Any): Any =
        when (payload) {
            is String, is Number, is Boolean -> payload
            else -> mapper.convertValue(payload, Any::class.java)
        }
}
