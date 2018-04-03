package dk.sdu.cloud.tus.api

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTDescriptions
import io.netty.handler.codec.http.HttpMethod
import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import io.tus.java.client.TusUploader
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URL
import java.util.*

data class UploadSummary(
    val id: String,
    val length: Long,
    val offset: Long,
    val savedAs: String?
)

data class UploadState(
    val id: String,

    val length: Long,
    val offset: Long,

    val user: String,
    val zone: String,

    val targetCollection: String,
    val targetName: String
)

data class UploadCreationCommand(
    val fileName: String?,
    val sensitive: Boolean,
    val owner: String?,
    val location: String?,
    val length: Long
)

/**
 * Describes the endpoints exposed by TUS. For most use-cases the recommended way to interact with this service is
 * through a TUS-client.
 */
object TusDescriptions : RESTDescriptions(TusServiceDescription) {
    val baseContext = "/api/tus"

    val create = callDescription<UploadCreationCommand, Unit, Unit>(
        additionalRequestConfiguration = { req ->
            addHeader(TusHeaders.Resumable, TUS_VERSION)
            addHeader(TusHeaders.UploadLength, req.length)

            val metadata = HashMap<String, String>().apply {
                if (req.fileName != null) this["filename"] = req.fileName
                if (req.owner != null) this["owner"] = req.owner
                if (req.location != null) this["location"] = req.location

                this["sensitive"] = req.sensitive.toString()
            }

            val encoder = Base64.getEncoder()
            val encodedMetadata = metadata.map {
                "${it.key} ${String(encoder.encode(it.value.toByteArray()))}"
            }.joinToString(",")
            addHeader(TusHeaders.UploadMetadata, encodedMetadata)
        },

        body = {
            prettyName = "tusCreate"
            path { using(baseContext) }
            method = HttpMethod.POST
        }
    )

    val probeTusConfiguration = callDescription<Unit, Unit, Unit> {
        prettyName = "tusOptions"
        path { using(baseContext) }
        method = HttpMethod.OPTIONS
    }

    val findUploadStatusById = callDescription<FindByStringId, Unit, Unit> {
        prettyName = "tusFindUploadStatusById"
        method = HttpMethod.HEAD
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }

    val uploadChunkViaPost = callDescription<FindByStringId, Unit, Unit> {
        prettyName = "tusUpload"
        method = HttpMethod.POST
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }

    val uploadChunk = callDescription<FindByStringId, Unit, Unit> {
        prettyName = "tusUpload"
        method = HttpMethod.PATCH
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }

    // Uploader
    fun uploader(
        inputStream: InputStream,
        location: String,
        payloadSizeMax32Bits: Int,
        cloud: JWTAuthenticatedCloud
    ): TusUploader {
        return uploader(inputStream, location, payloadSizeMax32Bits, cloud.parent, cloud.token)
    }

    fun uploader(
        inputStream: InputStream,
        location: String,
        payloadSizeMax32Bits: Int,
        cloud: CloudContext,
        token: String
    ): TusUploader {
        log.debug("Creating uploader: $inputStream, $location, $payloadSizeMax32Bits, $cloud, $token")
        val endpoint = cloud.resolveEndpoint(owner)
        val store = TusURLMemoryStore()
        val client = TusClient().apply {
            headers = mapOf("Authorization" to "Bearer $token")
            enableResuming(store)
        }

        val upload = TusUpload().apply {
            this@apply.inputStream = inputStream
            fingerprint = "file" // We create a new store every time.
            metadata = emptyMap()
        }

        log.debug("Using $endpoint")

        store[upload.fingerprint] = URL(endpoint + location)

        val uploader = client.resumeUpload(upload)
        uploader.chunkSize = 1024 * 8
        uploader.requestPayloadSize = payloadSizeMax32Bits // The tus java client really has problems. Limit is stupid
        return uploader
    }

    init {
        register("/api/tus/{id}", HttpMethod.OPTIONS) // Needed for CORS
    }

    private val log = LoggerFactory.getLogger(TusDescriptions::class.java)
    public const val TUS_VERSION = "1.0.0"
}