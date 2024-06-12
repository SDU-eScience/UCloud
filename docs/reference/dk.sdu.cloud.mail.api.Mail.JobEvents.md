[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Mail](/docs/developer-guide/core/communication/mail.md)

# `Mail.JobEvents`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobEvents(
    val jobIds: List<String>,
    val jobNames: List<String>,
    val appTitles: List<String>,
    val events: List<String>,
    val subject: String?,
    val type: String /* "jobEvents" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>jobNames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>appTitles</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>events</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>subject</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "jobEvents" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


