[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Project notifications](/docs/developer-guide/accounting-and-projects/project-notifications.md)

# `ProjectNotification`


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


