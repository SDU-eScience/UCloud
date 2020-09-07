[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](./index.md)

# Projects

`object Projects : CallDescriptionContainer`

### Properties

| Name | Summary |
|---|---|
| [acceptInvite](accept-invite.md) | Accepts the [invite](invite.md) to a project.`val acceptInvite: CallDescription<`[`AcceptInviteRequest`](../-accept-invite-request/index.md)`, `[`AcceptInviteResponse`](../-accept-invite-response.md)`, CommonErrorMessage>` |
| [archive](archive.md) | Archives/unarchives a project.`val archive: CallDescription<`[`ArchiveRequest`](../-archive-request/index.md)`, `[`ArchiveResponse`](../-archive-response.md)`, CommonErrorMessage>` |
| [baseContext](base-context.md) | `val baseContext: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [changeUserRole](change-user-role.md) | Changes the project role of an existing member.`val changeUserRole: CallDescription<`[`ChangeUserRoleRequest`](../-change-user-role-request/index.md)`, `[`ChangeUserRoleResponse`](../-change-user-role-response.md)`, CommonErrorMessage>` |
| [countSubProjects](count-sub-projects.md) | Returns the number of sub-projects of an existing project`val countSubProjects: CallDescription<`[`CountSubProjectsRequest`](../-count-sub-projects-request.md)`, `[`CountSubProjectsResponse`](../-count-sub-projects-response.md)`, CommonErrorMessage>` |
| [create](create.md) | Creates a project in UCloud.`val create: CallDescription<`[`CreateProjectRequest`](../-create-project-request/index.md)`, `[`CreateProjectResponse`](../-create-project-response.md)`, CommonErrorMessage>` |
| [deleteMember](delete-member.md) | Removes a member from a project.`val deleteMember: CallDescription<`[`DeleteMemberRequest`](../-delete-member-request/index.md)`, `[`DeleteMemberResponse`](../-delete-member-response.md)`, CommonErrorMessage>` |
| [exists](exists.md) | Checks if a project exists.`val exists: CallDescription<`[`ExistsRequest`](../-exists-request/index.md)`, `[`ExistsResponse`](../-exists-response/index.md)`, CommonErrorMessage>` |
| [invite](invite.md) | Invites a set of users to a project.`val invite: CallDescription<`[`InviteRequest`](../-invite-request/index.md)`, `[`InviteResponse`](../-invite-response.md)`, CommonErrorMessage>` |
| [leaveProject](leave-project.md) | The calling user leaves the project.`val leaveProject: CallDescription<`[`LeaveProjectRequest`](../-leave-project-request.md)`, `[`LeaveProjectResponse`](../-leave-project-response.md)`, CommonErrorMessage>` |
| [listFavoriteProjects](list-favorite-projects.md) | Fetches a list of favorite projects for the calling user.`val listFavoriteProjects: CallDescription<`[`ListFavoriteProjectsRequest`](../-list-favorite-projects-request/index.md)`, `[`ListFavoriteProjectsResponse`](../-list-favorite-projects-response.md)`, CommonErrorMessage>` |
| [listIngoingInvites](list-ingoing-invites.md) | Fetches a list of invites received by the requesting user.`val listIngoingInvites: CallDescription<`[`ListIngoingInvitesRequest`](../-list-ingoing-invites-request/index.md)`, `[`ListIngoingInvitesResponse`](../-list-ingoing-invites-response.md)`, CommonErrorMessage>` |
| [listOutgoingInvites](list-outgoing-invites.md) | Fetches a list of invites sent by the requesting project.`val listOutgoingInvites: CallDescription<`[`ListOutgoingInvitesRequest`](../-list-outgoing-invites-request/index.md)`, `[`ListOutgoingInvitesResponse`](../-list-outgoing-invites-response.md)`, CommonErrorMessage>` |
| [listProjects](list-projects.md) | Fetches a list of projects the calling user is a member of.`val listProjects: CallDescription<`[`ListProjectsRequest`](../-list-projects-request/index.md)`, `[`ListProjectsResponse`](../-list-projects-response.md)`, CommonErrorMessage>` |
| [listSubProjects](list-sub-projects.md) | Lists sub-projects of an existing project`val listSubProjects: CallDescription<`[`ListSubProjectsRequest`](../-list-sub-projects-request/index.md)`, `[`ListSubProjectsResponse`](../-list-sub-projects-response.md)`, CommonErrorMessage>` |
| [lookupPrincipalInvestigator](lookup-principal-investigator.md) | Lookup the principal investigator ([ProjectRole.PI](../-project-role/-p-i.md)) of a project`val lookupPrincipalInvestigator: CallDescription<`[`LookupPrincipalInvestigatorRequest`](../-lookup-principal-investigator-request.md)`, `[`LookupPrincipalInvestigatorResponse`](../-lookup-principal-investigator-response/index.md)`, CommonErrorMessage>` |
| [rejectInvite](reject-invite.md) | Rejects the invite to a project.`val rejectInvite: CallDescription<`[`RejectInviteRequest`](../-reject-invite-request/index.md)`, `[`RejectInviteResponse`](../-reject-invite-response.md)`, CommonErrorMessage>` |
| [transferPiRole](transfer-pi-role.md) | Transfers the [ProjectRole.PI](../-project-role/-p-i.md) role of the calling user to a different member of the project.`val transferPiRole: CallDescription<`[`TransferPiRoleRequest`](../-transfer-pi-role-request/index.md)`, `[`TransferPiRoleResponse`](../-transfer-pi-role-response.md)`, CommonErrorMessage>` |
| [verifyMembership](verify-membership.md) | Verify that the users of a project are still correct.`val verifyMembership: CallDescription<`[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`, `[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`, CommonErrorMessage>` |
| [viewAncestors](view-ancestors.md) | Returns a complete list of ancestors of an existing project`val viewAncestors: CallDescription<`[`ViewAncestorsRequest`](../-view-ancestors-request.md)`, `[`ViewAncestorsResponse`](../-view-ancestors-response.md)`, CommonErrorMessage>` |
| [viewMemberInProject](view-member-in-project.md) | View the status of a member in a project.`val viewMemberInProject: CallDescription<`[`ViewMemberInProjectRequest`](../-view-member-in-project-request/index.md)`, `[`ViewMemberInProjectResponse`](../-view-member-in-project-response/index.md)`, CommonErrorMessage>` |
| [viewProject](view-project.md) | View information about an existing project.`val viewProject: CallDescription<`[`ViewProjectRequest`](../-view-project-request/index.md)`, `[`ViewProjectResponse`](../-view-project-response.md)`, CommonErrorMessage>` |
