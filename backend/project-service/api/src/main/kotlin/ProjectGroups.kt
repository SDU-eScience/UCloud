package dk.sdu.cloud.project.api

import com.github.jasync.sql.db.util.size
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

data class CreateGroupRequest(val group: String) {
    init {
        if (group.isEmpty()) throw RPCException("Group cannot be empty", HttpStatusCode.BadRequest)
        if (group.contains('\n')) throw RPCException("Group cannot contain new lines", HttpStatusCode.BadRequest)
        if (group.size > 500) throw RPCException("Group name too long", HttpStatusCode.BadRequest)
    }
}
typealias CreateGroupResponse = Unit

typealias ListGroupsRequest = Unit
typealias ListGroupsResponse = List<String>

data class ListGroupsWithSummaryRequest(
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest
typealias ListGroupsWithSummaryResponse = Page<GroupWithSummary>
data class GroupWithSummary(val group: String, val numberOfMembers: Int, val memberPreview: List<String>)

data class DeleteGroupsRequest(val groups: Set<String>)
typealias DeleteGroupsResponse = Unit

data class AddGroupMemberRequest(val group: String, val memberUsername: String)
typealias AddGroupMemberResponse = Unit

data class RemoveGroupMemberRequest(val group: String, val memberUsername: String)
typealias RemoveGroupMemberResponse = Unit

data class UpdateGroupNameRequest(val oldGroupName: String, val newGroupName: String) {
    init {
        if (newGroupName.isEmpty()) throw RPCException("Group cannot be empty", HttpStatusCode.BadRequest)
        if (newGroupName.contains('\n')) throw RPCException("Group cannot contain new lines", HttpStatusCode.BadRequest)
        if (newGroupName.size > 500) throw RPCException("Group name too long", HttpStatusCode.BadRequest)
    }
}
typealias UpdateGroupNameResponse = Unit

data class ListGroupMembersRequest(
    val group: String,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

typealias ListGroupMembersResponse = Page<String>

data class IsMemberQuery(val project: String, val group: String, val username: String)
data class IsMemberRequest(val queries: List<IsMemberQuery>)
data class IsMemberResponse(val responses: List<Boolean>)

data class GroupExistsRequest(val project: String, val group: String)
data class GroupExistsResponse(val exists: Boolean)

object ProjectGroups : CallDescriptionContainer("project.group") {
    val baseContext = "/api/projects/groups"

    val create = call<CreateGroupRequest, CreateGroupResponse, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val list = call<ListGroupsRequest, ListGroupsResponse, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }
        }
    }

    val listGroupsWithSummary =
        call<ListGroupsWithSummaryRequest, ListGroupsWithSummaryResponse, CommonErrorMessage>("listGroupsWithSummary") {
            auth {
                access = AccessRight.READ
            }

            http {
                path {
                    using(baseContext)
                    +"summary"
                }

                params {
                    +boundTo(ListGroupsWithSummaryRequest::itemsPerPage)
                    +boundTo(ListGroupsWithSummaryRequest::page)
                }
            }
        }

    val delete = call<DeleteGroupsRequest, DeleteGroupsResponse, CommonErrorMessage>("delete") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val addGroupMember = call<AddGroupMemberRequest, AddGroupMemberResponse, CommonErrorMessage>("addGroupMember") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
                +"members"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeGroupMember =
        call<RemoveGroupMemberRequest, RemoveGroupMemberResponse, CommonErrorMessage>("removeGroupMember") {
            auth {
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Delete

                path {
                    using(baseContext)
                    +"members"
                }

                body { bindEntireRequestFromBody() }
            }
        }

    val updateGroupName = call<UpdateGroupNameRequest, UpdateGroupNameResponse, CommonErrorMessage>("updateGroupName") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"update-name"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val listGroupMembers =
        call<ListGroupMembersRequest, ListGroupMembersResponse, CommonErrorMessage>("listGroupMembers") {
            auth {
                access = AccessRight.READ
            }

            http {
                path {
                    using(baseContext)
                    +"members"
                }

                params {
                    +boundTo(ListGroupMembersRequest::group)
                    +boundTo(ListGroupMembersRequest::itemsPerPage)
                    +boundTo(ListGroupMembersRequest::page)
                }
            }
        }

    val isMember = call<IsMemberRequest, IsMemberResponse, CommonErrorMessage>("isMember") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"is-member"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val groupExists = call<GroupExistsRequest, GroupExistsResponse, CommonErrorMessage>("groupExists") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"exists"
            }

            body { bindEntireRequestFromBody() }
        }
    }
}
