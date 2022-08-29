package dk.sdu.cloud.app.store.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val yamlMapper by lazy { createYamlMapper() }
fun createYamlMapper(): ObjectMapper = ObjectMapper(YAMLFactory()).apply {
    registerKotlinModule()
    configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
}
