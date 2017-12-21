package dk.sdu.cloud.abc.api

import dk.sdu.cloud.client.*

object HPCJobDescriptions : RESTDescriptions() {
    private val baseContext = "/api/hpc/jobs"

    val findById = callDescription<FindById, HPCAppEvent, StandardError> {
        path {
            using(baseContext)
            +boundTo(FindById::id)
        }
    }

    val listRecent = callDescription<Unit, MyJobs, StandardError> {
        path {
            using(baseContext)
        }
    }
}

// TODO We are going to end up with conflicts on the very simple ones like these:
data class FindByName(val name: String)
data class FindByNameAndVersion(val name: String, val version: String)
data class FindById(val id: String)
