package com.drift.message.config

import com.drift.message.repository.TypingStore
import com.drift.message.service.TypingBroadcaster
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.UUID

/** Subscribes this pod to typing changes announced by any pod (Valkey pub/sub). */
@Configuration
class RedisConfig {
    @Bean
    fun typingListenerContainer(
        connectionFactory: RedisConnectionFactory,
        broadcaster: TypingBroadcaster,
    ): RedisMessageListenerContainer =
        RedisMessageListenerContainer().apply {
            setConnectionFactory(connectionFactory)
            addMessageListener({ message, _ ->
                runCatching { UUID.fromString(String(message.body)) }.getOrNull()?.let(broadcaster::broadcast)
            }, ChannelTopic(TypingStore.CHANNEL))
        }
}
