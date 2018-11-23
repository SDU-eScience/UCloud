package dk.sdu.cloud.client

import dk.sdu.cloud.service.InputParsingResponse
import io.ktor.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext

interface RequestPathSegmentMarshall<R> {
    fun deserializeSegment(segment: RESTPathSegment<*>, call: ApplicationCall): Pair<String, Any?>?
    fun serializePathSegment(segment: RESTPathSegment<*>, value: R): String
}

interface RequestQueryParamMarshall<R> {
    fun deserializeQueryParam(segment: RESTQueryParameter<*>, call: ApplicationCall): Pair<String, Any?>?
    fun serializeQueryParam(param: RESTQueryParameter<*>, value: R): Pair<String, String>
}

interface RequestBodyParamMarshall<R : Any> {
    suspend fun deserializeBody(
        description: RESTCallDescription<*, *, *, *>,
        context: PipelineContext<*, ApplicationCall>
    ): InputParsingResponse

    fun serializeBody(description: RESTCallDescription<*, *, *, *>, body: RESTBody<*, R>, value: R): Any
}
