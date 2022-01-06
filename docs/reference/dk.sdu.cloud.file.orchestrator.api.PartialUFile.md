[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Files](/docs/developer-guide/orchestration/storage/providers/files/README.md) / [Ingoing API](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md)

# `PartialUFile`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A partial UFile returned by providers and made complete by UCloud/Core_

```kotlin
data class PartialUFile(
    val id: String,
    val status: UFileStatus,
    val createdAt: Long,
    val owner: ResourceOwner?,
    val permissions: ResourcePermissions?,
    val legacySensitivity: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The id of the file. Corresponds to UFile.id
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileStatus.md'>UFileStatus</a></code></code> The status of the file. Corresponds to UFile.status
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The creation timestamp. Corresponds to UFile.createdAt
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a>?</code></code> The owner of the file. Corresponds to UFile.owner. This will default to the collection's owner.
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> The permissions of the file. Corresponds to UFile.permissions.This will default to the collection's permissions.
</summary>





</details>

<details>
<summary>
<code>legacySensitivity</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Legacy for reading old sensitivity values stored on in extended attributes
</summary>





</details>



</details>


