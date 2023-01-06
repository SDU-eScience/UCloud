<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/projects.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/project-notifications-providers.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / Project notifications
# Project notifications

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Project notifications are used by providers to synchronize UCloud's state with the provider's local state._

## Rationale

This feature is, for example, used to synchronize unix groups on a traditional HPC system with UCloud's
project structure. A provider will receive notifications for all relevant projects. A project is considered
relevant if the project has been allocated some resource on the provider. Providers should be aware that
they will receive notifications about users even if the users haven't connected yet. This problem is
typically handled by the integration module.

## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Pulls the database for more `ProjectNotification`s</td>
</tr>
<tr>
<td><a href='#markasread'><code>markAsRead</code></a></td>
<td>Marks one or more `ProjectNotification` as read</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#projectnotification'><code>ProjectNotification</code></a></td>
<td>A notification which indicates that a Project has changed</td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieve`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Pulls the database for more `ProjectNotification`s_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#projectnotification'>ProjectNotification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request fetches a new batch of `ProjectNotification`s. The provider should aim to handle all
notifications as soon as possible. Once a notification has been handled, the provider should call
`ProjectNotifications.markAsRead` with the appropriate `id`s. A robust provider implementation should
be able to handle receiving the same notification twice.

It is recommended that a provider calls this endpoint immediately after starting.


### `markAsRead`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Marks one or more `ProjectNotification` as read_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `ProjectNotification`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A notification which indicates that a Project has changed_

```kotlin
data class ProjectNotification(
    val id: String,
    val project: Project,
)
```
The notification contains the _current_ state of the `Project`. The project may have changed since the
notification was originally created. If multiple updates occur to a `Project` before a provider invokes
`markAsRead` then only a single notification will be created by UCloud.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> An identifier which uniquely identifies this notifications
</summary>



The identifier is never re-used for a new notifications.


</details>

<details>
<summary>
<code>project</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.project.api.v2.Project.md'>Project</a></code></code> The current state of the project which has been updated
</summary>





</details>



</details>



---

