package com.drift.room.config

import com.drift.room.common.JwtServerInterceptor
import com.drift.room.common.JwtSupport
import com.drift.room.common.LoggingServerInterceptor
import io.grpc.ServerInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.GlobalServerInterceptor

/** Interceptors applied to every gRPC service: request id + logging, then JWT metadata verification. */
@Configuration
class GrpcConfig {
    @Bean
    @GlobalServerInterceptor
    fun loggingServerInterceptor(): ServerInterceptor = LoggingServerInterceptor()

    @Bean
    @GlobalServerInterceptor
    fun jwtServerInterceptor(jwtSupport: JwtSupport): ServerInterceptor = JwtServerInterceptor(jwtSupport)
}
