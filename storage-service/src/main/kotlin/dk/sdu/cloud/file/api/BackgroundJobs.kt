package dk.sdu.cloud.file.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http

object BackgroundJobs : CallDescriptionContainer("files.jobs") {
    private val baseContext = FileDescriptions.baseContext + "/jobs"

    val query =
        call<Query.Request, Query.Response, CommonErrorMessage>("queryBackgroundJob") {
            auth {
                access = AccessRight.READ
            }

            http {
                path {
                    using(baseContext)
                }

                params {
                    +boundTo(Query.Request::jobId)
                }
            }
        }

    object Query {
        data class Request(val jobId: String)
        data class Response(val statusCode: Int, val message: String)
    }
}
