[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [ProjectGroups](index.md) / [groupExists](./group-exists.md)

# groupExists

`val groupExists: CallDescription<`[`GroupExistsRequest`](../-group-exists-request/index.md)`, `[`GroupExistsResponse`](../-group-exists-response/index.md)`, CommonErrorMessage>`

Checks if a group exists.

Only [Roles.PRIVILEGED](#) can call this endpoint. It is intended for services which need to verify that their input
is valid.

