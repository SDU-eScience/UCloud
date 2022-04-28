package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.IpcHandler
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withTransaction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.putJsonArray

// TODO(Dan): We need to determine if this information should only be given to some users. Currently we don't since
//  this information is public in the cluster.
object SlurmJobsIpc : IpcContainer("slurm.jobs") {
    val create = createHandler<SlurmJob, Unit>()

    val retrieve = retrieveHandler<FindByStringId, SlurmJob>()

    // NOTE(Dan): This is not paginated since Slurm does not paginate the results for us
    val browse = browseHandler<SlurmBrowseFlags, List<SlurmJob>>()
}

object SlurmJobsIpcServer {
    val handlers = listOf(
        SlurmJobsIpc.create.handler { user, request ->
            SlurmJobMapper.registerJob(request)
        },
        SlurmJobsIpc.retrieve.handler { user, request ->
            SlurmJobMapper.retrieveByUCloudId(request.id) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        },
        SlurmJobsIpc.browse.handler { user, request ->
            SlurmJobMapper.browse(request)
        }
    )
}

