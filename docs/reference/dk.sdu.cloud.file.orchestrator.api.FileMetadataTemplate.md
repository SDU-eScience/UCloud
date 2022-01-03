[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / [Metadata Documents](/docs/developer-guide/orchestration/storage/metadata/documents.md)

# `FileMetadataTemplate`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`_

```kotlin
data class FileMetadataTemplate(
    val namespaceId: String,
    val title: String,
    val version: String,
    val schema: JsonObject,
    val inheritable: Boolean,
    val requireApproval: Boolean,
    val description: String,
    val changeLog: String,
    val namespaceType: FileMetadataTemplateNamespaceType,
    val uiSchema: JsonObject?,
    val namespaceName: String?,
    val createdAt: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>namespaceId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the namespace that this template belongs to
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of this template. It does not have to be unique.
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Version identifier for this version. It must be unique within a single template group.
</summary>





</details>

<details>
<summary>
<code>schema</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code> JSON-Schema for this document
</summary>





</details>

<details>
<summary>
<code>inheritable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Makes this template inheritable by descendants of the file that the template is attached to
</summary>





</details>

<details>
<summary>
<code>requireApproval</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Description of this template. Markdown is supported.
</summary>





</details>

<details>
<summary>
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the change since last version. Markdown is supported.
</summary>





</details>

<details>
<summary>
<code>namespaceType</code>: <code><code><a href='#filemetadatatemplatenamespacetype'>FileMetadataTemplateNamespaceType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>uiSchema</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>namespaceName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>



</details>


