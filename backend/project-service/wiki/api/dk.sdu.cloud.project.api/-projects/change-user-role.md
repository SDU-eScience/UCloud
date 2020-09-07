[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [changeUserRole](./change-user-role.md)

# changeUserRole

`val changeUserRole: CallDescription<`[`ChangeUserRoleRequest`](../-change-user-role-request/index.md)`, `[`ChangeUserRoleResponse`](../-change-user-role-response.md)`, CommonErrorMessage>`

Changes the project role of an existing member.

Only the project administrators can change the role of a member. The new role cannot be [ProjectRole.PI](../-project-role/-p-i.md). In
order to promote a user to PI use the [transferPiRole](transfer-pi-role.md) endpoint.

