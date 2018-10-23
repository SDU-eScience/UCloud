package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.client.MultipartRequest
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.jwtAuth
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test

data class Wrapper<T>(val value: T)

data class JsonPayload(
    val foo: Int,
    val bar: String,
    val list: List<Wrapper<String>>
)

data class FormRequest(
    val normal: String,
    val json: JsonPayload
)

object MultipartDescriptions : RESTDescriptions("foo") {
    val multipart = callDescription<MultipartRequest<FormRequest>, Unit, Unit> {
        name = "multipart"
        auth {
            access = AccessRight.READ
        }

        path {
            +"foo"
        }

        body { bindEntireRequestFromBody() }
    }
}

class MultipartTest {
    @Test
    fun `test some stuff`() {
        val cloud = SDUCloud("http://127.0.0.1:8080").jwtAuth("token")

        runBlocking {
            MultipartDescriptions.multipart.call(
                MultipartRequest(
                    FormRequest(
                        "testing",
                        JsonPayload(42, "bar", listOf("a", "b", "c").map { Wrapper(it) })
                    )
                ),
                cloud
            )
        }
    }
}
