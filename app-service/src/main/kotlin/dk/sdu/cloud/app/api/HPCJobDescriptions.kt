package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.netty.handler.codec.http.HttpMethod

object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private const val baseContext = "/api/hpc/jobs"

    val findById = callDescription<FindByStringId, JobWithStatus, CommonErrorMessage> {
        prettyName = "jobsFindById"
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }

    val listRecent = callDescription<PaginationRequest, Page<JobWithStatus>, CommonErrorMessage> {
        prettyName = "jobsListRecent"
        path {
            using(baseContext)
        }

        params {
            +boundTo(PaginationRequest::itemsPerPage)
            +boundTo(PaginationRequest::page)
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

    val follow = callDescription<FollowStdStreamsRequest, FollowStdStreamsResponse, CommonErrorMessage> {
        prettyName = "followStdStreams"
        method = HttpMethod.GET

        path {
            using(baseContext)
            +"follow"
            +boundTo(FollowStdStreamsRequest::jobId)
        }

        params {
            +boundTo(FollowStdStreamsRequest::stderrLineStart)
            +boundTo(FollowStdStreamsRequest::stderrMaxLines)
            +boundTo(FollowStdStreamsRequest::stdoutLineStart)
            +boundTo(FollowStdStreamsRequest::stdoutMaxLines)
        }
    }
}

data class FindByNameAndVersion(val name: String, val version: String)
data class JobStartedResponse(val jobId: String)


data class FollowStdStreamsRequest(
    val jobId: String,
    val stdoutLineStart: Int,
    val stdoutMaxLines: Int,

    val stderrLineStart: Int,
    val stderrMaxLines: Int
) {
    init {
        if (stderrMaxLines < 0) throw IllegalArgumentException("stderrMaxLines < 0")
        if (stdoutMaxLines < 0) throw IllegalArgumentException("stdoutMaxLines < 0")
        if (stdoutLineStart < 0) throw IllegalArgumentException("stdoutLineStart < 0")
        if (stderrLineStart < 0) throw IllegalArgumentException("stderrLinesStart < 0")
    }
}

data class FollowStdStreamsResponse(
    val stdout: String,
    val stdoutNextLine: Int,

    val stderr: String,
    val stderrNextLine: Int,

    val application: NameAndVersion,
    val state: AppState,
    val status: String,
    val complete: Boolean,
    val id: String
)
