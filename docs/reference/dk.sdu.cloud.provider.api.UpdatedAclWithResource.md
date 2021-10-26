[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# `UpdatedAclWithResource`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdatedAclWithResource<Res>(
    val resource: Res,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resource</code>: <code><code>Res</code></code>
</summary>





</details>

<details>
<summary>
<code>added</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourceaclentry'>ResourceAclEntry</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>deleted</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#aclentity'>AclEntity</a>&gt;</code></code>
</summary>





</details>



</details>


