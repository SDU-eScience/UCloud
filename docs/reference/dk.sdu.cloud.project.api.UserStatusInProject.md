[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Projects](/docs/developer-guide/accounting-and-projects/projects/README.md) / [Members](/docs/developer-guide/accounting-and-projects/projects/members.md)

# `UserStatusInProject`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserStatusInProject(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val parent: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>whoami</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.project.api.ProjectMember.md'>ProjectMember</a></code></code>
</summary>





</details>

<details>
<summary>
<code>parent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>


