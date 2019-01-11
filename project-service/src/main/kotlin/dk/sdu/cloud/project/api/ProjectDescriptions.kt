package dk.sdu.cloud.project.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.http.HttpMethod

data class CreateProjectRequest(
    val title: String,
    val principalInvestigator: String
)

typealias CreateProjectResponse = FindByStringId

typealias ViewProjectRequest = FindByStringId
typealias ViewProjectResponse = Project

data class ViewMemberInProjectRequest(val projectId: String, val username: String)
data class ViewMemberInProjectResponse(val member: ProjectMember)

typealias DeleteProjectRequest = FindByStringId
typealias DeleteProjectResponse = Unit

data class AddMemberRequest(val projectId: String, val member: ProjectMember)
typealias AddMemberResponse = Unit

data class DeleteMemberRequest(val projectId: String, val member: String)
typealias DeleteMemberResponse = Unit

data class ChangeUserRoleRequest(val projectId: String, val member: String, val newRole: ProjectRole)
typealias ChangeUserRoleResponse = Unit

object ProjectDescriptions : RESTDescriptions("project") {
    val baseContext = "/api/projects"

    val create = callDescription<CreateProjectRequest, CreateProjectResponse, CommonErrorMessage> {
        name = "create"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val view = callDescription<ViewProjectRequest, ViewProjectResponse, CommonErrorMessage> {
        name = "view"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        params { +boundTo(ViewProjectRequest::id) }
    }

    val viewMemberInProject = callDescription<ViewMemberInProjectRequest, ViewMemberInProjectResponse, CommonErrorMessage> {
        name = "viewMemberInProject"
        method = HttpMethod.Get

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"members"
        }

        params {
            +boundTo(ViewMemberInProjectRequest::projectId)
            +boundTo(ViewMemberInProjectRequest::username)
        }
    }

    val delete = callDescription<DeleteProjectRequest, DeleteProjectResponse, CommonErrorMessage> {
        name = "delete"
        method = HttpMethod.Delete

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        params { +boundTo((DeleteProjectRequest::id)) }
    }

    val addMember = callDescription<AddMemberRequest, AddMemberResponse, CommonErrorMessage> {
        name = "addMember"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"members"
        }

        body { bindEntireRequestFromBody() }
    }

    val deleteMember = callDescription<DeleteMemberRequest, DeleteMemberResponse, CommonErrorMessage> {
        name = "deleteMember"
        method = HttpMethod.Delete

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"members"
        }

        body { bindEntireRequestFromBody() }
    }

    val changeUserRole = callDescription<ChangeUserRoleRequest, ChangeUserRoleResponse, CommonErrorMessage> {
        name = "changeUserRole"
        method = HttpMethod.Post
        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"members"
            +"change-role"
        }

        body { bindEntireRequestFromBody() }
    }
}
