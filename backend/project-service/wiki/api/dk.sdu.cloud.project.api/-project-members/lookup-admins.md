[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectMembers](index.md) / [lookupAdmins](./lookup-admins.md)

# lookupAdmins

`val lookupAdmins: CallDescription<`[`LookupAdminsRequest`](../-lookup-admins-request/index.md)`, `[`LookupAdminsResponse`](../-lookup-admins-response/index.md)`, CommonErrorMessage>`

Returns a complete list of all project administrators in a project.

This endpoint can only be used by [Roles.PRIVILEGED](#). It is intended for services to consume when they need to
communicate with administrators of a project.

