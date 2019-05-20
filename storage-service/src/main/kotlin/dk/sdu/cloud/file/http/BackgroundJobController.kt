package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.api.BackgroundJobs
import dk.sdu.cloud.file.services.background.BackgroundExecutor
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class BackgroundJobController(private val backgroundExecutor: BackgroundExecutor<*>) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(BackgroundJobs.query) {
            val status = backgroundExecutor.queryStatus(request.jobId)
            val response = status.response

            if (response == null) {
                ok(BackgroundJobs.Query.Response(-1, ""))
            } else {
                ok(BackgroundJobs.Query.Response(response.responseCode, response.response))
            }
        }

        return@with
    }
}
