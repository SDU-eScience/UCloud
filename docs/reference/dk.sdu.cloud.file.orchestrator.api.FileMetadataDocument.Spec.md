# `FileMetadataDocument.Spec`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_Specification of a FileMetadataDocument_

```kotlin
data class Spec(
    val templateId: String,
    val version: String,
    val document: JsonObject,
    val changeLog: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>templateId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the `FileMetadataTemplate` that this document conforms to
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The version of the `FileMetadataTemplate` that this document conforms to
</summary>





</details>

<details>
<summary>
<code>document</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code> The document which fills out the template
</summary>





</details>

<details>
<summary>
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Reason for this change
</summary>





</details>



</details>

