package dk.sdu.cloud.micro

import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.TokenValidation
import kotlin.reflect.KClass
import kotlin.reflect.KType

// Common features which are needed only as a hack to make existing code work

val Micro.client: RpcClient
    get() = get(MicroAttributeKey<ClientFeatureBase>("client-feature")).client


interface ClientFeatureBase : MicroFeature {
    val client: RpcClient
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

private val CONFIG_KEY = MicroAttributeKey<ServerConfiguration>("server-configuration")
var Micro.configuration: ServerConfiguration
    get() = attributes[CONFIG_KEY]
    set(value) {
        attributes[CONFIG_KEY] = value
    }

// NOTE(Dan): This interface is left here only to get the jackson dependency out of this library. There is meant to
// be only one implementation which is the Jackson one. This interface is vastly simplified but is only needed for
// auth at the moment.
interface ServerConfiguration {
    fun <T : Any> requestChunk(valueTypeRef: KClass<T>, node: String): T
    fun <T : Any> requestChunkAt(valueTypeRef: KClass<T>, vararg path: String): T
}
