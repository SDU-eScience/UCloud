[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Files](/docs/developer-guide/orchestration/storage/providers/files/README.md) / [Ingoing API](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md)

# `FilesProviderStreamingSearchResult.Result`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Result(
    val batch: List<PartialUFile>,
    val type: String /* "result" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>batch</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#partialufile'>PartialUFile</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "result" */</code></code> The type discriminator
</summary>





</details>



</details>


