package com.drift.room.exceptions

import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Status
import io.grpc.StatusException

private val log = KotlinLogging.logger {}

fun DomainError.toGrpcStatus(): Status =
    when (this) {
        is DomainError.ValidationFailed, is DomainError.UsernameInvalid -> Status.INVALID_ARGUMENT
        is DomainError.NotFound -> Status.NOT_FOUND
        is DomainError.Unauthorized -> Status.UNAUTHENTICATED
        is DomainError.Forbidden -> Status.PERMISSION_DENIED
        is DomainError.PayloadTooLarge -> Status.OUT_OF_RANGE
        is DomainError.UnsupportedMediaType -> Status.INVALID_ARGUMENT
        is DomainError.UsernameTaken -> Status.ALREADY_EXISTS
        is DomainError.DependencyUnavailable -> Status.UNAVAILABLE
    }.withDescription(detail)

inline fun <T> grpcGuard(block: () -> T): T =
    try {
        block()
    } catch (e: DomainError) {
        throw StatusException(e.toGrpcStatus())
    } catch (e: StatusException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        logUnexpected(e)
        throw StatusException(Status.INTERNAL.withDescription("Internal error"))
    }

@PublishedApi
internal fun logUnexpected(e: Exception) {
    log.error(e) { "Unhandled gRPC error" }
}
