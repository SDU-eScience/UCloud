package org.esciencecloud.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.ktor.cio.toInputStream
import org.jetbrains.ktor.client.DefaultHttpClient
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpHeaders
import java.net.URL

object JsonHttpClient {
    val _mapper = jacksonObjectMapper()

    suspend inline fun <reified ReturnType : Any> get(url: String): ReturnType {
        val response = DefaultHttpClient.request(URL(url)) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }

        return _mapper.readValue(response.channel.toInputStream())
    }

    suspend inline fun <reified ReturnType : Any> post(url: String, payload: Any? = null): ReturnType {
        val response = DefaultHttpClient.request(URL(url)) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            if (payload != null) {
                body = { _mapper.writeValue(it, payload) }
            }
        }

        return _mapper.readValue(response.channel.toInputStream())
    }
}