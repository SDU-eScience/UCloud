package dk.sdu.cloud.app.kubernetes.watcher.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.events.EventStreamContainer
import io.ktor.http.HttpMethod

data class JobCondition(
    val type: String?,
    val reason: String?,
    val isActive: Boolean = false,
    val isFailed: Boolean = false
)

data class JobEvent(
    val jobName: String,
    val condition: JobCondition?
)

object JobEvents : EventStreamContainer() {
    val events = stream<JobEvent>("app-kubernetes-job-events", { it.jobName })
}

typealias ReloadRequest = Unit
typealias ReloadResponse = Unit

object AppKubernetesWatcher : CallDescriptionContainer("k8watcher") {
    val reload = call<ReloadRequest, ReloadResponse, CommonErrorMessage>("reload") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using("/api/kubernetes/watcher")
                +"reload-k8s"
            }
        }
    }
}
