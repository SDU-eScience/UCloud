[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Project notifications](/docs/developer-guide/accounting-and-projects/project-notifications.md)

# `projects.v2.notifications.retrieve`

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


