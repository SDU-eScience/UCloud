package dk.sdu.cloud.app.store.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val yamlMapper by lazy { createYamlMapper() }
fun createYamlMapper(): ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
