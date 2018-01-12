package dk.sdu.cloud.tus.api

import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.JWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTDescriptions
import io.netty.handler.codec.http.HttpMethod
import io.tus.java.client.TusClient
import io.tus.java.client.TusURLMemoryStore
import io.tus.java.client.TusUpload
import io.tus.java.client.TusUploader
import java.io.InputStream
import java.net.URL
import java.util.*

/**
 * Describes the endpoints exposed by TUS. For most use-cases the recommended way to interact with this service is
 * through a TUS-client.
 */
object TusDescriptions : RESTDescriptions(TusServiceDescription) {
    val baseContext = "/api/tus"

    val create = callDescription<CreationCommand, Unit, Unit>(
            additionalRequestConfiguration = { req ->
                addHeader(TusHeaders.Resumable, TusConfiguration.Version)
                addHeader(TusHeaders.UploadLength, req.length)

                val metadata = HashMap<String, String>().apply {
                    if (req.fileName != null) this["filename"] = req.fileName
                    if (req.owner != null) this["owner"] = req.owner
                    if (req.location != null) this["location"] = req.location

                    this["sensitive"] = req.sensitive.toString()
                }

                val encoder = Base64.getEncoder()
                val encodedMetadata = metadata.map {
                    "${it.key} ${encoder.encode(it.value.toByteArray())}"
                }.joinToString(",")
                addHeader(TusHeaders.UploadMetadata, encodedMetadata)
            },

            body = {
                path { using(baseContext) }
                method = HttpMethod.POST
            }
    )

    init {
        // POST endpoint is done by the create from above
        register(baseContext, HttpMethod.OPTIONS)
        register("$baseContext/{id}", HttpMethod.POST)
        register("$baseContext/{id}", HttpMethod.PATCH)
        register("$baseContext/{id}", HttpMethod.HEAD)
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

        store[upload.fingerprint] = URL(endpoint + location)

        val uploader = client.resumeUpload(upload)
        uploader.chunkSize = 1024 * 8
        uploader.requestPayloadSize = payloadSizeMax32Bits // The tus java client really has problems. Limit is stupid
        return uploader
    }
}