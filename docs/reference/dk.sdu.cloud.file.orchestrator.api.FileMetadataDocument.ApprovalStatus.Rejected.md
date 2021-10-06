# `FileMetadataDocument.ApprovalStatus.Rejected`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The metadata document has been rejected by an admin of the workspace_

```kotlin
data class Rejected(
    val rejectedBy: String,
    val type: String /* "rejected" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>rejectedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "rejected" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>

