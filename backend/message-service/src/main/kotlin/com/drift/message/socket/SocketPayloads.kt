package com.drift.message.socket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object SocketPayloads {
    private val mapper: ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun toWire(payload: Any): Any =
        when (payload) {
            is String, is Number, is Boolean -> payload
            else -> mapper.convertValue(payload, Any::class.java)
        }
}
