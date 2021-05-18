package dk.sdu.cloud.micro

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.TokenValidation
import io.ktor.http.*
import java.io.File

// Common features which are needed only as a hack to make existing code work

val Micro.client: RpcClient
    get() = get(MicroAttributeKey<ClientFeatureBase>("client-feature")).client


interface ClientFeatureBase : MicroFeature {
    val client: RpcClient
}

sealed class ServerConfigurationException(why: String, status: HttpStatusCode) : RPCException(why, status) {
    class MissingNode(path: String) :
        ServerConfigurationException("Missing configuration at $path", HttpStatusCode.InternalServerError)
}


private val CONFIG_KEY = MicroAttributeKey<ServerConfiguration>("server-configuration")
var Micro.configuration: ServerConfiguration
    get() = attributes[CONFIG_KEY]
    set(value) {
        attributes[CONFIG_KEY] = value
    }


class ServerConfiguration(
    private val jsonMapper: ObjectMapper,
    val tree: JsonNode,
    val configDirs: List<File>
) {
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

    inline fun <reified T : Any> requestChunk(node: String): T {
        return requestChunk(jacksonTypeRef(), node)
    }

    inline fun <reified T : Any> requestChunkAt(vararg path: String): T {
        return requestChunkAt(jacksonTypeRef(), *path)
    }

    inline fun <reified T : Any> requestChunkOrNull(node: String): T? {
        return try {
            requestChunk(node)
        } catch (ex: ServerConfigurationException.MissingNode) {
            null
        }
    }

    inline fun <reified T : Any> requestChunkAtOrNull(vararg path: String): T? {
        return try {
            requestChunkAt(*path)
        } catch (ex: ServerConfigurationException.MissingNode) {
            null
        }
    }
}

private val tokenValidationKey = MicroAttributeKey<TokenValidation<Any>>("token-validation")
var Micro.tokenValidation: TokenValidation<Any>
    get() = attributes[tokenValidationKey]
    set (value) {
        attributes[tokenValidationKey] = value
    }

private val providerTokenValidationKey = MicroAttributeKey<TokenValidation<Any>>("provider-token-validation")
var Micro.providerTokenValidation: TokenValidation<Any>
    get() = attributes[providerTokenValidationKey]
    set (value) {
        attributes[providerTokenValidationKey] = value
    }

