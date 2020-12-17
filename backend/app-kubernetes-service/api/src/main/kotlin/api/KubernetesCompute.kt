package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.Compute
import dk.sdu.cloud.app.orchestrator.api.Shells
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod

data class ReloadRequest(val fileLocation: String)
typealias ReloadResponse = Unit

@TSNamespace("compute.ucloud.jobs")
object KubernetesCompute : Compute(UCLOUD_PROVIDER) {
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

@TSNamespace("compute.ucloud.shell")
object AppKubernetesShell : Shells(UCLOUD_PROVIDER)
