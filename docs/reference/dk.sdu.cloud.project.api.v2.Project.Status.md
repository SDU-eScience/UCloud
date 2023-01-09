[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Projects](/docs/developer-guide/accounting-and-projects/projects.md)

# `Project.Status`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Status(
    val archived: Boolean,
    val isFavorite: Boolean?,
    val members: List<ProjectMember>?,
    val groups: List<Group>?,
    val settings: Project.Settings?,
    val myRole: ProjectRole?,
    val path: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>archived</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> A flag which indicates if the project is currently archived.
</summary>



Currently, archiving does not mean a lot in UCloud. This is subject to change in the future. For the most
part, archived projects simply do not appear when using a `browse`, unless `includeArchived = true`.


</details>

<details>
<summary>
<code>isFavorite</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A flag which indicates if the current user has marked this as one of their favorite projects.
</summary>





</details>

<details>
<summary>
<code>members</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#projectmember'>ProjectMember</a>&gt;?</code></code> A list of project members, conditionally included if `includeMembers = true`.
</summary>



NOTE: This list will contain all members of a project, always. There are currently no plans for a
pagination API. This might change in the future if it becomes plausible that projects have many thousands
of members.


</details>

<details>
<summary>
<code>groups</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#group'>Group</a>&gt;?</code></code> A list of groups, conditionally included if `includeGroups = true`.
</summary>



NOTE: This list will contain all groups of a project, always. There are currently no plans for a pagination
API. This might change in the future if it becomes plausible that projects have many thousands of groups.


</details>

<details>
<summary>
<code>settings</code>: <code><code><a href='#project.settings'>Project.Settings</a>?</code></code> The settings of this project, conditionally included if `includeSettings = true`.
</summary>





</details>

<details>
<summary>
<code>myRole</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.project.api.ProjectRole.md'>ProjectRole</a>?</code></code> The role of the current user, this value is always included.
</summary>



This is typically not-null, but it can be null if the request was made by an actor which has access to the
project without being a member. Common examples include: `Actor.System` and a relevant provider.


</details>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A path to this project, conditionally included if `includePath = true`.
</summary>



The path is a '/' separated string where each component is a project title. The path will not contain
this project. The path does not start or end with a '/'. If the project is a root, then "" will be returned.


</details>



</details>


