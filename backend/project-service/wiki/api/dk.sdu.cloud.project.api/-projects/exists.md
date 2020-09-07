[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [exists](./exists.md)

# exists

`val exists: CallDescription<`[`ExistsRequest`](../-exists-request/index.md)`, `[`ExistsResponse`](../-exists-response/index.md)`, CommonErrorMessage>`

Checks if a project exists.

Only [Roles.PRIVILEGED](#) users can call this endpoint. It is intended that services call this to verify input
parameters that relate to existing projects.

