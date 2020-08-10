package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.app.orchestrator.api.ComputationDescriptions
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod

data class ReloadRequest(val fileLocation: String)
typealias ReloadResponse = Unit

object AppKubernetesDescriptions : ComputationDescriptions("kubernetes") {
    val reload = call<ReloadRequest, ReloadResponse, CommonErrorMessage>("reload") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"reload-k8s"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
