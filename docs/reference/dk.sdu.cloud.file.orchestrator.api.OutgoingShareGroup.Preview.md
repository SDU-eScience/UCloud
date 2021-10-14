[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Shares](/docs/developer-guide/orchestration/storage/shares.md)

# `OutgoingShareGroup.Preview`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Preview(
    val sharedWith: String,
    val permissions: List<Permission>,
    val state: Share.State,
    val shareId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sharedWith</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.Permission.md'>Permission</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#share.state'>Share.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>shareId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>


