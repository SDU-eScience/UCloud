package dk.sdu.cloud.abc.api

import dk.sdu.cloud.client.RESTDescriptions

object HPCToolDescriptions : RESTDescriptions() {
    private val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = callDescription<FindByNameAndVersion, ToolDescription, StandardError> {
        path {
            using(baseContext)
            +boundTo(FindByNameAndVersion::name)
            +boundTo(FindByNameAndVersion::version)
        }
    }

    val findByName = callDescription<FindByName, List<ToolDescription>, StandardError> {
        path {
            using(baseContext)
            +boundTo(FindByName::name)
        }
    }

    val listAll = callDescription<Unit, List<ToolDescription>, List<ToolDescription>> {
        path {
            using(baseContext)
        }
    }
}