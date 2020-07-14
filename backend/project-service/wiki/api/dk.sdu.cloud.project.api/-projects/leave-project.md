[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [leaveProject](./leave-project.md)

# leaveProject

`val leaveProject: CallDescription<`[`LeaveProjectRequest`](../-leave-project-request.md)`, `[`LeaveProjectResponse`](../-leave-project-response.md)`, CommonErrorMessage>`

The calling user leaves the project.

Note: The PI cannot leave a project. They must first transfer the role to another user, see [transferPiRole](transfer-pi-role.md).
If there are no other members then the PI can [archive](archive.md) the project.

