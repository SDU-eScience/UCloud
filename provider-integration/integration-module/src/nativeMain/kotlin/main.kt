package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.orchestrator.api.*
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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import platform.posix.SIZE_MAX
import platform.posix.sleep
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class, ExperimentalUnsignedTypes::class)
fun main() {
    runBlocking {
        /*
            TODO Binary
            ================================================================================================================

            All dependencies should be statically linked to make sure that this is easy to deploy.

         */

        val server = H2OServer()

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
                when (val ctx = handler.ctx.serverContext) {
                    is HttpContext -> {
                        val req = ctx.reqPtr.pointed
                        val res = h2o_find_header(req.headers.ptr, H2O_TOKEN_AUTHORIZATION, SIZE_MAX.toLong())
                        if (res != SIZE_MAX.toLong()) {
                            val header = req.headers.entries?.get(res)?.value
                            val authorizationHeader = header?.base?.readBytes(header.len.toInt())?.decodeToString()
                            if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return
                            handler.ctx.bearerOrNull = authorizationHeader.removePrefix("Bearer ")
                        }
                    }

                    is WebSocketContext<*, *, *> -> {
                        handler.ctx.bearerOrNull = ctx.rawRequest.bearer
                    }
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
                    log.debug("Principal was null")
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
            JwtRefresher.Provider(refreshToken),
            becomesInvalidSoon = { accessToken ->
                val expiresAt = validation.validateOrNull(accessToken)?.expiresAt
                (expiresAt ?: return@RefreshingJWTAuthenticator true) +
                    (1000 * 120) >= Time.now()
            }
        )

        val providerClient = authenticator.authenticateClient(OutgoingHttpCall)

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

            val PROVIDER_ID = "im-test"
            val jobs = JobsProvider(PROVIDER_ID)
            val knownProducts = listOf(
                ProductReference("im1", "im1", PROVIDER_ID),
            )

            implement(jobs.create) {
                sleep(2)
                runBlocking {
                    JobsControl.update.call(
                        bulkRequestOf(request.items.map { job ->
                            JobsControlUpdateRequestItem(
                                job.id,
                                JobState.RUNNING,
                                "We are now running!"
                            )
                        }),
                        providerClient
                    ).orThrow()
                }

                OutgoingCallResponse.Ok(Unit)
            }

            implement(jobs.retrieveProducts) {
                OutgoingCallResponse.Ok(
                    JobsProviderRetrieveProductsResponse(
                        knownProducts.map { productRef ->
                            ComputeProductSupport(
                                productRef,
                                ComputeSupport(
                                    ComputeSupport.Docker(
                                        enabled = true,
                                        terminal = true,
                                        logs = true,
                                        timeExtension = false
                                    ),
                                    ComputeSupport.VirtualMachine(
                                        enabled = false,
                                    )
                                )
                            )
                        }
                    )
                )
            }

            implement(jobs.delete) {
                runBlocking {
                    JobsControl.update.call(
                        bulkRequestOf(request.items.map { job ->
                            JobsControlUpdateRequestItem(
                                job.id,
                                JobState.SUCCESS,
                                "We are no longer running!"
                            )
                        }),
                        providerClient
                    ).orThrow()
                }

                OutgoingCallResponse.Ok(Unit)
            }

            implement(jobs.extend) {
                runBlocking {
                    log.info("Extending some jobs: $request")
                    JobsControl.update.call(
                        bulkRequestOf(request.items.map { requestItem ->
                            JobsControlUpdateRequestItem(
                                requestItem.job.id,
                                status = "We have extended your requestItem with ${requestItem.requestedTime}"
                            )
                        }),
                        providerClient
                    ).orThrow()
                }

                OutgoingCallResponse.Ok(Unit)
            }

            val maxStreams = 1024 * 32
            val streams = atomicArrayOfNulls<Unit>(maxStreams)

            implement(jobs.follow) {
                // TODO TODO TODO STREAM IDS NEEDS TO BE UNGUESSABLE THIS ALLOWS ANYONE TO CANCEL OTHER PEOPLES STREAMS
                when (request) {
                    is JobsProviderFollowRequest.Init -> {
                        var streamId: Int? = null
                        for (i in 0 until maxStreams) {
                            if (streams[i].compareAndSet(null, Unit)) {
                                streamId = i
                                break
                            }
                        }

                        if (streamId == null) {
                            throw RPCException("Server is too busy", HttpStatusCode.BadGateway)
                        }

                        wsContext.sendMessage(JobsProviderFollowResponse(streamId.toString(), -1))

                        var counter = 0
                        while (streams[streamId].compareAndSet(Unit, Unit) && wsContext.isOpen()) {
                            println("Message: $streamId")
                            wsContext.sendMessage(JobsProviderFollowResponse(
                                streamId.toString(),
                                0,
                                "Hello, World! $counter\n"
                            ))
                            counter++
                            sleep(1)
                        }

                        OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                    }
                    is JobsProviderFollowRequest.CancelStream -> {
                        val id = request.streamId.toIntOrNull()
                            ?: throw RPCException("Bad stream id", HttpStatusCode.BadRequest)
                        if (id !in 0 until maxStreams) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                        streams[id].compareAndSet(Unit, null)
                        OutgoingCallResponse.Ok(JobsProviderFollowResponse("", -1))
                    }
                }
            }

            implement(jobs.openInteractiveSession) {
                log.info("open interactive session $request")
                TODO()
            }

            implement(jobs.retrieveUtilization) {
                OutgoingCallResponse.Ok(
                    JobsProviderUtilizationResponse(CpuAndMemory(0.0, 0L), CpuAndMemory(0.0, 0L), QueueStatus(0, 0))
                )
            }

            implement(jobs.suspend) {
                log.info("Suspending jobs $request")
                OutgoingCallResponse.Ok(Unit)
            }

            implement(jobs.verify) {
                log.info("Verifying some jobs $request")
                OutgoingCallResponse.Ok(Unit)
            }
        }


        server.start()
    }
}

@Suppress("UNCHECKED_CAST")
val <S : Any> CallHandler<*, S, *>.wsContext: WebSocketContext<*, S, *>
    get() = ctx.serverContext as WebSocketContext<*, S, *>

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

    val testQuery = call<TestRequest, Unit, CommonErrorMessage>("testQuery") {
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
