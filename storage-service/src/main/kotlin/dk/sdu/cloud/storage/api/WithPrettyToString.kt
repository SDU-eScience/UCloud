package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

private val prettyMapper = jacksonObjectMapper().apply { configure(SerializationFeature.INDENT_OUTPUT, true) }

interface WithPrettyToString {
    fun toPrettyString(): String = prettyMapper.writeValueAsString(this)!!
}