[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/filecollections.md)

# `FSProductStatsSupport`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which stats a given product supports_

```kotlin
data class FSProductStatsSupport(
    val sizeInBytes: Boolean?,
    val sizeIncludingChildrenInBytes: Boolean?,
    val modifiedAt: Boolean?,
    val createdAt: Boolean?,
    val accessedAt: Boolean?,
    val unixPermissions: Boolean?,
    val unixOwner: Boolean?,
    val unixGroup: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sizeInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>sizeIncludingChildrenInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>accessedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixPermissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixOwner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>


