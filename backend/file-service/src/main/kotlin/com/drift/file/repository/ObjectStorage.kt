package com.drift.file.repository

import com.drift.file.config.AppProperties
import com.drift.file.interfaces.IObjectStorage
import com.drift.file.models.ObjectLocation
import com.drift.file.models.PartEtag
import com.google.common.collect.HashMultimap
import io.github.oshai.kotlinlogging.KotlinLogging
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.SetBucketEncryptionArgs
import io.minio.StatObjectArgs
import io.minio.messages.Part
import io.minio.messages.SseConfiguration
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream

private val log = KotlinLogging.logger {}

/**
 * MinIO-backed [IObjectStorage] (spec sections 11 and 13.4); the only class that talks to the MinIO SDK.
 * Abandoned multipart uploads need no rule from us: MinIO itself removes stale ones
 * (`MINIO_API_STALE_UPLOADS_EXPIRY`, 24h by default).
 */
@Component
class ObjectStorage(
    private val client: MinioAsyncClient,
    private val properties: AppProperties,
) : IObjectStorage {
    /** Runs once the app is up so a slow MinIO doesn't block startup; buckets are also created by the compose init job. */
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        runCatching { ensureBuckets() }.onFailure { log.warn(it) { "Could not prepare MinIO buckets yet" } }
    }

    override fun ensureBuckets() {
        listOf(properties.storage.minioBucketAvatars, properties.storage.minioBucketMedia).forEach { bucket ->
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()).get()) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()).get()
            }
            runCatching {
                client
                    .setBucketEncryption(
                        SetBucketEncryptionArgs
                            .builder()
                            .bucket(bucket)
                            .config(SseConfiguration.newConfigWithSseS3Rule())
                            .build(),
                    ).get()
            }.onFailure { log.warn { "Server-side encryption not enabled on $bucket (is a KMS key configured on MinIO?): ${it.message}" } }
        }
    }

    override fun put(
        location: ObjectLocation,
        content: ByteArray,
        contentType: String,
    ) {
        client
            .putObject(
                PutObjectArgs
                    .builder()
                    .bucket(location.bucket)
                    .`object`(location.key)
                    .contentType(contentType)
                    .stream(ByteArrayInputStream(content), content.size.toLong(), -1)
                    .build(),
            ).get()
    }

    override fun startMultipart(
        location: ObjectLocation,
        contentType: String,
    ): String {
        val headers = HashMultimap.create<String, String>()
        headers.put("Content-Type", contentType)
        return client
            .createMultipartUploadAsync(location.bucket, null, location.key, headers, null)
            .get()
            .result()
            .uploadId()
    }

    override fun uploadPart(
        location: ObjectLocation,
        multipartId: String,
        partNumber: Int,
        data: ByteArray,
    ): String =
        client
            .uploadPartAsync(location.bucket, null, location.key, data, data.size.toLong(), multipartId, partNumber, null, null)
            .get()
            .etag()

    override fun listParts(
        location: ObjectLocation,
        multipartId: String,
    ): List<PartEtag> =
        client
            .listPartsAsync(location.bucket, null, location.key, MAX_PARTS, 0, multipartId, null, null)
            .get()
            .result()
            .partList()
            .map { PartEtag(it.partNumber(), it.etag()) }

    override fun completeMultipart(
        location: ObjectLocation,
        multipartId: String,
        parts: List<PartEtag>,
    ) {
        val minioParts = parts.sortedBy { it.partNumber }.map { Part(it.partNumber, it.etag) }.toTypedArray()
        client.completeMultipartUploadAsync(location.bucket, null, location.key, multipartId, minioParts, null, null).get()
    }

    override fun abortMultipart(
        location: ObjectLocation,
        multipartId: String,
    ) {
        client.abortMultipartUploadAsync(location.bucket, null, location.key, multipartId, null, null).get()
    }

    override fun size(location: ObjectLocation): Long? =
        try {
            client
                .statObject(
                    StatObjectArgs
                        .builder()
                        .bucket(location.bucket)
                        .`object`(location.key)
                        .build(),
                ).get()
                .size()
        } catch (e: java.util.concurrent.ExecutionException) {
            if (isNotFound(e)) null else throw e
        }

    override fun read(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): ByteArray = open(location, offset, length).use { it.readAllBytes() }

    override fun open(
        location: ObjectLocation,
        offset: Long,
        length: Long,
    ): InputStream =
        client
            .getObject(
                GetObjectArgs
                    .builder()
                    .bucket(location.bucket)
                    .`object`(location.key)
                    .offset(offset)
                    .length(length)
                    .build(),
            ).get()

    override fun delete(location: ObjectLocation) {
        client
            .removeObject(
                RemoveObjectArgs
                    .builder()
                    .bucket(location.bucket)
                    .`object`(location.key)
                    .build(),
            ).get()
    }

    /** The SDK wraps the real error in one or two layers of future exceptions; look through them. */
    private fun isNotFound(e: Throwable): Boolean =
        generateSequence(e) { it.cause }
            .filterIsInstance<io.minio.errors.ErrorResponseException>()
            .any { it.errorResponse().code() in NOT_FOUND_CODES }

    private companion object {
        const val MAX_PARTS = 1000
        val NOT_FOUND_CODES = setOf("NoSuchKey", "NoSuchObject", "NoSuchBucket")
    }
}
