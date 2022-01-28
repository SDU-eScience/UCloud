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
