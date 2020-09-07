[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectGroups](./index.md)

# ProjectGroups

`object ProjectGroups : CallDescriptionContainer`

### Properties

| Name | Summary |
|---|---|
| [addGroupMember](add-group-member.md) | Adds a member of the project to a group.`val addGroupMember: CallDescription<`[`AddGroupMemberRequest`](../-add-group-member-request/index.md)`, `[`AddGroupMemberResponse`](../-add-group-member-response.md)`, CommonErrorMessage>` |
| [baseContext](base-context.md) | `val baseContext: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html) |
| [count](count.md) | Returns the count of groups for a project.`val count: CallDescription<`[`GroupCountRequest`](../-group-count-request.md)`, `[`GroupCountResponse`](../-group-count-response.md)`, CommonErrorMessage>` |
| [create](create.md) | Creates a new group.`val create: CallDescription<`[`CreateGroupRequest`](../-create-group-request/index.md)`, `[`CreateGroupResponse`](../-create-group-response.md)`, CommonErrorMessage>` |
| [delete](delete.md) | Deletes a group.`val delete: CallDescription<`[`DeleteGroupsRequest`](../-delete-groups-request/index.md)`, `[`DeleteGroupsResponse`](../-delete-groups-response.md)`, CommonErrorMessage>` |
| [groupExists](group-exists.md) | Checks if a group exists.`val groupExists: CallDescription<`[`GroupExistsRequest`](../-group-exists-request/index.md)`, `[`GroupExistsResponse`](../-group-exists-response/index.md)`, CommonErrorMessage>` |
| [isMember](is-member.md) | Checks if a project member is a member of a group.`val isMember: CallDescription<`[`IsMemberRequest`](../-is-member-request/index.md)`, `[`IsMemberResponse`](../-is-member-response/index.md)`, CommonErrorMessage>` |
| [listAllGroupMembers](list-all-group-members.md) | List all members of a group.`val listAllGroupMembers: CallDescription<`[`ListAllGroupMembersRequest`](../-list-all-group-members-request/index.md)`, `[`ListAllGroupMembersResponse`](../-list-all-group-members-response.md)`, CommonErrorMessage>` |
| [listGroupMembers](list-group-members.md) | Lists members of a group.`val listGroupMembers: CallDescription<`[`ListGroupMembersRequest`](../-list-group-members-request/index.md)`, `[`ListGroupMembersResponse`](../-list-group-members-response.md)`, CommonErrorMessage>` |
| [listGroupsWithSummary](list-groups-with-summary.md) | Lists groups of a project with a summary (See [GroupWithSummary](../-group-with-summary/index.md)).`val listGroupsWithSummary: CallDescription<`[`ListGroupsWithSummaryRequest`](../-list-groups-with-summary-request/index.md)`, `[`ListGroupsWithSummaryResponse`](../-list-groups-with-summary-response.md)`, CommonErrorMessage>` |
| [removeGroupMember](remove-group-member.md) | Removes a group member from a group.`val removeGroupMember: CallDescription<`[`RemoveGroupMemberRequest`](../-remove-group-member-request/index.md)`, `[`RemoveGroupMemberResponse`](../-remove-group-member-response.md)`, CommonErrorMessage>` |
