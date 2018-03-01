package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private const val baseContext = "/api/hpc/jobs"

    val findById = callDescription<FindByStringId, JobWithStatusAndInvocation, CommonErrorMessage> {
        prettyName = "jobsFindById"
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
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

data class FindByNameAndVersion(val name: String, val version: String)
data class JobStartedResponse(val jobId: String)
data class JobCancelledResponse(val jobSuccessfullyCancelled: Boolean)
