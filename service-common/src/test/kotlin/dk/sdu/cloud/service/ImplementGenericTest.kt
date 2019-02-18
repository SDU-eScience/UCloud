package dk.sdu.cloud.service

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PROP_TYPE = "type"

// Covers issue #291
class ImplementGenericTest {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = PROP_TYPE
    )
    @JsonSubTypes(
        JsonSubTypes.Type(SealedType.A::class, name = "a"),
        JsonSubTypes.Type(SealedType.B::class, name = "b")
    )
    sealed class SealedType {
        data class A(val a: String) : SealedType()
        data class B(val b: Int) : SealedType()
    }

    object GenericDescriptions : CallDescriptionContainer("generic") {
        val call = call<Unit, Page<SealedType>, Unit>("call") {
            auth {
                roles = Roles.PUBLIC
                access = AccessRight.READ
            }

            http {
                path {
                    using("/")
                }
            }
        }
    }


    class GenericController : Controller {
        override fun configure(rpcServer: RpcServer) = with(rpcServer) {
            implement(GenericDescriptions.call) {
                ok(
                    listOf(
                        SealedType.A("a"),
                        SealedType.B(42)
                    ).paginate(PaginationRequest().normalize())
                )
            }
        }
    }

    @Test
    fun `test that generic type erasure does not occur`() {
        withKtorTest(
            setup = { listOf(GenericController()) },
            test = {
                with(engine) {
                    val response = handleRequest(HttpMethod.Get, "/")
                    assertTrue(response.requestHandled)
                    assertTrue(
                        ContentType.parse(response.response.headers[HttpHeaders.ContentType]!!).match(
                            ContentType.Application.Json
                        )
                    )

                    val tree =
                        defaultMapper.readTree(response.response.content)?.takeIf { !it.isNull && !it.isMissingNode }
                    tree!!

                    println(tree)
                    val items = tree["items"]?.takeIf { !it.isNull && !it.isMissingNode }!!
                    assertEquals(2, items.size())
                    assertEquals("a", items[0][PROP_TYPE].asText())
                    assertEquals("b", items[1][PROP_TYPE].asText())
                }
            }
        )
    }
}
