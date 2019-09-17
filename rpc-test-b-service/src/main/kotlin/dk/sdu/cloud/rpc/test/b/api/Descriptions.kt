package dk.sdu.cloud.rpc.test.b.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.websocket
import io.ktor.http.HttpMethod

data class PingRequest(val ping: String)
data class PingResponse(val pong: String)

object TestB : CallDescriptionContainer("rpc.test.b") {
    private val baseContext = "/api/rpc/test/b"

    val ping = call<PingRequest, PingResponse, CommonErrorMessage>("ping") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        websocket(baseContext)

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"ping"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val processing = call<Unit, Unit, CommonErrorMessage>("processing") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        websocket(baseContext)

        http {
            method = HttpMethod.Post
            
            path {
                using(baseContext)
                +"processing"
            }
        }
    }

    val suspendingProcessing =
        call<Unit, Unit, CommonErrorMessage>("suspendingProcessing") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ_WRITE
            }

            websocket(baseContext)

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"suspendingProcessing"
                }
            }
        }

    val suspendingProcessing2 =
        call<Unit, Unit, CommonErrorMessage>("suspendingProcessing2") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ_WRITE
            }

            websocket(baseContext)

            http {
                method = HttpMethod.Post

                path {
                    using(baseContext)
                    +"suspendingProcessing2"
                }
            }
        }
}
