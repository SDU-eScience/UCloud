[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Shares](/docs/developer-guide/orchestration/storage/shares.md)

# `OutgoingShareGroup`


[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class OutgoingShareGroup(
    val sourceFilePath: String,
    val storageProduct: ProductReference,
    val sharePreview: List<OutgoingShareGroup.Preview>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sourceFilePath</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>storageProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sharePreview</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#outgoingsharegroup.preview'>OutgoingShareGroup.Preview</a>&gt;</code></code>
</summary>





</details>



</details>


