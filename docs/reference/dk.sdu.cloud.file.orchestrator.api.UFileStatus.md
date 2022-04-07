[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `UFileStatus`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_General system-level stats about a file_

```kotlin
data class UFileStatus(
    val type: FileType?,
    val icon: FileIconHint?,
    val sizeInBytes: Long?,
    val sizeIncludingChildrenInBytes: Long?,
    val modifiedAt: Long?,
    val accessedAt: Long?,
    val unixMode: Int?,
    val unixOwner: Int?,
    val unixGroup: Int?,
    val metadata: FileMetadataHistory?,
    val synced: Boolean?,
    val resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>?,
    val resolvedProduct: Product.Storage?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='#filetype'>FileType</a>?</code></code> Which type of file this is, see `FileType` for more information.
</summary>





</details>

<details>
<summary>
<code>icon</code>: <code><code><a href='#fileiconhint'>FileIconHint</a>?</code></code> A hint to clients about which icon to display next to this file. See `FileIconHint` for details.
</summary>





</details>

<details>
<summary>
<code>sizeInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The size of this file in bytes (Requires `includeSizes`)
</summary>





</details>

<details>
<summary>
<code>sizeIncludingChildrenInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The size of this file and any child (Requires `includeSizes`)
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The modified at timestamp (Requires `includeTimestamps`)
</summary>





</details>

<details>
<summary>
<code>accessedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> The accessed at timestamp (Requires `includeTimestamps`)
</summary>





</details>

<details>
<summary>
<code>unixMode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix mode of a file (Requires `includeUnixInfo`
</summary>





</details>

<details>
<summary>
<code>unixOwner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix owner of a file as a UID (Requires `includeUnixInfo`)
</summary>





</details>

<details>
<summary>
<code>unixGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The unix group of a file as a GID (Requires `includeUnixInfo`)
</summary>





</details>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#filemetadatahistory'>FileMetadataHistory</a>?</code></code> User-defined metadata for this file. See `FileMetadataTemplate` for details.
</summary>





</details>

<details>
<summary>
<code>synced</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> If the file is added to synchronization or not
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FSSupport.md'>FSSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>


