package com.drift.file.common

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

class GrpcSupportTest {
    private val jwt = JwtSupport("test-secret-key-that-is-32-bytes-long!".toByteArray(), Clock.systemUTC())

    private val stringMarshaller =
        object : MethodDescriptor.Marshaller<String> {
            override fun stream(value: String): InputStream = ByteArrayInputStream(value.toByteArray())

            override fun parse(stream: InputStream): String = String(stream.readBytes())
        }

    private val method: MethodDescriptor<String, String> =
        MethodDescriptor
            .newBuilder(stringMarshaller, stringMarshaller)
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.v1.Echo/Say")
            .build()

    private fun call(
        vararg interceptors: io.grpc.ServerInterceptor,
        headers: Metadata = Metadata(),
        seenRequestId: AtomicReference<String?> = AtomicReference(),
    ): String {
        val name = "grpc-support-${System.nanoTime()}"
        val definition =
            ServerServiceDefinition
                .builder("test.v1.Echo")
                .addMethod(
                    method,
                    ServerCalls.asyncUnaryCall<String, String> { req, obs ->
                        seenRequestId.set(org.slf4j.MDC.get(RequestContextClientInterceptor.REQUEST_ID_MDC))
                        obs.onNext("echo:$req")
                        obs.onCompleted()
                    },
                ).build()
        val server =
            InProcessServerBuilder
                .forName(name)
                .directExecutor()
                .addService(io.grpc.ServerInterceptors.intercept(definition, *interceptors))
                .build()
                .start()
        val channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        try {
            val withHeaders =
                io.grpc.ClientInterceptors.intercept(
                    channel,
                    io.grpc.stub.MetadataUtils
                        .newAttachHeadersInterceptor(headers),
                )
            return ClientCalls.blockingUnaryCall(withHeaders, method, CallOptions.DEFAULT, "hi")
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    private fun bearer(token: String) = Metadata().apply { put(GrpcKeys.AUTHORIZATION, "Bearer $token") }

    @Test
    fun `valid jwt in metadata is accepted`() {
        val token = jwt.issue("u1", "sipho", Duration.ofHours(1)).serialize()
        assertThat(call(JwtServerInterceptor(jwt), headers = bearer(token))).isEqualTo("echo:hi")
    }

    @Test
    fun `invalid jwt in metadata is rejected as unauthenticated`() {
        assertThatThrownBy { call(JwtServerInterceptor(jwt), headers = bearer("garbage")) }
            .isInstanceOfSatisfying(StatusRuntimeException::class.java) {
                assertThat(it.status.code).isEqualTo(Status.Code.UNAUTHENTICATED)
            }
    }

    @Test
    fun `expired jwt is rejected`() {
        val token = jwt.issue("u1", "sipho", Duration.ofSeconds(-10)).serialize()
        assertThatThrownBy { call(JwtServerInterceptor(jwt), headers = bearer(token)) }
            .isInstanceOfSatisfying(StatusRuntimeException::class.java) {
                assertThat(it.status.code).isEqualTo(Status.Code.UNAUTHENTICATED)
            }
    }

    @Test
    fun `missing jwt is allowed by default but rejected when required`() {
        assertThat(call(JwtServerInterceptor(jwt))).isEqualTo("echo:hi")
        assertThatThrownBy { call(JwtServerInterceptor(jwt, required = true)) }
            .isInstanceOfSatisfying(StatusRuntimeException::class.java) {
                assertThat(it.status.code).isEqualTo(Status.Code.UNAUTHENTICATED)
            }
    }

    @Test
    fun `logging interceptor puts the caller's request id in the logging context`() {
        val seen = AtomicReference<String?>()
        val headers = Metadata().apply { put(GrpcKeys.REQUEST_ID, "req-123") }
        call(LoggingServerInterceptor(), headers = headers, seenRequestId = seen)
        assertThat(seen.get()).isEqualTo("req-123")
    }

    @Test
    fun `logging interceptor generates a request id when none is sent`() {
        val seen = AtomicReference<String?>()
        call(LoggingServerInterceptor(), seenRequestId = seen)
        assertThat(seen.get()).isNotBlank()
    }

    @Test
    fun `client interceptor sends a request id header`() {
        val next = mockk<io.grpc.Channel>()
        val delegate = mockk<ClientCall<String, String>>(relaxed = true)
        every { next.newCall(method, CallOptions.DEFAULT) } returns delegate
        val sent = slot<Metadata>()
        every { delegate.start(any(), capture(sent)) } returns Unit

        RequestContextClientInterceptor()
            .interceptCall(method, CallOptions.DEFAULT, next)
            .start(mockk(relaxed = true), Metadata())

        assertThat(sent.captured.get(GrpcKeys.REQUEST_ID)).isNotBlank()
        verify { delegate.start(any(), any()) }
    }

    @Test
    fun `channels can be created and shut down`() {
        val channel = GrpcChannels.create("localhost:1")
        assertThat(channel.authority()).isEqualTo("localhost:1")
        GrpcChannels.shutdown(channel)
        assertThat(channel.isShutdown).isTrue()
    }
}
