package dk.sdu.cloud.micro

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.TokenValidation
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType

sealed class ServerConfigurationException(why: String, status: HttpStatusCode) : RPCException(why, status) {
    class MissingNode(path: String) :
        ServerConfigurationException("Missing configuration at $path", HttpStatusCode.InternalServerError)
}

class JacksonServerConfiguration(
    private val jsonMapper: ObjectMapper,
    val tree: JsonNode,
    val configDirs: List<File>
) : ServerConfiguration {
    override fun <T : Any> requestChunk(valueTypeRef: KClass<T>, node: String): T {
        val jsonNode =
            tree.get(node)?.takeIf { !it.isMissingNode }
                ?: throw ServerConfigurationException.MissingNode(node)
        return jsonNode.traverse(jsonMapper).readValueAs(valueTypeRef.java)
    }

    override fun <T : Any> requestChunkAt(valueTypeRef: KClass<T>, vararg path: String): T {
        val jsonNode = tree.at("/" + path.joinToString("/"))?.takeIf { !it.isMissingNode }
            ?: throw ServerConfigurationException.MissingNode(path.joinToString("/"))
        return jsonNode.traverse(jsonMapper).readValueAs(valueTypeRef.java)
    }

    fun <T> requestChunk(valueTypeRef: TypeReference<T>, node: String): T {
        val jsonNode =
            tree.get(node)?.takeIf { !it.isMissingNode }
                ?: throw ServerConfigurationException.MissingNode(node)
        return jsonNode.traverse(jsonMapper).readValueAs<T>(valueTypeRef)
    }

    fun <T> requestChunkAt(valueTypeRef: TypeReference<T>, vararg path: String): T {
        val jsonNode = tree.at("/" + path.joinToString("/"))?.takeIf { !it.isMissingNode }
            ?: throw ServerConfigurationException.MissingNode(path.joinToString("/"))
        return jsonNode.traverse(jsonMapper).readValueAs<T>(valueTypeRef)
    }
}

inline fun <reified T : Any> ServerConfiguration.requestChunk(node: String): T {
    require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
    return requestChunk(jacksonTypeRef(), node)
}

inline fun <reified T : Any> ServerConfiguration.requestChunkAt(vararg path: String): T {
    require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
    return requestChunkAt(jacksonTypeRef(), *path)
}

inline fun <reified T : Any> ServerConfiguration.requestChunkOrNull(node: String): T? {
    require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
    return try {
        requestChunk(node)
    } catch (ex: ServerConfigurationException.MissingNode) {
        null
    }
}

inline fun <reified T : Any> ServerConfiguration.requestChunkAtOrNull(vararg path: String): T? {
    require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
    return try {
        requestChunkAt(*path)
    } catch (ex: ServerConfigurationException.MissingNode) {
        null
    }
}

val ServerConfiguration.tree: JsonNode
    get() {
        require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
        return tree
    }

val ServerConfiguration.configDirs: List<File>
    get() {
        require(this is JacksonServerConfiguration) { "Error! See interface notes on ServerConfiguration" }
        return configDirs
    }

