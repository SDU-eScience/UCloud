package dk.sdu.cloud.client

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

var defaultMapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
    configure(JsonParser.Feature.ALLOW_COMMENTS, true)
}
