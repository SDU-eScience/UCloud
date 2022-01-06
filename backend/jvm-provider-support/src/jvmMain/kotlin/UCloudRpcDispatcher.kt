package dk.sdu.cloud.providers

import dk.sdu.cloud.calls.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

private val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}

abstract class UCloudRpcDispatcher(
    private val container: CallDescriptionContainer,
    private val wsDispatcher: UCloudWsDispatcher,
) : UCloudWSHandler {
    init {
        wsDispatcher.addContainer(container)
        wsDispatcher.addHandler(this) // probably a bad idea

        // Force loading of all calls
        container::class.members.forEach {
            try {
                if (it.returnType.isSubtypeOf(CallDescription::class.starProjectedType) && it.name != "call") {
                    it.call(container)
                }
            } catch (ex: Throwable) {
                println("Unexpected failure: ${container} ${it}. ${ex.stackTraceToString()}")
            }
        }
    }

    @RequestMapping("/**", consumes = [MediaType.ALL_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun handler(request: HttpServletRequest, response: HttpServletResponse): String {
        val path = request.requestURI.removeSuffix("/").removePrefix("/").let { "/$it" }
        val method = request.method

        if (request.getHeader("Upgrade").equals("websocket", ignoreCase = true) &&
            request.getHeader("Connection").equals("upgrade", ignoreCase = true)) {
            // Just in case we receive a WS request (which hasn't been authenticated) we should bail out
            response.sendError(404)
            return ""
        }

        var foundCall: CallDescription<*, *, *>? = null
        inner@for (call in container.callContainer) {
            val http = call.httpOrNull ?: continue@inner
            val expectedPath =
                (http.path.basePath.removeSuffix("/") + "/" +
                    http.path.segments.joinToString("/") {
                        when (it) {
                            is HttpPathSegment.Simple -> it.text
                            else -> error("Unexpected path segment")
                        }
                    }).removePrefix("/").removeSuffix("/").let { "/$it" }

            if (expectedPath != path) continue@inner
            if (method != http.method.value.toUpperCase()) continue@inner

            foundCall = call
            break
        }

        if (foundCall == null) {
            response.sendError(404)
            return ""
        }

        val http = foundCall.http
        val requestMessage = try {
            run {
                if (foundCall.requestType == Unit.serializer()) {
                    return@run Unit
                } else if (http.body is HttpBody.BoundToEntireRequest<*>) {
                    val entireMessage = request.inputStream?.readAllBytes()?.decodeToString()
                    if (entireMessage.isNullOrBlank()) {
                        response.sendError(400)
                        return ""
                    } else {
                        return@run defaultMapper.decodeFromString(foundCall.requestType, entireMessage)
                    }
                } else if (http.params?.parameters?.isNotEmpty() == true) {
                    ParamsParsing(request, foundCall).decodeSerializableValue(foundCall.requestType)
                } else {
                    error("Unable to parse request")
                }
            }
        } catch (ex: Throwable) {
            response.sendError(400)
            return ""
        }

        @Suppress("UNCHECKED_CAST")
        val responseMessage = dispatchToHandler(
            foundCall as CallDescription<Any, *, *>,
            requestMessage,
            request,
            response
        )

        response.contentType = MediaType.APPLICATION_JSON_VALUE
        @Suppress("UNCHECKED_CAST")
        return defaultMapper.encodeToString(foundCall.successType as KSerializer<Any>, responseMessage)
    }

    abstract fun <R : Any, S : Any, E : Any> dispatchToHandler(
        call: CallDescription<R, S, E>,
        request: R,
        rawRequest: HttpServletRequest,
        rawResponse: HttpServletResponse,
    ): S
}
