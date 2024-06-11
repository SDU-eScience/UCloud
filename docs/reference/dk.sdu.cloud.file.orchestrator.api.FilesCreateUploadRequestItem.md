[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `FilesCreateUploadRequestItem`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesCreateUploadRequestItem(
    val id: String,
    val type: UploadType,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code><a href='#uploadtype'>UploadType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>supportedProtocols</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#uploadprotocol'>UploadProtocol</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='#writeconflictpolicy'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>


