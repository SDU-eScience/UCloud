[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/filecollections.md)

# `FSFileSupport`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which file-level operations a product supports_

```kotlin
data class FSFileSupport(
    val aclModifiable: Boolean?,
    val trashSupported: Boolean?,
    val isReadOnly: Boolean?,
    val searchSupported: Boolean?,
    val streamingSearchSupported: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>aclModifiable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>trashSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isReadOnly</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>searchSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Declares support for the normal search endpoint
</summary>



NOTE(Dan, 01/09/2022): For backwards compatibility, this is true by default, however, this will likely change 
to false in a later release. Providers should explicitly declare support for this endpoint for the time being.


</details>

<details>
<summary>
<code>streamingSearchSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Declares support for the streamingSearch endpoint
</summary>





</details>



</details>


