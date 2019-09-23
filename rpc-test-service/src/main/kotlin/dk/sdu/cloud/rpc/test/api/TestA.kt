package dk.sdu.cloud.rpc.test.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.websocket
import io.ktor.http.HttpMethod

data class StartRequest(val parallelism: Int? = null, val ws: Boolean? = null)

object TestA : CallDescriptionContainer("rpc.test") {
    private const val baseContext = "/api/rpc/test"

    val ping = call<StartRequest, Unit, CommonErrorMessage>("ping") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"ping"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val processing = call<StartRequest, Unit, CommonErrorMessage>("processing") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"processing"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val processingSuspend = call<StartRequest, Unit, CommonErrorMessage>("processingSuspend") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"processingSuspend"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val processingSuspend2 = call<StartRequest, Unit, CommonErrorMessage>("processingSuspend2") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"processingSuspend2"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val pingSelf = call<Unit, FindByStringId, CommonErrorMessage>("pingSelf") {
        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
    }
}
