package org.esciencecloud.transactions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.ktor.application.ApplicationCallPipeline
import org.jetbrains.ktor.application.ApplicationFeature
import org.jetbrains.ktor.content.FinalContent
import org.jetbrains.ktor.content.IncomingContent
import org.jetbrains.ktor.content.readText
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.withCharset
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.ApplicationReceivePipeline
import org.jetbrains.ktor.request.ApplicationReceiveRequest
import org.jetbrains.ktor.request.acceptItems
import org.jetbrains.ktor.request.contentType
import org.jetbrains.ktor.response.ApplicationSendPipeline
import org.jetbrains.ktor.response.contentLength
import org.jetbrains.ktor.response.contentType
import org.jetbrains.ktor.util.AttributeKey
import org.jetbrains.ktor.util.ValuesMap

class JacksonSupport(val mapper: ObjectMapper) {
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, ObjectMapper, JacksonSupport> {
        override val key = AttributeKey<JacksonSupport>("mapper")

        override fun install(pipeline: ApplicationCallPipeline, configure: ObjectMapper.() -> Unit): JacksonSupport {
            val mapper = jacksonObjectMapper().apply(configure)
            val feature = JacksonSupport(mapper)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Render) {
                if (it !is FinalContent && call.request.acceptItems().any { ContentType.Application.Json.match(it.value) }) {
                    proceedWith(feature.renderJsonContent(this, it))
                }
            }
            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                if (call.request.contentType().match(ContentType.Application.Json)) {
                    val message = it.value as? IncomingContent ?: return@intercept
                    val json = message.readText()
                    val value = mapper.readValue(json, it.type.javaObjectType)
                    proceedWith(ApplicationReceiveRequest(it.type, value))
                }
            }
            return feature
        }
    }

    private fun renderJsonContent(context: PipelineContext<*>, model: Any): JsonContent {
        val json = mapper.writeValueAsString(model)
        val status = context.call.response.status() ?: HttpStatusCode.OK
        return JsonContent(json, status)
    }
}

private class JsonContent(val text: String, override val status: HttpStatusCode? = null) : FinalContent.ByteArrayContent() {
    private val bytes by lazy { text.toByteArray(Charsets.UTF_8) }

    override val headers by lazy {
        ValuesMap.build(true) {
            contentType(contentType)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes
    override fun toString() = "JsonContent \"${text.take(30)}\""

    companion object {
        private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)
    }
}
