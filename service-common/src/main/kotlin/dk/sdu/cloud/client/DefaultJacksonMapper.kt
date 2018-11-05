package dk.sdu.cloud.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

var defaultMapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
