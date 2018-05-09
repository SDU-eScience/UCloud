package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.netty.handler.codec.http.HttpMethod

/**
 * Call descriptions for the endpoint `/api/hpc/jobs`
 */
object HPCJobDescriptions : RESTDescriptions(AppServiceDescription) {
    private const val baseContext = "/api/hpc/jobs"

    /**
     * Finds a job by it's ID.
     *
     * __Request:__ [FindByStringId]
     *
     * __Response:__ [JobWithStatus]
     *
     * Queries a job by its ID and returns the resulting job along with its status. Only the user who
     * owns the job is allowed to receive the result.
     *
     * __Example:__ `http :42200/api/hpc/jobs/<jobId>`
     */
    val findById = callDescription<FindByStringId, JobWithStatus, CommonErrorMessage> {
        prettyName = "jobsFindById"
        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }

    /**
     * Lists a user's recent jobs, sorted by the modified at timestamp.
     *
     * __Request:__ [PaginationRequest]
     *
     * __Response:__ [Page] with [JobWithStatus]
     *
     * __Example:__ `http :42200/api/hpc/jobs?page=0&itemsPerPage=10`
     */
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

    /**
     * Starts a job.
     *
     * __Request:__ [AppRequest.Start]
     *
     * __Response:__ [JobStartedResponse]
     *
     * __Example:__ `http :42200/api/hpc/jobs?page=0&itemsPerPage=10`
     */
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

    /**
     * Follows the std streams of a job.
     *
     * __Request:__ [FollowStdStreamsRequest]
     *
     * __Response:__ [FollowStdStreamsResponse]
     */
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
    /**
     * The ID of the [JobWithStatus] to follow
     *
     * Must be positive
     */
    val jobId: String,

    /**
     * The line index to start at (0-indexed) for stdout
     *
     * Must be positive
     */
    val stdoutLineStart: Int,

    /**
     * The maximum amount of lines to retrieve for stdout. Fewer lines can be returned.
     */
    val stdoutMaxLines: Int,

    /**
     * The line index to start at (0-indexed) for stderr
     *
     * Must be positive
     */
    val stderrLineStart: Int,

    /**
     * The maximum amount of lines to retrieve for stderr. Fewer lines can be returned.
     *
     * Must be positive
     */
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
    /**
     * The lines for stdout
     */
    val stdout: String,

    /**
     * The next line index. See [FollowStdStreamsRequest.stderrLineStart]
     */
    val stdoutNextLine: Int,

    /**
     * The lines for stderr
     */
    val stderr: String,

    /**
     * The next line index. See [FollowStdStreamsRequest.stderrLineStart]
     */
    val stderrNextLine: Int,

    /**
     * [NameAndVersion] for the application running.
     */
    val application: NameAndVersion,

    /**
     * The application's current state
     */
    val state: AppState,

    /**
     * The current status
     */
    val status: String,

    /**
     * true if the application has completed (successfully or not) otherwise false
     */
    val complete: Boolean,

    /**
     * The job ID
     */
    val id: String
)
