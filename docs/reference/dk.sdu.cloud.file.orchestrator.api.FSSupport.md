[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/filecollections.md)

# `FSSupport`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FSSupport(
    val product: ProductReference,
    val stats: FSProductStatsSupport?,
    val collection: FSCollectionSupport?,
    val files: FSFileSupport?,
    val maintenance: Maintenance?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stats</code>: <code><code><a href='#fsproductstatssupport'>FSProductStatsSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>collection</code>: <code><code><a href='#fscollectionsupport'>FSCollectionSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>files</code>: <code><code><a href='#fsfilesupport'>FSFileSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>maintenance</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.Maintenance.md'>Maintenance</a>?</code></code>
</summary>





</details>



</details>


