[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectMembers](index.md) / [userStatus](./user-status.md)

# userStatus

`val userStatus: CallDescription<`[`UserStatusRequest`](../-user-status-request/index.md)`, `[`UserStatusResponse`](../-user-status-response/index.md)`, CommonErrorMessage>`

An endpoint for retrieving the complete project status of a specific user.

UCloud users in [Roles.PRIVILEGED](#) can set [UserStatusRequest.username](../-user-status-request/username.md) otherwise the username of the caller
will be used.

The returned information will contain a complete status of all groups and project memberships. This endpoint
is mostly intended for services to perform permission checks.

