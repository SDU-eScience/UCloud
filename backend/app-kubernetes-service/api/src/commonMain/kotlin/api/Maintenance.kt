package dk.sdu.cloud.app.kubernetes.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import kotlinx.serialization.Serializable

@Serializable
data class KillJobRequest(val jobId: String)
typealias KillJobResponse = Unit

@Serializable
data class UpdatePauseStateRequest(val paused: Boolean)
typealias UpdatePauseStateResponse = Unit

@Serializable
data class DrainNodeRequest(val node: String)
typealias DrainNodeResponse = Unit

typealias DrainClusterRequest = Unit
typealias DrainClusterResponse = Unit

typealias IsPausedRequest = Unit
@Serializable
data class IsPausedResponse(val paused: Boolean)

@UCloudApiInternal(InternalLevel.BETA)
object Maintenance : CallDescriptionContainer("app.compute.kubernetes.maintenance") {
    const val baseContext = "/api/app/compute/kubernetes/maintenance"

    init {
        title = "Maintenance of K8 cluster"
        description = """
            By using the K8 API we are able to manipulate jobs on the kubernetes cluster through our own code by 
            pausing or killing them. The owner of the jobs should always (if possible) be informed prior. 
            
            This API also allows to drain single nodes or the entire cluster if needed. Users should always have the 
            possibility to shutdown their jobs gracefully before a drain of a node and a warning should be issued as soon 
            as possible before the drain is initiated.
            
            Even though the endpoints related to these actions are available to all, it is in fact only possible for 
            ADMINs and SERVICEs to invoke them fully.
            It is mainly only used prior to maintenance of the UCloud system. Jobs should never be killed unless it is 
            last resort.
            
            ${ApiConventions.nonConformingApiWarning}

        """.trimIndent()
    }

    val killJob = call<KillJobRequest, KillJobResponse, CommonErrorMessage>("killJob") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"kill-job"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val isPaused = call<IsPausedRequest, IsPausedResponse, CommonErrorMessage>("isPaused") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"paused"
            }
        }
    }

    val updatePauseState = call<UpdatePauseStateRequest, UpdatePauseStateResponse, CommonErrorMessage>(
        "updatePauseState"
    ) {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"pause"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val drainNode = call<DrainNodeRequest, DrainNodeResponse, CommonErrorMessage>("drainNode") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"drain-node"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val drainCluster = call<DrainClusterRequest, DrainClusterResponse, CommonErrorMessage>("drainCluster") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"drain-cluster"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
