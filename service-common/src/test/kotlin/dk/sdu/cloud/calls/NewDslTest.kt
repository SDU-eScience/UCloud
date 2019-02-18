package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Roles
import io.ktor.http.HttpMethod
import java.time.Period

data class FooRequest(val a: Int)
data class FooResponse(val b: Int)
data class FooError(val c: Int)
data class FooAudit(val d: Int)

object NewDescriptions : CallDescriptionContainer("namespace") {
    val foo = call<FooRequest, FooResponse, FooError>("foo") {
        http {
            method = HttpMethod.Post

            path {
                +"echo"
            }

            body { bindEntireRequestFromBody() }
        }

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        audit<FooAudit> {
            retentionPeriod = Period.ofWeeks(2)
        }
    }
}
