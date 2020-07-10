[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [create](./create.md)

# create

`val create: CallDescription<`[`CreateProjectRequest`](../-create-project-request/index.md)`, `[`CreateProjectResponse`](../-create-project-response.md)`, CommonErrorMessage>`

Creates a project in UCloud.

Only UCloud administrators can create root-level (i.e. no parent) projects. Project administrators can create
a sub-project by supplying [CreateProjectRequest.parent](../-create-project-request/parent.md) with their [Project.id](../-project/id.md).

End-users can create new projects by applying to an existing project through the grant-service.

