package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.KafkaCallDescriptionBundle
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private val baseContext = "/api/hpc/jobs"

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

    val start = kafkaDescription<AppRequest.Start> {
        prettyName = "jobsStart"
        method = HttpMethod.POST

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val cancel = kafkaDescription<AppRequest.Cancel> {
        prettyName = "jobsCancel"
        method = HttpMethod.DELETE

        path {
            using(baseContext)
            +boundTo(AppRequest.Cancel::jobId)
        }
    }

    val appRequestBundle: KafkaCallDescriptionBundle<AppRequest> = listOf(start, cancel)
}

// TODO We are going to end up with conflicts on the very simple ones like these:
data class FindByNameAndVersion(val name: String, val version: String)

data class FindById(val id: String)
