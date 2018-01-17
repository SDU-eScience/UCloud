package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.services.JobWithStatus
import dk.sdu.cloud.app.services.JobWithStatusAndInvocation
import dk.sdu.cloud.client.KafkaCallDescriptionBundle
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.netty.handler.codec.http.HttpMethod

object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private val baseContext = "/api/hpc/jobs"

    val findById = callDescription<FindById, JobWithStatusAndInvocation, CommonErrorMessage> {
        path {
            using(baseContext)
            +boundTo(FindById::id)
        }
    }

    val listRecent = callDescription<Unit, List<JobWithStatus>, CommonErrorMessage> {
        path {
            using(baseContext)
        }
    }

    val start = kafkaDescription<AppRequest.Start> {
        method = HttpMethod.POST

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val cancel = kafkaDescription<AppRequest.Cancel> {
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
