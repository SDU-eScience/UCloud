[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `FileMetadataOrDeleted.Deleted`


[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Indicates that the metadata document has been deleted is no longer in use_

```kotlin
data class Deleted(
    val id: String,
    val changeLog: String,
    val createdAt: Long,
    val createdBy: String,
    val status: FileMetadataDocument.Status,
    val type: String /* "deleted" */,
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
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Reason for this change
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp indicating when this change was made
</summary>





</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the user who made this change
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataDocument.Status.md'>FileMetadataDocument.Status</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "deleted" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


