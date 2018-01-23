package dk.sdu.cloud.app.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByName
import dk.sdu.cloud.client.RESTDescriptions

object HPCToolDescriptions : RESTDescriptions(AppServiceDescription) {
    private val baseContext = "/api/hpc/tools"

    val findByNameAndVersion = callDescription<FindByNameAndVersion, ToolDescription, CommonErrorMessage> {
        prettyName = "toolsByNameAndVersion"
        path {
            using(baseContext)
            +boundTo(FindByNameAndVersion::name)
            +boundTo(FindByNameAndVersion::version)
        }
    }

    val findByName = callDescription<FindByName, List<ToolDescription>, CommonErrorMessage> {
        prettyName = "toolsByName"
        path {
            using(baseContext)
            +boundTo(FindByName::name)
        }
    }

    val listAll = callDescription<Unit, List<ToolDescription>, List<ToolDescription>> {
        prettyName = "toolsListAll"
        path {
            using(baseContext)
        }
    }
}