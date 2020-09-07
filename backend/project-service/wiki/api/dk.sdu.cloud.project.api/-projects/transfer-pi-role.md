[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [transferPiRole](./transfer-pi-role.md)

# transferPiRole

`val transferPiRole: CallDescription<`[`TransferPiRoleRequest`](../-transfer-pi-role-request/index.md)`, `[`TransferPiRoleResponse`](../-transfer-pi-role-response.md)`, CommonErrorMessage>`

Transfers the [ProjectRole.PI](../-project-role/-p-i.md) role of the calling user to a different member of the project.

Only the [ProjectRole.PI](../-project-role/-p-i.md) of the [IngoingCall.project](#) can call this.

