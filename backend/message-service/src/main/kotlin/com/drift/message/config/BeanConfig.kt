package com.drift.message.config

import com.drift.message.common.JwtSupport
import com.drift.message.common.MessageCipher
import com.drift.message.interfaces.IMessageCipher
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.util.Base64

/** Shared bean wiring: Clock (testable time), JwtSupport and the message cipher with its key versions. */
@Configuration
@EnableConfigurationProperties(AppProperties::class)
class BeanConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun jwtSupport(
        properties: AppProperties,
        clock: Clock,
    ): JwtSupport = JwtSupport(properties.jwtSecret.toByteArray(), clock)

    @Bean
    fun messageCipher(properties: AppProperties): IMessageCipher {
        val decoder = Base64.getDecoder()
        val keys = mutableMapOf<Int, ByteArray>()
        properties.messageEncryptionPreviousKeys
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach {
                val (version, key) = it.split(':', limit = 2)
                keys[version.toInt()] = decoder.decode(key)
            }
        keys[properties.messageEncryptionKeyVersion] = decoder.decode(properties.messageEncryptionKey)
        return MessageCipher(keys, properties.messageEncryptionKeyVersion)
    }
}
