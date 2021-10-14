[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Communication](/docs/developer-guide/core/communication/README.md) / [Notifications](/docs/developer-guide/core/communication/notifications.md)

# `Notification`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Notification(
    val type: String,
    val message: String,
    val id: Long?,
    val meta: JsonObject?,
    val ts: Long?,
    val read: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>message</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>meta</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>ts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>read</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>


