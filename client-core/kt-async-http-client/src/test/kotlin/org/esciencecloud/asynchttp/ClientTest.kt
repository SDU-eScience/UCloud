package org.esciencecloud.asynchttp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Test

import org.hamcrest.CoreMatchers.*

class ClientTest {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BinGet(val origin: String, val url: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class BinPost<out T>(val json: T)

    data class DummyData(val a: Int, val b: Boolean, val c: String)

    @Test
    fun testJsonPostAndDeserialize() {
        val payload = DummyData(42, true, "Hello")
        val response = runBlocking {
            HttpClient.post("https://httpbin.org/post") {
                setJsonBody(payload)
            }.asJson<BinPost<DummyData>>()
        }
        assertEquals(payload, response.json)
    }

    @Test
    fun testGet() {
        val url = "https://httpbin.org/get"
        val response = runBlocking { HttpClient.get(url) }
        assertEquals(url, response.asJson<BinGet>().url)
        assertEquals(200, response.statusCode)
    }

    @Test
    fun testAuth() {
        val response = runBlocking {
            HttpClient.get("https://httpbin.org/basic-auth/abc/def") {
                addBasicAuth("abc", "def")
            }
        }

        assertThat(response.responseBody, containsString("\"authenticated\": true"))
    }

    @Test
    fun testPut() {
        val response = runBlocking { HttpClient.put("https://httpbin.org/put") }
        assertEquals(200, response.statusCode)
    }

    @Test
    fun testPatch() {
        val response = runBlocking { HttpClient.patch("https://httpbin.org/patch") }
        assertEquals(200, response.statusCode)
    }

    @Test
    fun testDelete() {
        val response = runBlocking { HttpClient.delete("https://httpbin.org/delete") }
        assertEquals(200, response.statusCode)
    }
}