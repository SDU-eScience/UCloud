package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.KafkaCallDescriptionBundle
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private const val baseContext = "/api/hpc/jobs"

    // TODO FIXME Remove directories from public API
    val findById = callDescription<FindById, JobWithStatusAndInvocation, CommonErrorMessage> {
        prettyName = "jobsFindById"
        path {
            using(baseContext)
            +boundTo(FindById::id)
        }
    }

    val listRecent = callDescription<Unit, List<JobWithStatus>, CommonErrorMessage> {
        prettyName = "jobsListRecent"
        path {
            using(baseContext)
        }
    }

    val start = callDescription<AppRequest.Start, JobStartedResponse, CommonErrorMessage> {
        prettyName = "jobsStart"
        method = HttpMethod.POST

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val cancel = callDescription<AppRequest.Cancel, JobCancelledResponse, JobCancelledResponse> {
        prettyName = "jobsCancel"
        method = HttpMethod.DELETE

        path {
            using(baseContext)
            +boundTo(AppRequest.Cancel::jobId)
        }
    }
}

// TODO We are going to end up with conflicts on the very simple ones like these:
data class FindByNameAndVersion(val name: String, val version: String)

data class FindById(val id: String)

data class JobStartedResponse(val jobId: String)
data class JobCancelledResponse(val jobSuccessfullyCancelled: Boolean)
