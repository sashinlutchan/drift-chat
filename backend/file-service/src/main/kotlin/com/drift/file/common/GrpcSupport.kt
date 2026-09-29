package com.drift.file.common

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingServerCall
import io.grpc.ForwardingServerCallListener
import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.MDC
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/** gRPC metadata keys shared by the interceptors. */
object GrpcKeys {
    val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    val REQUEST_ID: Metadata.Key<String> = Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)
    val TRACEPARENT: Metadata.Key<String> = Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)
}

/** Builds outbound gRPC channels to the other services on the private network. */
object GrpcChannels {
    private const val DEFAULT_DEADLINE_SECONDS = 30L

    fun create(target: String): ManagedChannel =
        Grpc
            .newChannelBuilder(target, InsecureChannelCredentials.create())
            .maxInboundMessageSize(MAX_MESSAGE_BYTES)
            .intercept(RequestContextClientInterceptor())
            .build()

    fun shutdown(channel: ManagedChannel) {
        channel.shutdown()
        if (!channel.awaitTermination(DEFAULT_DEADLINE_SECONDS, TimeUnit.SECONDS)) channel.shutdownNow()
    }

    const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024
}

/** Propagates the request id (from MDC) to called services. */
class RequestContextClientInterceptor : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> =
        object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            override fun start(
                responseListener: Listener<RespT>,
                headers: Metadata,
            ) {
                headers.put(GrpcKeys.REQUEST_ID, MDC.get(REQUEST_ID_MDC) ?: UUID.randomUUID().toString())
                super.start(responseListener, headers)
            }
        }

    companion object {
        const val REQUEST_ID_MDC = "requestId"
    }
}

/** Reads/creates the request id, keeps it in the logging context during the call and logs the outcome. */
class LoggingServerInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val requestId = headers.get(GrpcKeys.REQUEST_ID) ?: UUID.randomUUID().toString()
        val method = call.methodDescriptor.fullMethodName
        val wrapped =
            object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                override fun close(
                    status: Status,
                    trailers: Metadata,
                ) {
                    logger.info { "gRPC $method -> ${status.code} (requestId=$requestId)" }
                    super.close(status, trailers)
                }
            }
        val delegate = withRequestId(requestId) { next.startCall(wrapped, headers) }
        return object : ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(delegate) {
            override fun onMessage(message: ReqT) = withRequestId(requestId) { super.onMessage(message) }

            override fun onHalfClose() = withRequestId(requestId) { super.onHalfClose() }

            override fun onCancel() = withRequestId(requestId) { super.onCancel() }

            override fun onComplete() = withRequestId(requestId) { super.onComplete() }
        }
    }

    private fun <T> withRequestId(
        requestId: String,
        block: () -> T,
    ): T {
        MDC.put(RequestContextClientInterceptor.REQUEST_ID_MDC, requestId)
        try {
            return block()
        } finally {
            MDC.remove(RequestContextClientInterceptor.REQUEST_ID_MDC)
        }
    }
}

/**
 * Verifies the user's JWT passed in call metadata (`authorization: Bearer <jwt>`).
 * A token that is present but invalid is rejected; a missing token is rejected only when [required].
 * Internal, event-driven calls carry no user JWT, so the default is optional.
 */
class JwtServerInterceptor(
    private val jwtSupport: JwtSupport,
    private val required: Boolean = false,
) : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val token = headers.get(GrpcKeys.AUTHORIZATION)?.removePrefix("Bearer ")?.trim()
        val rejected =
            when {
                token == null -> required
                else -> jwtSupport.verify(token) == null
            }
        if (rejected) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid session token"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }
        return next.startCall(call, headers)
    }
}
