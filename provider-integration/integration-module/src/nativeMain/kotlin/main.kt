package dk.sdu.cloud

import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.homeDirectory
import dk.sdu.cloud.utils.readText
import h2o.H2O_TOKEN_AUTHORIZATION
import h2o.h2o_find_header
import io.ktor.http.*
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import platform.posix.SIZE_MAX
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main() {
    runBlocking {
        /*
            Streaming uploads and downloads (Fixed)
            ================================================================================================================

            Mongoose does not like large uploads/downloads. Streaming is not possible when uploading, this we can live
            without and just tweak the API to our liking, we probably want the ability to resume in this anyway so let usl
            just design the API around it.

            However, we must make sure that this server is actually capable of streaming out large files. We cannot live
            without this feature.

            Conclusion:
            ----------------------------------------------------------------------------------------------------------------
            It is not an issue to send out large files. Memory usage through uploads is limited to the receiving buffer,
            which is what we want.

            TODO TLS
            ================================================================================================================

            We need to verify that this is actually using a reasonable configuration for TLS. Our options are quite limited
            in what we can tell it to do. We can pass a list of ciphers along with some certificates and keys, that is
            really it.

            TODO Binary
            ================================================================================================================

            All dependencies should be statically linked to make sure that this is easy to deploy.

         */
        val server = H2OServer()
        val value = 2000
        with(server) {
            /*
            implement(CallTest.testQuery) {
                println("Received the following token: ${ctx.bearerOrNull}")
                println("Call made by: ${ctx.securityPrincipalTokenOrNull}")
                println("Request is: $request")
                OutgoingCallResponse.Ok(Unit)
            }

            implement(CallTest.testBody) {
                OutgoingCallResponse.Ok(request.items.first())
            }

            implement(CallTest.testError) {
                throw RPCException("Something didn't work", HttpStatusCode.BadGateway)
            }


             */
            implement(CallTest.testUnit) {
                OutgoingCallResponse.Ok(Unit)
            }

            val jobs = JobsProvider("im-test")
            implement(jobs.retrieveProducts) {
                println("Call made by: ${ctx.securityPrincipalTokenOrNull}")
                throw RPCException("Bailing out", HttpStatusCode.InternalServerError)
            }
        }


        val log = Logger("Main")

        val homeDir = homeDirectory()
        val refreshToken = NativeFile.open("$homeDir/sducloud/refreshtoken.txt", readOnly = true).readText().lines()
            .firstOrNull() ?: throw IllegalStateException("Missing refresh token")
        val certificate = NativeFile.open("$homeDir/sducloud/certificate.txt", readOnly = true).readText()
            .replace("\n", "")
            .replace("\r", "")
            .removePrefix("-----BEGIN PUBLIC KEY-----")
            .removeSuffix("-----END PUBLIC KEY-----")
            .chunked(64)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .let { "-----BEGIN PUBLIC KEY-----\n" + it + "\n-----END PUBLIC KEY-----" }

        val validation = NativeJWTValidation(certificate)

        addMiddleware(object : Middleware {
            override fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
                val ctx = handler.ctx.serverContext as H2OCtx
                val req = ctx.reqPtr.pointed
                val res = h2o_find_header(req.headers.ptr, H2O_TOKEN_AUTHORIZATION, SIZE_MAX.toLong())
                if (res != SIZE_MAX.toLong()) {
                    val header = req.headers.entries?.get(res)?.value
                    val authorizationHeader = header?.base?.readBytes(header.len.toInt())?.decodeToString()
                    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return
                    handler.ctx.bearerOrNull = authorizationHeader.removePrefix("Bearer ")
                }
            }
        })

        addMiddleware(object : Middleware {
            override fun <R : Any> beforeRequest(handler: CallHandler<R, *, *>) {
                val bearer = handler.ctx.bearerOrNull
                val token = if (bearer != null) {
                    val token = validation.validateOrNull(bearer)
                    handler.ctx.securityPrincipalTokenOrNull = token
                    token
                } else {
                    null
                }

                val principal = token?.principal

                val auth = handler.description.authDescription
                if (auth.roles != Roles.PUBLIC && principal == null) {
                    log.debug("Princpal was null")
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                } else if (principal != null && principal.role !in auth.roles) {
                    log.debug("Role is not authorized ${principal}")
                    throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
                }
            }
        })


        val client = RpcClient().also { client ->
            OutgoingHttpRequestInterceptor()
                .install(
                    client,
                    FixedOutgoingHostResolver(HostInfo("localhost", "http", 8080))
                )
        }

        val authenticator = RefreshingJWTAuthenticator(
            client,
            JwtRefresher.Normal(refreshToken),
            becomesInvalidSoon = { accessToken ->
                val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
                (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                    (1000 * 120) >= Time.now()
            }
        )

        val providerClient = authenticator.authenticateClient(OutgoingHttpCall)

        server.start()
    }
}

@SharedImmutable
val bearerKey = AttributeKey<String>("bearer").also { it.freeze() }
var IngoingCall<*>.bearerOrNull: String?
    get() = attributes.getOrNull(bearerKey)
    set(value) {
        attributes.setOrDelete(bearerKey, value)
    }

@SharedImmutable
val securityPrincipalTokenKey = AttributeKey<SecurityPrincipalToken>("principalToken").also { it.freeze() }
var IngoingCall<*>.securityPrincipalTokenOrNull: SecurityPrincipalToken?
    get() = attributes.getOrNull(securityPrincipalTokenKey)
    set(value) {
        attributes.setOrDelete(securityPrincipalTokenKey, value)
    }

val IngoingCall<*>.securityPrincipal: SecurityPrincipal
    get() = securityPrincipalTokenOrNull?.principal ?: error("User is not authenticated")

@Serializable
data class TestRequest(val a: Int, val b: Boolean, val c: String)

@OptIn(ExperimentalStdlibApi::class)
object CallTest : CallDescriptionContainer("test") {
    const val baseContext = "/ucloud/test"

    init {
        println("init()")
    }

    val testQuery = call<TestRequest, Unit, CommonErrorMessage>("testQuery") {
        println("testQuery")
        httpRetrieve(baseContext)
    }

    val testBody = call<BulkRequest<TestRequest>, TestRequest, CommonErrorMessage>("testBody") {
        httpUpdate(baseContext, "body")
    }

    val testError = call<TestRequest, Unit, CommonErrorMessage>("testError") {
        httpRetrieve(baseContext, "error")
    }

    val testUnit = call<Unit, Unit, CommonErrorMessage>("testUnit") {
        httpRetrieve(baseContext, "unit")
    }
}