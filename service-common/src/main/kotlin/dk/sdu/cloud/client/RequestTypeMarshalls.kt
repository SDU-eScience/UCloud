package dk.sdu.cloud.client

import dk.sdu.cloud.service.InputParsingResponse
import io.ktor.application.ApplicationCall
import io.ktor.pipeline.PipelineContext

interface RequestPathSegmentMarshall<R> {
    fun serializePathSegment(segment: RESTPathSegment<*>, value: R): String
}

interface RequestQueryParamMarshall<R> {
    fun serializeQueryParam(param: RESTQueryParameter<*>, value: R): Pair<String, String>
}

interface RequestBodyParamMarshall<R : Any> {
    suspend fun deserializeBody(
        description: RESTCallDescription<*, *, *, *>,
        context: PipelineContext<*, ApplicationCall>
    ): InputParsingResponse

    fun serializeBody(description: RESTCallDescription<*, *, *, *>, body: RESTBody<*, R>, value: R): Any
}
