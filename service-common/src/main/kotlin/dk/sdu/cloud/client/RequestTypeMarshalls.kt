package dk.sdu.cloud.client

interface RequestPathSegmentMarshall<R> {
    fun serializePathSegment(segment: RESTPathSegment<*>, value: R): String
}

interface RequestQueryParamMarshall<R> {
    fun serializeQueryParam(param: RESTQueryParameter<*>, value: R): Pair<String, String>
}

interface RequestBodyParamMarshall<R : Any> {
    fun serializeBody(description: RESTCallDescription<*, *, *, *>, body: RESTBody<*, R>, value: R): Any
}
