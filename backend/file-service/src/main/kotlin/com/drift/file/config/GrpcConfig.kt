package com.drift.file.config

import com.drift.file.common.JwtServerInterceptor
import com.drift.file.common.JwtSupport
import com.drift.file.common.LoggingServerInterceptor
import io.grpc.ServerInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.GlobalServerInterceptor

@Configuration
class GrpcConfig {
    @Bean
    @GlobalServerInterceptor
    fun loggingServerInterceptor(): ServerInterceptor = LoggingServerInterceptor()

    @Bean
    @GlobalServerInterceptor
    fun jwtServerInterceptor(jwtSupport: JwtSupport): ServerInterceptor = JwtServerInterceptor(jwtSupport)
}
