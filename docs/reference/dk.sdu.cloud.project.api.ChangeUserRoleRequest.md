[UCloud Developer Guide](/docs/developer-guide/README.md) / [Legacy](/docs/developer-guide/legacy/README.md) / [Projects (Legacy)](/docs/developer-guide/legacy/projects-legacy/README.md) / [Projects](/docs/developer-guide/legacy/projects-legacy/projects.md)

# `ChangeUserRoleRequest`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ChangeUserRoleRequest(
    val projectId: String,
    val member: String,
    val newRole: ProjectRole,
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
<code>member</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newRole</code>: <code><code><a href='#projectrole'>ProjectRole</a></code></code>
</summary>





</details>



</details>


