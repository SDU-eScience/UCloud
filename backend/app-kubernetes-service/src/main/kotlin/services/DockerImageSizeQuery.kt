package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder

class DockerImageSizeQuery {
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            this.socketTimeoutMillis = 1000 * 30
            this.connectTimeoutMillis = 1000 * 30
            this.requestTimeoutMillis = 1000 * 30
        }
        expectSuccess = false
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")
    private fun encodeQueryParamsToString(queryPathMap: Map<String, String>): String {
        return queryPathMap
            .map {
                if (it.value.isEmpty()) {
                    it.key.urlEncode()
                } else {
                    it.key.urlEncode() + "=" + it.value.urlEncode()
                }
            }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""
    }

    private suspend fun fetchToken(realm: String, params: Map<String, String>): String {
        val resp = httpClient.get(realm + encodeQueryParamsToString(params))
        val text = resp.bodyAsChannel().toByteArray(1024 * 1024 * 2).toString(Charsets.UTF_8)
        return defaultMapper.decodeFromString<TokenResponse>(text).access_token
    }

    private data class TokenResponse(var access_token: String)

    private suspend fun queryRegistry(
        tok: TokenResponse,
        url: String,
        method: HttpMethod = HttpMethod.Get,
        attempts: Int = 0
    ): HttpResponse {
        if (attempts > 5) error("too many attempts $url")

        val resp = httpClient.request(url) {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer ${tok.access_token}")
        }

        if (resp.status == HttpStatusCode.Unauthorized) {
            val auth = resp.headers[HttpHeaders.WWWAuthenticate] ?: error("No WWW-Authenticate header")
            val params = auth.removePrefix("Bearer ").split(",").map {
                val (key, value) = it.split("=")
                key to value.removeSurrounding("\"")
            }.toMap().toMutableMap()
            val realm = params.remove("realm") ?: error("no realm")

            tok.access_token = fetchToken(realm, params)
            return queryRegistry(tok, url, method, attempts + 1)
        }

        return resp
    }

    private data class Layer(val blobSum: String)
    private data class DockerManifest(val fsLayers: List<Layer>)

    suspend fun estimateSize(qualifiedImage: String): Long {
        val tok = TokenResponse("")
        val tag = qualifiedImage.substringAfterLast(':')
        val imageWithRegistry = qualifiedImage.substringBeforeLast(':')
        val image = imageWithRegistry.substringAfter('/')
        val registry = imageWithRegistry.substringBefore('/')

        val manifest = defaultMapper.decodeFromString<DockerManifest>(
            queryRegistry(tok, "https://$registry/v2/$image/manifests/$tag")
                .bodyAsChannel()
                .toByteArray(1024 * 1024 * 8)
                .decodeToString()
        )

        return manifest.fsLayers.fold(0L) { acc, layer ->
            acc + fetchLayerSize(tok, BlobReference(registry, image, tag, layer.blobSum))
        }
    }

    private data class BlobReference(
        val registry: String,
        val image: String,
        val tag: String,
        val blobSum: String
    )

    private val layerCache = SimpleCache<BlobReference, Long>(maxAge = SimpleCache.DONT_EXPIRE) { null }
    private suspend fun fetchLayerSize(
        tok: TokenResponse,
        ref: BlobReference
    ): Long {
        with(ref) {
            val cached = layerCache.get(ref)
            if (cached != null) return cached

            val manifest = queryRegistry(
                tok,
                "https://$registry/v2/$image/blobs/$blobSum",
                method = HttpMethod.Head
            )

            if (!manifest.status.isSuccess()) error("bad layer reference")
            val length = manifest.headers[HttpHeaders.ContentLength]?.toLong() ?: error("no length for layer")
            layerCache.insert(ref, length)
            return length
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
