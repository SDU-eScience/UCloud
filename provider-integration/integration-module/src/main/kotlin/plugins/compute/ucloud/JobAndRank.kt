package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job

data class JobAndRank(val job: Job, val rank: Int)
data class JobIdAndRank(val jobId: String, val rank: Int)
