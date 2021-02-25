package dk.sdu.cloud.project.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(val group: String) {
    init {
        if (group.isEmpty()) throw RPCException("Group cannot be empty", HttpStatusCode.BadRequest)
        if (group.contains('\n')) throw RPCException("Group cannot contain new lines", HttpStatusCode.BadRequest)
        if (group.length > 500) throw RPCException("Group name too long", HttpStatusCode.BadRequest)
        if (group == "-") throw RPCException("Group cannot be '-'", HttpStatusCode.BadRequest)
    }
}

typealias CreateGroupResponse = FindByStringId

@Serializable
data class ListGroupsWithSummaryRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
typealias ListGroupsWithSummaryResponse = Page<GroupWithSummary>
@Serializable
data class GroupWithSummary(val groupId: String, val groupTitle: String, val numberOfMembers: Int)
@Serializable
data class ProjectAndGroup(val project: Project, val group: ProjectGroup)

@Serializable
data class DeleteGroupsRequest(val groups: Set<String>)
typealias DeleteGroupsResponse = Unit

@Serializable
data class AddGroupMemberRequest(val group: String, val memberUsername: String)
typealias AddGroupMemberResponse = Unit

@Serializable
data class RemoveGroupMemberRequest(val group: String, val memberUsername: String)
typealias RemoveGroupMemberResponse = Unit

@Serializable
data class UpdateGroupNameRequest(val groupId: String, val newGroupName: String) {
    init {
        if (newGroupName.isEmpty()) throw RPCException("Group cannot be empty", HttpStatusCode.BadRequest)
        if (newGroupName.contains('\n')) throw RPCException("Group cannot contain new lines", HttpStatusCode.BadRequest)
        if (newGroupName.length > 500) throw RPCException("Group name too long", HttpStatusCode.BadRequest)
    }
}
typealias UpdateGroupNameResponse = Unit

@Serializable
data class ListGroupMembersRequest(
    val group: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

typealias ListAllGroupIdsAndTitlesRequest = Unit
@Serializable
data class ListAllGroupIdsAndTitlesResponse(val groups: Map<String, String>)

typealias ListGroupMembersResponse = Page<String>

@Serializable
data class IsMemberQuery(val project: String, val group: String, val username: String)
@Serializable
data class IsMemberRequest(val queries: List<IsMemberQuery>)
@Serializable
data class IsMemberResponse(val responses: List<Boolean>)

@Serializable
data class GroupExistsRequest(val project: String, val groups: List<String>)
@Serializable
data class GroupExistsResponse(val exists: List<Boolean>)

@Serializable
data class ListAllGroupMembersRequest(val project: String, val group: String)
typealias ListAllGroupMembersResponse = List<String>

typealias GroupCountRequest = Unit
typealias GroupCountResponse = Long

@Serializable
data class ViewGroupRequest(val id: String)
typealias ViewGroupResponse = GroupWithSummary

@Serializable
data class LookupByGroupTitleRequest(
    val projectId: String,
    val title: String
)
typealias LookupByGroupTitleResponse = GroupWithSummary

@Serializable
data class LookupProjectAndGroupRequest(
    val project: String,
    val group: String
)
typealias LookupProjectAndGroupResponse = ProjectAndGroup

object ProjectGroups : CallDescriptionContainer("project.group") {
    val baseContext = "/api/projects/groups"

    /**
     * Creates a new group.
     *
     * Only project administrators can create new groups in a project.
     */
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

    /**
     * Lists groups of a project with a summary (See [GroupWithSummary]).
     *
     * Any project member can read the groups.
     */
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

    /**
     * List all members of a group.
     *
     * Any member of the project can read the members of the group.
     */
    val listAllGroupMembers = call<ListAllGroupMembersRequest, ListAllGroupMembersResponse, CommonErrorMessage>(
        "listAllGroupMembers"
    ) {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"list-all-group-members"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Deletes a group.
     *
     * Only project administrators can delete a group.
     */
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

    /**
     * Adds a member of the project to a group.
     *
     * Only project administrators can add members to a group.
     */
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

    /**
     * Removes a group member from a group.
     *
     * Only project administrators can remove members from a group.
     */
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

    /**
     * Lists members of a group.
     *
     * All members of a project can read members of any group.
     */
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

    /**
     * Checks if a project member is a member of a group.
     *
     * Only [Roles.PRIVILEGED] can use this endpoint. It is intended for services which need to check if a members
     * belongs to a specific group.
     */
    val isMember = call<IsMemberRequest, IsMemberResponse, CommonErrorMessage>("isMember") {
        auth {
            roles = Roles.PRIVILEGED
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

    /**
     * Checks if a group exists.
     *
     * Only [Roles.PRIVILEGED] can call this endpoint. It is intended for services which need to verify that their input
     * is valid.
     */
    val groupExists = call<GroupExistsRequest, GroupExistsResponse, CommonErrorMessage>("groupExists") {
        auth {
            roles = Roles.PRIVILEGED
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

    /**
     * Returns the count of groups for a project.
     *
     * All project members can use this endpoint.
     */
    val count = call<GroupCountRequest, GroupCountResponse, CommonErrorMessage>("count") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"count"
            }
        }
    }

    /**
     * View information about a group
     *
     * All project members can use this endpoint.
     */
    val view = call<ViewGroupRequest, ViewGroupResponse, CommonErrorMessage>("view") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"view"
            }

            params {
                +boundTo(ViewGroupRequest::id)
            }
        }
    }

    /**
     * Look up project group by title
     *
     * Only [Roles.PRIVILEGED] can call this endpoint. It is intended for services which need to verify that their input
     * is valid.
     */
    val lookupByTitle = call<LookupByGroupTitleRequest, LookupByGroupTitleResponse, CommonErrorMessage>("lookupByTitle") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookup-by-title"
            }

            params {
                +boundTo(LookupByGroupTitleRequest::projectId)
                +boundTo(LookupByGroupTitleRequest::title)
            }
        }
    }

    /**
     * Look up a project and group
     *
     * Only [Roles.PRIVILEGED] can call this endpoint. It is intended for services which need to look up a
     * project and group, which is not necessarily the active project.
     */
    val lookupProjectAndGroup = call<LookupProjectAndGroupRequest, LookupProjectAndGroupResponse, CommonErrorMessage>("lookupProjectAndGroup") {
        auth {
            access = AccessRight.READ
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"lookup-project-and-group"
            }

            params {
                +boundTo(LookupProjectAndGroupRequest::project)
                +boundTo(LookupProjectAndGroupRequest::group)
            }
        }
    }

    val listAllGroupIdsAndTitles = call<ListAllGroupIdsAndTitlesRequest, ListAllGroupIdsAndTitlesResponse, CommonErrorMessage>("listAllGroupIdsAndTitles") {
        auth {
            access = AccessRight.READ
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list-all-groups"
            }
        }
    }
}
