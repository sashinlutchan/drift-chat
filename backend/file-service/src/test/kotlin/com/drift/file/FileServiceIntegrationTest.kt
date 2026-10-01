package com.drift.file

import com.drift.file.exceptions.DomainError
import com.drift.file.interfaces.IFileService
import com.drift.file.interfaces.IObjectStorage
import com.drift.file.interfaces.IRoomMembership
import com.drift.file.models.FileKind
import com.drift.file.models.ObjectLocation
import com.drift.file.models.UploadInfo
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.Base64
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class FileServiceIntegrationTest {
    companion object {
        private const val MINIO_USER = "minioadmin"
        private const val MINIO_PASSWORD = "minioadmin"

        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

        @Container
        @ServiceConnection
        val kafka: ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.1"))

        @Container
        val minio: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("chainguard/minio:latest"))
                .withExposedPorts(9000)
                .withEnv("MINIO_ROOT_USER", MINIO_USER)
                .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                .withCommand("server", "/data")
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("drift.storage.minio-endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("drift.storage.minio-access-key") { MINIO_USER }
            registry.add("drift.storage.minio-secret-key") { MINIO_PASSWORD }
        }

        private val png: ByteArray =
            Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
    }

    @Autowired lateinit var files: IFileService

    @Autowired lateinit var storage: IObjectStorage

    @MockkBean lateinit var rooms: IRoomMembership

    private val owner = UUID.randomUUID()
    private val room = UUID.randomUUID()

    @BeforeEach
    fun prepare() {
        storage.ensureBuckets()
        every { rooms.isMember(room, owner) } returns true
        every { rooms.isMember(room, neq(owner)) } returns false
    }

    private fun bigPng(size: Int): ByteArray = ByteArray(size).also { png.copyInto(it) }

    private fun mediaInfo(mime: String = "image/png") = UploadInfo(owner, room, FileKind.MEDIA, "big.png", mime)

    @Test
    fun `a multi-part upload is assembled in MinIO, sniffed and served back byte for byte with ranges`() {
        val content = bigPng(11 * 1024 * 1024)
        val started = files.startMultipart(mediaInfo(), content.size.toLong())

        val partSize = 5 * 1024 * 1024
        content.toList().chunked(partSize).forEachIndexed { index, part ->
            files.uploadPart(started.fileId, started.multipartId, index + 1, part.toByteArray())
        }
        val stored = files.completeMultipart(started.fileId, started.multipartId, content.size.toLong())

        assertThat(stored.mimeType).isEqualTo("image/png")
        assertThat(stored.sizeBytes).isEqualTo(content.size.toLong())

        val whole = files.open(started.fileId, owner, null)
        assertThat(whole.partial).isFalse()
        assertThat(whole.open().use { it.readAllBytes() }).isEqualTo(content)

        val ranged = files.open(started.fileId, owner, "bytes=1000-1999")
        assertThat(ranged.partial).isTrue()
        assertThat(ranged.open().use { it.readAllBytes() }).isEqualTo(content.copyOfRange(1000, 2000))

        val tail = files.open(started.fileId, owner, "bytes=-500")
        assertThat(tail.open().use { it.readAllBytes() }).isEqualTo(content.copyOfRange(content.size - 500, content.size))

        assertThatThrownBy { files.open(started.fileId, UUID.randomUUID(), null) }.isInstanceOf(DomainError.Forbidden::class.java)
    }

    @Test
    fun `a file that says it is a png but is not gets rejected and removed`() {
        val content = ByteArray(6 * 1024 * 1024) { 'A'.code.toByte() } + "PK".toByteArray()
        val started = files.startMultipart(mediaInfo(), content.size.toLong())
        files.uploadPart(started.fileId, started.multipartId, 1, content.copyOfRange(0, 5 * 1024 * 1024))
        files.uploadPart(started.fileId, started.multipartId, 2, content.copyOfRange(5 * 1024 * 1024, content.size))

        assertThatThrownBy { files.completeMultipart(started.fileId, started.multipartId, content.size.toLong()) }
            .isInstanceOf(DomainError.UnsupportedMediaType::class.java)
        assertThat(files.metadata(started.fileId)).isNull()
    }

    @Test
    fun `a size mismatch is rejected`() {
        val content = bigPng(5 * 1024 * 1024 + 10)
        val started = files.startMultipart(mediaInfo(), content.size.toLong() + 5)
        files.uploadPart(started.fileId, started.multipartId, 1, content.copyOfRange(0, 5 * 1024 * 1024))
        files.uploadPart(started.fileId, started.multipartId, 2, content.copyOfRange(5 * 1024 * 1024, content.size))

        assertThatThrownBy { files.completeMultipart(started.fileId, started.multipartId, content.size.toLong() + 5) }
            .isInstanceOf(DomainError.ValidationFailed::class.java)
    }

    @Test
    fun `aborting an upload leaves nothing behind`() {
        val started = files.startMultipart(mediaInfo(), 6L * 1024 * 1024)
        files.uploadPart(started.fileId, started.multipartId, 1, ByteArray(5 * 1024 * 1024))

        files.abortMultipart(started.fileId, started.multipartId)

        assertThat(files.metadata(started.fileId)).isNull()
    }

    @Test
    fun `avatar upload stores a small image any signed-in user can open, and deleting the user removes it`() {
        val stored = files.upload(UploadInfo(owner, null, FileKind.AVATAR, "a.png", "image/png"), png)
        assertThat(stored.mimeType).isEqualTo("image/png")

        val content = files.open(stored.id, UUID.randomUUID(), null)
        assertThat(content.open().use { it.readAllBytes() }).isEqualTo(png)

        assertThat(files.deleteAvatarsOwnedBy(owner)).isEqualTo(1)
        assertThat(files.metadata(stored.id)).isNull()
    }

    @Test
    fun `deleting a room removes its media from storage`() {
        val started = files.startMultipart(mediaInfo(), 5L * 1024 * 1024 + 1)
        val content = bigPng(5 * 1024 * 1024 + 1)
        files.uploadPart(started.fileId, started.multipartId, 1, content.copyOfRange(0, 5 * 1024 * 1024))
        files.uploadPart(started.fileId, started.multipartId, 2, content.copyOfRange(5 * 1024 * 1024, content.size))
        files.completeMultipart(started.fileId, started.multipartId, content.size.toLong())
        val record = files.metadata(started.fileId)!!
        val location = ObjectLocation("drift-media", record.objectKey)
        assertThat(storage.size(location)).isEqualTo(content.size.toLong())

        assertThat(files.deleteByRoom(room)).isGreaterThanOrEqualTo(1)

        assertThat(storage.size(location)).isNull()
    }
}
