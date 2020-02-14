package dk.sdu.cloud.k8

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

val defaultMapper = jacksonObjectMapper()
val yamlMapper = YAMLMapper().registerKotlinModule()
