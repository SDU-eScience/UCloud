[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectGroups](index.md) / [isMember](./is-member.md)

# isMember

`val isMember: CallDescription<`[`IsMemberRequest`](../-is-member-request/index.md)`, `[`IsMemberResponse`](../-is-member-response/index.md)`, CommonErrorMessage>`

Checks if a project member is a member of a group.

Only [Roles.PRIVILEGED](#) can use this endpoint. It is intended for services which need to check if a members
belongs to a specific group.

