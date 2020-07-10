[api](../index.md) / [dk.sdu.cloud.project.api](./index.md)

## Package dk.sdu.cloud.project.api

The API for handling ordinary project management and query endpoints.

You can find the management endpoints [here](./-projects/index.md) and [here](./-project-members/index.md).
Group management can be found [here](./-project-groups/index.md).

You can read more about the event stream associated with projects [here](./-project-events/index.md).

### Types

| Name | Summary |
|---|---|
| [AcceptInviteRequest](-accept-invite-request/index.md) | `data class AcceptInviteRequest` |
| [AcceptInviteResponse](-accept-invite-response.md) | `typealias AcceptInviteResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [AddGroupMemberRequest](-add-group-member-request/index.md) | `data class AddGroupMemberRequest` |
| [AddGroupMemberResponse](-add-group-member-response.md) | `typealias AddGroupMemberResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [ArchiveRequest](-archive-request/index.md) | `data class ArchiveRequest` |
| [ArchiveResponse](-archive-response.md) | `typealias ArchiveResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [ChangeUserRoleRequest](-change-user-role-request/index.md) | `data class ChangeUserRoleRequest` |
| [ChangeUserRoleResponse](-change-user-role-response.md) | `typealias ChangeUserRoleResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [CountRequest](-count-request.md) | `typealias CountRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [CountResponse](-count-response.md) | `typealias CountResponse = `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [CountSubProjectsRequest](-count-sub-projects-request.md) | `typealias CountSubProjectsRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [CountSubProjectsResponse](-count-sub-projects-response.md) | `typealias CountSubProjectsResponse = `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [CreateGroupRequest](-create-group-request/index.md) | `data class CreateGroupRequest` |
| [CreateGroupResponse](-create-group-response.md) | `typealias CreateGroupResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [CreateProjectRequest](-create-project-request/index.md) | `data class CreateProjectRequest` |
| [CreateProjectResponse](-create-project-response.md) | `typealias CreateProjectResponse = FindByStringId` |
| [DeleteGroupsRequest](-delete-groups-request/index.md) | `data class DeleteGroupsRequest` |
| [DeleteGroupsResponse](-delete-groups-response.md) | `typealias DeleteGroupsResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [DeleteMemberRequest](-delete-member-request/index.md) | `data class DeleteMemberRequest` |
| [DeleteMemberResponse](-delete-member-response.md) | `typealias DeleteMemberResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [ExistsRequest](-exists-request/index.md) | `data class ExistsRequest` |
| [ExistsResponse](-exists-response/index.md) | `data class ExistsResponse` |
| [GroupCountRequest](-group-count-request.md) | `typealias GroupCountRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [GroupCountResponse](-group-count-response.md) | `typealias GroupCountResponse = `[`Long`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/index.html) |
| [GroupExistsRequest](-group-exists-request/index.md) | `data class GroupExistsRequest` |
| [GroupExistsResponse](-group-exists-response/index.md) | `data class GroupExistsResponse` |
| [GroupWithSummary](-group-with-summary/index.md) | `data class GroupWithSummary` |
| [IngoingInvite](-ingoing-invite/index.md) | `data class IngoingInvite` |
| [InviteRequest](-invite-request/index.md) | `data class InviteRequest` |
| [InviteResponse](-invite-response.md) | `typealias InviteResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [IsMemberQuery](-is-member-query/index.md) | `data class IsMemberQuery` |
| [IsMemberRequest](-is-member-request/index.md) | `data class IsMemberRequest` |
| [IsMemberResponse](-is-member-response/index.md) | `data class IsMemberResponse` |
| [LeaveProjectRequest](-leave-project-request.md) | `typealias LeaveProjectRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [LeaveProjectResponse](-leave-project-response.md) | `typealias LeaveProjectResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [ListAllGroupMembersRequest](-list-all-group-members-request/index.md) | `data class ListAllGroupMembersRequest` |
| [ListAllGroupMembersResponse](-list-all-group-members-response.md) | `typealias ListAllGroupMembersResponse = `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [ListFavoriteProjectsRequest](-list-favorite-projects-request/index.md) | `data class ListFavoriteProjectsRequest : WithPaginationRequest` |
| [ListFavoriteProjectsResponse](-list-favorite-projects-response.md) | `typealias ListFavoriteProjectsResponse = `[`ListProjectsResponse`](-list-projects-response.md) |
| [ListGroupMembersRequest](-list-group-members-request/index.md) | `data class ListGroupMembersRequest : WithPaginationRequest` |
| [ListGroupMembersResponse](-list-group-members-response.md) | `typealias ListGroupMembersResponse = Page<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`>` |
| [ListGroupsWithSummaryRequest](-list-groups-with-summary-request/index.md) | `data class ListGroupsWithSummaryRequest : WithPaginationRequest` |
| [ListGroupsWithSummaryResponse](-list-groups-with-summary-response.md) | `typealias ListGroupsWithSummaryResponse = Page<`[`GroupWithSummary`](-group-with-summary/index.md)`>` |
| [ListIngoingInvitesRequest](-list-ingoing-invites-request/index.md) | `data class ListIngoingInvitesRequest : WithPaginationRequest` |
| [ListIngoingInvitesResponse](-list-ingoing-invites-response.md) | `typealias ListIngoingInvitesResponse = Page<`[`IngoingInvite`](-ingoing-invite/index.md)`>` |
| [ListOutgoingInvitesRequest](-list-outgoing-invites-request/index.md) | `data class ListOutgoingInvitesRequest : WithPaginationRequest` |
| [ListOutgoingInvitesResponse](-list-outgoing-invites-response.md) | `typealias ListOutgoingInvitesResponse = Page<`[`OutgoingInvite`](-outgoing-invite/index.md)`>` |
| [ListProjectsRequest](-list-projects-request/index.md) | `data class ListProjectsRequest : WithPaginationRequest` |
| [ListProjectsResponse](-list-projects-response.md) | `typealias ListProjectsResponse = Page<`[`UserProjectSummary`](-user-project-summary/index.md)`>` |
| [ListSubProjectsRequest](-list-sub-projects-request/index.md) | `data class ListSubProjectsRequest : WithPaginationRequest` |
| [ListSubProjectsResponse](-list-sub-projects-response.md) | `typealias ListSubProjectsResponse = Page<`[`Project`](-project/index.md)`>` |
| [LookupAdminsRequest](-lookup-admins-request/index.md) | `data class LookupAdminsRequest` |
| [LookupAdminsResponse](-lookup-admins-response/index.md) | `data class LookupAdminsResponse` |
| [LookupPrincipalInvestigatorRequest](-lookup-principal-investigator-request.md) | `typealias LookupPrincipalInvestigatorRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [LookupPrincipalInvestigatorResponse](-lookup-principal-investigator-response/index.md) | `data class LookupPrincipalInvestigatorResponse` |
| [OutgoingInvite](-outgoing-invite/index.md) | `data class OutgoingInvite` |
| [Project](-project/index.md) | `data class Project` |
| [ProjectEvent](-project-event/index.md) | `sealed class ProjectEvent` |
| [ProjectEvents](-project-events/index.md) | `object ProjectEvents : EventStreamContainer` |
| [ProjectGroups](-project-groups/index.md) | `object ProjectGroups : CallDescriptionContainer` |
| [ProjectMember](-project-member/index.md) | `data class ProjectMember` |
| [ProjectMembers](-project-members/index.md) | `object ProjectMembers : CallDescriptionContainer` |
| [ProjectRole](-project-role/index.md) | `enum class ProjectRole` |
| [Projects](-projects/index.md) | `object Projects : CallDescriptionContainer` |
| [RejectInviteRequest](-reject-invite-request/index.md) | `data class RejectInviteRequest` |
| [RejectInviteResponse](-reject-invite-response.md) | `typealias RejectInviteResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [RemoveGroupMemberRequest](-remove-group-member-request/index.md) | `data class RemoveGroupMemberRequest` |
| [RemoveGroupMemberResponse](-remove-group-member-response.md) | `typealias RemoveGroupMemberResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [SearchRequest](-search-request/index.md) | `data class SearchRequest : WithPaginationRequest` |
| [SearchResponse](-search-response.md) | `typealias SearchResponse = Page<`[`ProjectMember`](-project-member/index.md)`>` |
| [TransferPiRoleRequest](-transfer-pi-role-request/index.md) | `data class TransferPiRoleRequest` |
| [TransferPiRoleResponse](-transfer-pi-role-response.md) | `typealias TransferPiRoleResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [UpdateGroupNameRequest](-update-group-name-request/index.md) | `data class UpdateGroupNameRequest` |
| [UpdateGroupNameResponse](-update-group-name-response.md) | `typealias UpdateGroupNameResponse = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [UserGroupSummary](-user-group-summary/index.md) | `data class UserGroupSummary` |
| [UserProjectSummary](-user-project-summary/index.md) | A project summary from a user's perspective`data class UserProjectSummary` |
| [UserStatusInProject](-user-status-in-project/index.md) | `data class UserStatusInProject` |
| [UserStatusRequest](-user-status-request/index.md) | `data class UserStatusRequest` |
| [UserStatusResponse](-user-status-response/index.md) | `data class UserStatusResponse` |
| [ViewAncestorsRequest](-view-ancestors-request.md) | `typealias ViewAncestorsRequest = `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html) |
| [ViewAncestorsResponse](-view-ancestors-response.md) | `typealias ViewAncestorsResponse = `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`Project`](-project/index.md)`>` |
| [ViewMemberInProjectRequest](-view-member-in-project-request/index.md) | `data class ViewMemberInProjectRequest` |
| [ViewMemberInProjectResponse](-view-member-in-project-response/index.md) | `data class ViewMemberInProjectResponse` |
| [ViewProjectRequest](-view-project-request/index.md) | `data class ViewProjectRequest` |
| [ViewProjectResponse](-view-project-response.md) | `typealias ViewProjectResponse = `[`UserProjectSummary`](-user-project-summary/index.md) |
